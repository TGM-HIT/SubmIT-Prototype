#!/usr/bin/env ruby
# frozen_string_literal: true

# Bounded evidence collector for Compliance-03. It reads only paths named on
# the command line and writes one JSON report into the caller-supplied output
# directory. Auditor judgments are retained as judgments; they are not inferred.

require 'digest'
require 'fileutils'
require 'json'
require 'optparse'
require 'yaml'

module Compliance03Evidence
  class InputError < StandardError; end

  METHODS = %w[GET POST PUT PATCH DELETE HEAD OPTIONS].freeze
  MAPPING_ANNOTATIONS = {
    'GetMapping' => 'GET',
    'PostMapping' => 'POST',
    'PutMapping' => 'PUT',
    'PatchMapping' => 'PATCH',
    'DeleteMapping' => 'DELETE'
  }.freeze
  UPLOADS = [
    ['POST', '/api/ideas/pdf'],
    ['PUT', '/api/ideas/{id}/pdf'],
    ['PUT', '/api/ideas/{id}/specification/pdf'],
    ['POST', '/api/projects/{projectId}/artifacts']
  ].freeze
  UPLOAD_STATUSES = %w[400 413].freeze

  module_function

  def normalize_path(path)
    path.to_s.split('?').first.to_s.sub(%r{/+\z}, '').gsub(/\{[^}]+\}/, '{}')
  end

  def canonical(value)
    case value
    when Hash
      value.keys.sort.each_with_object({}) { |key, result| result[key] = canonical(value[key]) }
    when Array
      value.map { |item| canonical(item) }
    else
      value
    end
  end

  def fingerprint(value)
    Digest::SHA256.hexdigest(JSON.generate(canonical(value)))
  end

  def read_json(path, label)
    JSON.parse(File.read(path))
  rescue Errno::ENOENT, Errno::EACCES => error
    raise InputError, "#{label}: cannot read #{path}: #{error.message}"
  rescue JSON::ParserError => error
    raise InputError, "#{label}: invalid JSON in #{path}: #{error.message}"
  end

  def read_yaml(path)
    YAML.safe_load(File.read(path), [], [], false)
  rescue Errno::ENOENT, Errno::EACCES => error
    raise InputError, "OpenAPI: cannot read #{path}: #{error.message}"
  rescue Psych::Exception => error
    raise InputError, "OpenAPI: invalid YAML in #{path}: #{error.message}"
  end

  def require_hash(value, label)
    raise InputError, "#{label} must be a JSON object" unless value.is_a?(Hash)

    value
  end

  def require_array(value, label)
    raise InputError, "#{label} must be an array" unless value.is_a?(Array)

    value
  end

  def require_text(value, label)
    raise InputError, "#{label} must be a non-empty string" unless value.is_a?(String) && !value.strip.empty?

    value
  end

  def load_sources(source_specs, controller_specs)
    sources = {}
    controllers = {}
    (source_specs + controller_specs).each do |identifier, path, kind|
      raise InputError, "duplicate source identifier: #{identifier}" if sources.key?(identifier)
      raise InputError, "source identifier is empty" if identifier.to_s.empty?
      raise InputError, "#{kind} source is not a file: #{path}" unless File.file?(path)

      sources[identifier] = { 'path' => path, 'text' => File.read(path) }
      controllers[identifier] = parse_java_controller(sources[identifier]['text'], identifier) if kind == 'controller'
    rescue Errno::EACCES => error
      raise InputError, "#{kind} source #{path}: #{error.message}"
    end
    [sources, controllers]
  end

  def source_citation(citation, sources, label)
    citation = require_hash(citation, label)
    source_id = require_text(citation['source'], "#{label}.source")
    text = require_text(citation['text'], "#{label}.text")
    source = sources[source_id]
    raise InputError, "#{label} names an undeclared source: #{source_id}" unless source
    raise InputError, "#{label} quote does not occur verbatim in source #{source_id}" unless source['text'].include?(text)

    {
      'source' => source_id,
      'path' => source['path'],
      'text' => text,
      'source_sha256' => Digest::SHA256.hexdigest(source['text'])
    }
  end

  def quote_list(value, sources, label)
    require_array(value, label).each_with_index.map do |citation, index|
      source_citation(citation, sources, "#{label}[#{index}]")
    end
  end

  def extract_routes(text)
    text.scan(/\b(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\s+(\/[A-Za-z0-9_{}.$\/-]+(?:\?[A-Za-z0-9_{}.$=&%-]*)?)/i).map do |method, route|
      [method.upcase, normalize_path(route.sub(/[.,;:]+\z/, ''))]
    end.uniq
  end

  def validate_query_evidence(assertion, parameters, spec_text, method, path, label)
    citations_by_parameter = assertion['query_parameter_citations']
    raise InputError, "#{label}.specification.query_parameter_citations must be an object" unless citations_by_parameter.is_a?(Hash)

    extras = citations_by_parameter.keys - parameters
    raise InputError, "#{label} has query citations without declared parameters: #{extras.join(', ')}" unless extras.empty?

    parameters.each_with_object({}) do |parameter, result|
      quotes = require_array(
        citations_by_parameter[parameter],
        "#{label}.specification.query_parameter_citations.#{parameter}"
      )
      raise InputError, "#{label} query parameter #{parameter} has no specification citation" if quotes.empty?

      checked = quotes.each_with_index.map do |quote, index|
        quote = require_text(quote, "#{label} query citation #{parameter}[#{index}]")
        raise InputError, "#{label} query citation for #{parameter} does not occur verbatim in --spec" unless spec_text.include?(quote)
        unless quote.match?(/(?<![A-Za-z0-9_])#{Regexp.escape(parameter)}(?![A-Za-z0-9_])/)
          raise InputError, "#{label} query citation does not establish parameter #{parameter}"
        end

        route_specific = extract_routes(quote).include?([method, normalize_path(path)])
        shared_rule = quote.match?(/\b(?:every|all)\s+collection endpoint|\bshared pagination\b/i)
        unless route_specific || shared_rule
          raise InputError, "#{label} query citation for #{parameter} is not operation-specific or an explicit shared pagination rule"
        end
        quote
      end
      result[parameter] = checked
    end
  end

  def validate_specification(assertion, method, path, spec_text, label)
    return {
      'basis' => 'not_cited',
      'mapping_status' => 'unverified',
      'query_parameters' => nil,
      'query_parameter_citations' => nil,
      'path_gap' => false
    } if assertion.nil?

    assertion = require_hash(assertion, "#{label}.specification")
    basis = require_text(assertion['basis'], "#{label}.specification.basis")
    citations = require_array(assertion['citations'], "#{label}.specification.citations")
    raise InputError, "#{label}.specification.citations must not be empty" if citations.empty?

    cited_text = citations.each_with_index.map do |quote, index|
      text = require_text(quote, "#{label}.specification.citations[#{index}]")
      raise InputError, "#{label} specification quote does not occur verbatim in --spec" unless spec_text.include?(text)

      text
    end.join("\n")
    cited_routes = extract_routes(cited_text)
    normalized_path = normalize_path(path)
    case basis
    when 'explicit'
      unless cited_routes.include?([method, normalized_path])
        raise InputError, "#{label} explicit specification citation does not establish #{method} #{path}"
      end
    when 'same_route'
      base_method = require_text(assertion['base_method'], "#{label}.specification.base_method").upcase
      base_path = require_text(assertion['base_path'], "#{label}.specification.base_path")
      unless method != base_method && normalized_path == normalize_path(base_path) &&
             cited_routes.include?([base_method, normalize_path(base_path)]) &&
             cited_text.match?(/same route|same endpoint|inherits the route/i)
        raise InputError, "#{label} same_route citation must establish a different method on the cited same route"
      end
      unless cited_text.match?(/\b#{Regexp.escape(method)}\b/i)
        raise InputError, "#{label} same_route citation does not name target method #{method}"
      end
    when 'gap'
      scope = require_text(assertion['scope'], "#{label}.specification.scope")
      if cited_routes.include?([method, normalized_path])
        raise InputError, "#{label} cannot mark a cited #{method} #{path} as a specification gap"
      end
      if extract_routes(spec_text).include?([method, normalized_path])
        raise InputError, "#{label} cannot mark #{method} #{path} as a gap; that exact route occurs in --spec"
      end
      parameters = require_array(assertion.fetch('query_parameters', []), "#{label}.specification.query_parameters")
      parameters = parameters.map { |name| require_text(name, "#{label}.specification query parameter") }.uniq.sort
      query_citations = validate_query_evidence(assertion, parameters, spec_text, method, path, label)
      return {
        'basis' => 'gap',
        'mapping_status' => 'auditor-cited gap scope',
        'scope' => scope,
        'citations' => citations,
        'query_parameters' => parameters,
        'query_parameter_citations' => query_citations,
        'path_gap' => true
      }
    when 'sibling'
      base_method = require_text(assertion['base_method'], "#{label}.specification.base_method").upcase
      base_path = require_text(assertion['base_path'], "#{label}.specification.base_path")
      suffix = require_text(assertion['suffix'], "#{label}.specification.suffix")
      expected = normalize_path(base_path + suffix)
      unless method == base_method && normalized_path == expected &&
             cited_routes.include?([base_method, normalize_path(base_path)]) &&
             cited_text.include?(suffix) &&
             cited_text.match?(/sibling|sub[- ]?resource|under that route/i)
        raise InputError, "#{label} sibling citation must establish the same-method base route and literal suffix"
      end
    else
      raise InputError, "#{label}.specification.basis must be explicit, same_route, sibling, or gap"
    end

    parameters = require_array(assertion['query_parameters'], "#{label}.specification.query_parameters")
    parameters = parameters.map { |name| require_text(name, "#{label}.specification query parameter") }.uniq.sort
    query_citations = validate_query_evidence(assertion, parameters, spec_text, method, path, label)

    {
      'basis' => basis,
      'mapping_status' => 'cited',
      'citations' => citations,
      'query_parameters' => parameters,
      'query_parameter_citations' => query_citations,
      'path_gap' => false
    }
  end

  def parse_java_controller(text, source_id)
    class_match = text.match(/@RequestMapping\s*\(([^)]*)\)\s*(?:@[^\n]+\s*)*(?:public\s+)?(?:abstract\s+)?class\s+\w+/m)
    class_base = class_match ? annotation_path(class_match[1]) : ''
    operations = []
    annotation_pattern = /@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)\b(?:\s*\(([^)]*)\))?/
    text.to_enum(:scan, annotation_pattern).each do
      annotation = Regexp.last_match
      annotation_name = annotation[1]
      arguments = annotation[2].to_s
      after_annotation = text[annotation.end(0)..-1]
      signature = after_annotation.match(/\b(?:(?:public|protected|private)\s+)?[\w<>, ?.\[\]]+\s+(\w+)\s*\(/m)
      next unless signature

      # Ignore unrelated mapping annotations which happen to precede a later
      # method declaration, and reject signatures separated by another route.
      intervening = after_annotation[0...signature.begin(0)]
      next if intervening.match?(/@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)\b/)

      method_name = signature[1]
      params_start = signature.end(0)
      params, = balanced_parentheses(after_annotation, params_start - 1)
      http_method = MAPPING_ANNOTATIONS[annotation_name]
      if annotation_name == 'RequestMapping'
        http_method = arguments[/RequestMethod\.(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)/, 1]
      end
      next unless http_method

      method_path = annotation_path(arguments)
      full_path = join_path(class_base, method_path)
      operations << {
        'method' => http_method,
        'path' => full_path,
        'method_name' => method_name,
        'query_parameters' => request_parameters(params),
        'source' => source_id,
        'line' => text[0...annotation.begin(0)].count("\n") + 1
      }
    end
    operations
  end

  def balanced_parentheses(text, opening)
    raise InputError, 'cannot parse Java method parameter list' unless text[opening] == '('

    depth = 0
    text[opening..-1].each_char.with_index do |character, offset|
      depth += 1 if character == '('
      depth -= 1 if character == ')'
      return [text[(opening + 1)...(opening + offset)], opening + offset + 1] if depth.zero?
    end
    raise InputError, 'unterminated Java method parameter list'
  end

  def annotation_path(arguments)
    match = arguments.match(/(?:value|path)\s*=\s*"([^"]+)"|"([^"]+)"/)
    match ? (match[1] || match[2]) : ''
  end

  def join_path(base, child)
    left = base.to_s.sub(%r{/+\z}, '')
    right = child.to_s.sub(%r{\A/+}, '')
    return '/' if left.empty? && right.empty?
    return "/#{right}" if left.empty?
    return left if right.empty?

    "#{left}/#{right}"
  end

  def request_parameters(params)
    params.scan(/@RequestParam(?:\s*\(([^)]*)\))?\s*(?:final\s+)?[\w<>, ?.\[\]]+\s+(\w+)/m).map do |arguments, variable|
      explicit = arguments.to_s.match(/(?:name|value)\s*=\s*"([^"]+)"/)
      positional = arguments.to_s.strip.match(/\A"([^"]+)"\z/)
      explicit ? explicit[1] : positional ? positional[1] : variable
    end.uniq.sort
  end

  def openapi_operation(openapi, method, path)
    paths = openapi.is_a?(Hash) ? openapi['paths'] : nil
    item = paths.is_a?(Hash) ? paths[path] : nil
    raise InputError, "OpenAPI path-item reference is not expanded: #{item['$ref']}" if item.is_a?(Hash) && item['$ref']
    operation = item.is_a?(Hash) ? item[method.downcase] : nil
    [item, operation]
  end

  def contract_query_parameters(path_item, operation)
    parameters = []
    [path_item, operation].each do |container|
      next unless container.is_a?(Hash)

      raw_parameters = container['parameters']
      next if raw_parameters.nil?
      raise InputError, 'OpenAPI parameters must be an array' unless raw_parameters.is_a?(Array)

      raw_parameters.each do |parameter|
        raise InputError, 'OpenAPI parameter entry must be an object' unless parameter.is_a?(Hash)
        raise InputError, "OpenAPI parameter reference is not expanded: #{parameter['$ref']}" if parameter['$ref']
        unless parameter.key?('in') && parameter.key?('name')
          raise InputError, 'OpenAPI parameter is missing its in/name fields'
        end
        if parameter['in'] == 'query'
          parameters << require_text(parameter['name'], 'OpenAPI query parameter name')
        end
      end
    end
    parameters.compact.uniq.sort
  end

  def run_topic2(data, spec_text, controllers, openapi)
    data = require_hash(data, 'Topic 2 evidence')
    operations = require_array(data['operations'], 'Topic 2 operations')
    rows = operations.each_with_index.map do |raw, index|
      label = "Topic 2 operations[#{index}]"
      raw = require_hash(raw, label)
      method = require_text(raw['method'], "#{label}.method").upcase
      path = require_text(raw['path'], "#{label}.path")
      raise InputError, "#{label}.method is unsupported: #{method}" unless METHODS.include?(method)

      path_item, contract_operation = openapi_operation(openapi, method, path)
      raise InputError, "#{label}: generated OpenAPI operation not found: #{method} #{path}" unless contract_operation

      implementation = require_hash(raw['implementation'], "#{label}.implementation")
      controller_id = require_text(implementation['controller'], "#{label}.implementation.controller")
      method_name = require_text(implementation['method'], "#{label}.implementation.method")
      candidates = controllers[controller_id]
      raise InputError, "#{label} references undeclared controller: #{controller_id}" unless candidates

      matched = candidates.select { |candidate| candidate['method_name'] == method_name }
      matched.select! do |candidate|
        candidate['method'] == method && normalize_path(candidate['path']) == normalize_path(path)
      end
      raise InputError, "#{label}: verb-matched controller method not found for #{method} #{path}" unless matched.length == 1

      source_assertion = validate_specification(
        raw['specification'], method, path, spec_text, label
      )
      implementation_parameters = matched.first['query_parameters']
      contract_parameters = contract_query_parameters(path_item, contract_operation)
      {
        'method' => method,
        'path' => path,
        'specification' => source_assertion,
        'implementation' => {
          'source' => controller_id,
          'method' => method_name,
          'line' => matched.first['line'],
          'query_parameters' => implementation_parameters
        },
        'contract' => {
          'query_parameters' => contract_parameters
        },
        'parameter_comparison' => {
          'specification_equals_implementation' => if source_assertion['mapping_status'] == 'unverified'
                                                     nil
                                                   else
                                                     source_assertion['query_parameters'] == implementation_parameters
                                                   end,
          'specification_equals_contract' => if source_assertion['mapping_status'] == 'unverified'
                                               nil
                                             else
                                               source_assertion['query_parameters'] == contract_parameters
                                             end,
          'implementation_equals_contract' => implementation_parameters == contract_parameters
        }
      }
    end
    { 'operations' => rows }
  end

  def check_citations(citations, sources, label)
    quote_list(citations, sources, label)
  end

  def response_fact(openapi, method, path, status)
    _path_item, operation = openapi_operation(openapi, method, path)
    raise InputError, "OpenAPI responses must be an object for #{method} #{path}" unless operation.is_a?(Hash) && operation['responses'].is_a?(Hash)

    responses = operation['responses']
    key = responses.key?(status.to_s) ? status.to_s : status.to_i
    return { 'declared' => false, 'description' => nil, 'schemas' => nil } unless responses.key?(key)
    response = responses[key]
    raise InputError, "OpenAPI response must be an object for #{method} #{path} status #{status}" unless response.is_a?(Hash)
    raise InputError, "OpenAPI response reference is not expanded for #{method} #{path} status #{status}: #{response['$ref']}" if response['$ref']

    content = response['content']
    raise InputError, "OpenAPI response content must be an object for #{method} #{path} status #{status}" if content && !content.is_a?(Hash)
    schemas = if content.is_a?(Hash)
                content.each_with_object({}) do |(media_type, media), result|
                  raise InputError, "OpenAPI media type content must be an object for #{method} #{path} status #{status}" unless media.is_a?(Hash)

                  result[media_type] = media.key?('schema') ? media['schema'] : nil
                end
              end
    {
      'declared' => true,
      'description' => response.key?('description') ? response['description'] : nil,
      'schemas' => schemas
    }
  end

  def validate_upload_inventory(operations)
    indexed = operations.each_with_object({}) do |operation, result|
      raise InputError, 'Topic 6 operation entry must be an object' unless operation.is_a?(Hash)
      method = require_text(operation['method'], 'Topic 6 operation.method').upcase
      path = require_text(operation['path'], 'Topic 6 operation.path')
      key = [method, normalize_path(path)]
      raise InputError, "duplicate Topic 6 operation: #{key.join(' ')}" if result.key?(key)

      result[key] = operation
    end
    UPLOADS.each do |method, path|
      operation = indexed[[method, normalize_path(path)]]
      raise InputError, "Topic 6 inventory is missing required upload: #{method} #{path}" unless operation

      statuses_input = require_array(operation['statuses'], "Topic 6 #{method} #{path} statuses")
      statuses_input.each do |status|
        raise InputError, "Topic 6 #{method} #{path} status entry must be an object" unless status.is_a?(Hash)
      end
      statuses = statuses_input.map { |status| require_text(status['status'], "Topic 6 #{method} #{path} status").to_s }
      missing = UPLOAD_STATUSES - statuses
      raise InputError, "#{method} #{path} is missing required status evidence: #{missing.join(', ')}" unless missing.empty?
      duplicate = statuses.select { |status| statuses.count(status) > 1 }.uniq
      raise InputError, "#{method} #{path} has duplicate status evidence: #{duplicate.join(', ')}" unless duplicate.empty?

      status_413 = operation['statuses'].find { |status| status['status'].to_s == '413' }
      unless status_413['applicable'] == true
        raise InputError, "#{method} #{path} 413 must be included as applicable, even when OpenAPI omits the response"
      end
      status_400 = operation['statuses'].find { |status| status['status'].to_s == '400' }
      raise InputError, "#{method} #{path} 400 must be applicable" unless status_400['applicable'] == true
    end
  end

  def run_topic6(data, sources, openapi)
    data = require_hash(data, 'Topic 6 evidence')
    operations = require_array(data['operations'], 'Topic 6 operations')
    validate_upload_inventory(operations)
    result = operations.each_with_index.map do |raw_operation, operation_index|
      label = "Topic 6 operations[#{operation_index}]"
      raw_operation = require_hash(raw_operation, label)
      method = require_text(raw_operation['method'], "#{label}.method").upcase
      path = require_text(raw_operation['path'], "#{label}.path")
      path_item, contract_operation = openapi_operation(openapi, method, path)
      raise InputError, "#{label}: generated OpenAPI operation not found: #{method} #{path}" unless contract_operation

      protected = raw_operation['protected']
      raise InputError, "#{label}.protected must be true or false" unless [true, false].include?(protected)
      access = check_citations([raw_operation['access_citation']], sources, "#{label}.access_citation").first
      statuses = require_array(raw_operation['statuses'], "#{label}.statuses").map.with_index do |raw_status, status_index|
        status_label = "#{label}.statuses[#{status_index}]"
        raw_status = require_hash(raw_status, status_label)
        status = require_text(raw_status['status'], "#{status_label}.status")
        applicable = raw_status['applicable']
        raise InputError, "#{status_label}.applicable must be true or false" unless [true, false].include?(applicable)
        trigger = require_text(raw_status['trigger'], "#{status_label}.trigger")
        triggers = require_array(raw_status['triggers'], "#{status_label}.triggers")
        checked_triggers = triggers.map.with_index do |raw_trigger, trigger_index|
          trigger_label = "#{status_label}.triggers[#{trigger_index}]"
          raw_trigger = require_hash(raw_trigger, trigger_label)
          kind = require_text(raw_trigger['kind'], "#{trigger_label}.kind")
          description = require_text(raw_trigger['description'], "#{trigger_label}.description")
          citations = check_citations(raw_trigger['citations'], sources, "#{trigger_label}.citations")
          raise InputError, "#{trigger_label} must include at least one source citation" if citations.empty?
          { 'kind' => kind, 'description' => description, 'citations' => citations }
        end
        if applicable && checked_triggers.empty?
          raise InputError, "#{status_label} is applicable and must include a cited trigger"
        end
        fact = response_fact(openapi, method, path, status)
        runtime_body = raw_status['runtime_body']
        raise InputError, "#{status_label}.runtime_body must explicitly describe runtime body evidence" unless runtime_body.is_a?(Hash)
        runtime_body_evidence = if runtime_body['status'] == 'not_observed'
                                  { 'status' => 'not_observed' }
                                elsif runtime_body['status'] == 'observed'
                                  citations = check_citations(runtime_body['citations'], sources, "#{status_label}.runtime_body.citations")
                                  raise InputError, "#{status_label}.runtime_body must include a source citation" if citations.empty?
                                  { 'status' => 'observed', 'citations' => citations }
                                else
                                  raise InputError, "#{status_label}.runtime_body.status must be observed or not_observed"
                                end

        binding_facts = {
          'method' => method,
          'path' => normalize_path(path),
          'protected' => protected,
          'access_citation' => access,
          'status' => status,
          'applicable' => applicable,
          'trigger' => trigger,
          'triggers' => checked_triggers,
          'runtime_body_evidence' => runtime_body_evidence,
          'response' => fact
        }
        review = review_record(raw_status['review'], binding_facts, checked_triggers, method, path, status, status_label)
        {
          'status' => status,
          'applicable' => applicable,
          'trigger' => trigger,
          'triggers' => checked_triggers,
          'generated_contract' => fact,
          'runtime_body_evidence' => runtime_body_evidence,
          'review' => review
        }
      end
      {
        'method' => method,
        'path' => path,
        'protected' => protected,
        'access_citation' => access,
        'statuses' => statuses
      }
    end
    { 'operations' => result }
  end

  def review_record(raw_review, binding_facts, triggers, method, path, status, label)
    binding = fingerprint(binding_facts)
    complete_kinds = triggers.map { |trigger| trigger['kind'] }
    upload_operation = UPLOADS.any? do |upload_method, upload_path|
      method == upload_method && normalize_path(path) == normalize_path(upload_path)
    end
    required_kinds = if status.to_s == '413' && upload_operation
                       %w[per_file_limit aggregate_limit]
                     else
                       []
                     end
    missing_kinds = required_kinds - complete_kinds
    return {
      'status' => 'not_reviewed',
      'disposition' => nil,
      'rationale' => nil,
      'binding_sha256' => binding,
      'evidence_complete' => missing_kinds.empty?,
      'missing_trigger_kinds' => missing_kinds
    } if raw_review.nil?

    raw_review = require_hash(raw_review, "#{label}.review")
    disposition = require_text(raw_review['disposition'], "#{label}.review.disposition")
    unless %w[accurate inaccurate unresolved].include?(disposition)
      raise InputError, "#{label}.review.disposition must be accurate, inaccurate, or unresolved"
    end
    rationale = require_text(raw_review['rationale'], "#{label}.review.rationale")
    submitted_binding = raw_review['binding_sha256']
    bound = submitted_binding == binding
    {
      'status' => if missing_kinds.any?
                   'incomplete_evidence'
                 elsif bound
                   'reviewed_by_auditor'
                 else
                   'stale_or_unbound'
                 end,
      'submitted_disposition' => disposition,
      'disposition' => bound && missing_kinds.empty? ? disposition : nil,
      'rationale' => rationale,
      'binding_sha256' => binding,
      'binding_matches' => bound,
      'evidence_complete' => missing_kinds.empty?,
      'missing_trigger_kinds' => missing_kinds,
      'judgment_source' => 'auditor; not machine-validated'
    }
  end

  def parse_assignments(values, label)
    values.map do |value|
      identifier, path = value.split('=', 2)
      raise InputError, "#{label} must use ID=PATH: #{value}" if identifier.to_s.empty? || path.to_s.empty?

      [identifier, path]
    end
  end

  def execute(argv)
    options = { sources: [], controllers: [] }
    parser = OptionParser.new do |opts|
      opts.banner = 'Usage: ruby harness/checks/Compliance-03-evidence.rb --spec PATH --openapi PATH --topic2 PATH --topic6 PATH --output-dir PATH [--controller ID=PATH] [--source ID=PATH]'
      opts.on('--spec PATH', 'Approved backend specification Markdown file') { |value| options[:spec] = value }
      opts.on('--openapi PATH', 'Generated OpenAPI YAML file') { |value| options[:openapi] = value }
      opts.on('--topic2 PATH', 'Topic 2 evidence-plan JSON file') { |value| options[:topic2] = value }
      opts.on('--topic6 PATH', 'Topic 6 evidence-plan JSON file') { |value| options[:topic6] = value }
      opts.on('--controller ID=PATH', 'Explicit Java controller source (repeatable)') { |value| options[:controllers] << value }
      opts.on('--source ID=PATH', 'Explicit source for quoted evidence (repeatable)') { |value| options[:sources] << value }
      opts.on('--output-dir PATH', 'Supplied ignored run output directory') { |value| options[:output_dir] = value }
      opts.on('-h', '--help', 'Show this help') do
        puts opts
        return 0
      end
    end
    parser.parse!(argv)
    required = %i[spec openapi topic2 topic6 output_dir]
    missing = required.reject { |key| options[key] }
    raise InputError, "missing required option(s): #{missing.map { |key| "--#{key.to_s.tr('_', '-')}" }.join(', ')}" unless missing.empty?
    raise InputError, "unexpected arguments: #{argv.join(' ')}" unless argv.empty?
    raise InputError, 'at least one --controller is required' if options[:controllers].empty?

    output_dir = File.expand_path(options[:output_dir])
    raise InputError, "--output-dir is not a directory: #{output_dir}" if File.exist?(output_dir) && !File.directory?(output_dir)

    controller_specs = parse_assignments(options[:controllers], '--controller').map { |id, path| [id, path, 'controller'] }
    source_specs = parse_assignments(options[:sources], '--source').map { |id, path| [id, path, 'source'] }
    sources, controllers = load_sources(source_specs, controller_specs)
    spec_text = File.read(options[:spec])
    openapi = read_yaml(options[:openapi])
    topic2 = read_json(options[:topic2], 'Topic 2 evidence')
    topic6 = read_json(options[:topic6], 'Topic 6 evidence')
    report = {
      'format' => 'compliance-03-evidence/v1',
      'inputs' => {
        'specification' => File.expand_path(options[:spec]),
        'openapi' => File.expand_path(options[:openapi]),
        'topic2_evidence' => File.expand_path(options[:topic2]),
        'topic6_evidence' => File.expand_path(options[:topic6]),
        'source_files' => sources.each_with_object({}) { |(id, source), result| result[id] = File.expand_path(source['path']) }
      },
      'topic2' => run_topic2(topic2, spec_text, controllers, openapi),
      'topic6' => run_topic6(topic6, sources, openapi),
      'interpretation' => {
        'machine_validated' => [
          'source quotes occur verbatim in explicitly supplied files',
          'cited specification method/path and per-parameter evidence is checked separately from implementation and contract facts',
          'controller query parameters are extracted from verb-matched Java controller methods',
          'contract query parameters, descriptions, and schemas are extracted from the supplied OpenAPI YAML',
          'review bindings match the exact retained trigger and contract facts'
        ],
        'auditor_judgment' => 'Semantic dispositions and the exhaustiveness of a cited gap scope are Auditor-supplied; neither judgment is computed by this tool.'
      }
    }
    FileUtils.mkdir_p(output_dir)
    output_path = File.join(output_dir, 'compliance-03-evidence.json')
    File.write(output_path, JSON.pretty_generate(report) + "\n")
    puts "Wrote #{output_path}"
    0
  rescue InputError => error
    warn "Compliance-03 evidence error: #{error.message}"
    2
  rescue Errno::ENOENT, Errno::EACCES => error
    warn "Compliance-03 evidence error: #{error.message}"
    2
  end
end

exit Compliance03Evidence.execute(ARGV) if $PROGRAM_NAME == __FILE__

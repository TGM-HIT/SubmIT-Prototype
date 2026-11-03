# frozen_string_literal: true

require 'fileutils'
require 'json'
require 'minitest/autorun'
require 'open3'
require 'rbconfig'
require 'tmpdir'

class Compliance03EvidenceTest < Minitest::Test
  SCRIPT = File.expand_path('Compliance-03-evidence.rb', __dir__)
  UPLOADS = [
    ['POST', '/api/ideas/pdf'],
    ['PUT', '/api/ideas/{id}/pdf'],
    ['PUT', '/api/ideas/{id}/specification/pdf'],
    ['POST', '/api/projects/{projectId}/artifacts']
  ].freeze

  def setup
    @directory = Dir.mktmpdir('compliance-03-evidence')
    @spec_path = write('backend-spec.md', <<~'SPEC')
      # Approved API routes
      - GET /api/items lists items.
      - Every collection endpoint is paginated. The query parameters page and size control results.
      - POST /api/items uses the same route as GET /api/items.
      - GET /api/projects/{id}; sibling subresource /api/projects/{id}/archive.
      - POST /api/ideas/pdf accepts multipart uploads.
      - PUT /api/ideas/{id}/pdf accepts multipart uploads.
      - PUT /api/ideas/{id}/specification/pdf accepts multipart uploads.
      - POST /api/projects/{projectId}/artifacts accepts multipart uploads.
    SPEC
    @list_controller = write('ItemController.java', <<~'JAVA')
      @RequestMapping("/api/items")
      public class ItemController {
        @GetMapping
        public Object list(@RequestParam(name = "page") int page,
                           @RequestParam(defaultValue = "20") int size) { return null; }

        @PostMapping
        Object create(@RequestParam("source") String source) { return null; }

        @PutMapping("/{id}")
        public Object update(@RequestParam(name = "revision") int revision) { return null; }
      }
    JAVA
    @project_controller = write('ProjectController.java', <<~'JAVA')
      @RequestMapping("/api/projects")
      public class ProjectController {
        @GetMapping("/{id}/archive")
        public Object archive(@PathVariable String id) { return null; }
      }
    JAVA
    @source_path = write('runtime-evidence.txt', <<~'EVIDENCE')
      Public routes require no authentication.
      All upload routes require an authenticated project role.
      Per-file PDF parser ceiling is 10 MB for each uploaded file.
      Aggregate multipart request ceiling is 11 MB across all parts.
      assertEquals(413, perFileResponse.statusCode(), responseText(perFileResponse));
      assertEquals(413, aggregateResponse.statusCode(), responseText(aggregateResponse));
      A malformed multipart request may produce a 400 response.
      The project artifact OpenAPI operation has no declared 413 response.
    EVIDENCE
    @openapi_path = write('openapi.yaml', JSON.generate(openapi_fixture))
    @topic2_path = write('topic2.json', JSON.generate(topic2_fixture))
    @topic6_path = write('topic6.json', JSON.generate(topic6_fixture))
    @output_dir = File.join(@directory, 'ignored-run-output')
  end

  def teardown
    FileUtils.remove_entry(@directory) if @directory && File.exist?(@directory)
  end

  def test_topic2_keeps_method_aware_explicit_inherited_sibling_and_gap_evidence
    report, _stdout, stderr = execute_tool
    assert_equal '', stderr
    rows = report.fetch('topic2').fetch('operations')
    assert_equal 4, rows.length

    explicit = row_for(rows, 'GET', '/api/items')
    assert_equal 'explicit', explicit.dig('specification', 'basis')
    assert_equal %w[page size], explicit.dig('specification', 'query_parameters')
    assert_equal %w[page size], explicit.dig('implementation', 'query_parameters')
    assert_equal %w[page size], explicit.dig('contract', 'query_parameters')

    inherited = row_for(rows, 'POST', '/api/items')
    assert_equal 'same_route', inherited.dig('specification', 'basis')
    assert_equal %w[source], inherited.dig('implementation', 'query_parameters')

    sibling = row_for(rows, 'GET', '/api/projects/{id}/archive')
    assert_equal 'sibling', sibling.dig('specification', 'basis')
    assert_equal false, sibling.dig('specification', 'path_gap')

    missing = row_for(rows, 'PUT', '/api/items/{id}')
    assert_equal true, missing.dig('specification', 'path_gap')
    assert_equal %w[revision], missing.dig('implementation', 'query_parameters')
    refute_equal missing.dig('implementation', 'query_parameters'),
                  missing.dig('contract', 'query_parameters')
  end

  def test_wrong_verb_specification_citation_is_rejected
    fixture = topic2_fixture
    fixture['operations'][0]['method'] = 'POST'
    fixture['operations'][0]['path'] = '/api/items'
    fixture['operations'][0]['implementation'] = { 'controller' => 'items', 'method' => 'create' }
    fixture['operations'][0]['specification'] = {
      'basis' => 'explicit',
      'citations' => ['- GET /api/items lists items.'],
      'query_parameters' => %w[page size]
    }
    # Keep the second source record present; the first row is deliberately a
    # POST operation whose cited evidence establishes only GET.
    write('topic2.json', JSON.generate(fixture))
    _report, _stdout, stderr, status = execute_tool_result
    assert_equal 2, status.exitstatus
    assert_match(/does not establish POST \/api\/items/, stderr)
  end

  def test_uncited_route_is_unverified_not_a_documentation_gap
    fixture = topic2_fixture
    fixture['operations'][0]['specification'] = nil
    write('topic2.json', JSON.generate(fixture))
    report, = execute_tool
    row = row_for(report.dig('topic2', 'operations'), 'GET', '/api/items')
    assert_equal 'unverified', row.dig('specification', 'mapping_status')
    assert_equal false, row.dig('specification', 'path_gap')
    assert_nil row.dig('parameter_comparison', 'specification_equals_implementation')
  end

  def test_query_parameter_citation_from_wrong_operation_is_rejected
    fixture = topic2_fixture
    unrelated_quote = '- GET /api/other?size={size} lists other records.'
    spec_text = File.read(@spec_path)
    spec_text = "#{spec_text}#{unrelated_quote}\n"
    write('backend-spec.md', spec_text)
    fixture['operations'][0]['specification']['query_parameters'] = %w[page size]
    fixture['operations'][0]['specification']['query_parameter_citations'] = {
      'page' => ['- Every collection endpoint is paginated. The query parameters page and size control results.'],
      'size' => [unrelated_quote]
    }
    write('topic2.json', JSON.generate(fixture))
    _report, _stdout, stderr, status = execute_tool_result
    assert_equal 2, status.exitstatus
    assert_match(/query citation for size is not operation-specific/, stderr)
  end

  def test_topic6_keeps_exact_contract_facts_and_does_not_infer_review
    report, _stdout, stderr = execute_tool
    assert_equal '', stderr
    operations = report.fetch('topic6').fetch('operations')
    assert_equal UPLOADS.length, operations.length
    assert_equal UPLOADS, operations.map { |operation| [operation.fetch('method'), operation.fetch('path')] }
    assert operations.all? { |operation| operation.fetch('protected') }

    operations.each do |operation|
      operation.fetch('statuses').each do |status|
        assert_equal true, status.fetch('applicable')
        assert_equal 'not_observed', status.dig('runtime_body_evidence', 'status')
        contract = status.fetch('generated_contract')
        artifact_413 = operation.fetch('path') == '/api/projects/{projectId}/artifacts' &&
                       status.fetch('status') == '413'
        assert_equal !artifact_413, contract.fetch('declared')
        if artifact_413
          assert_nil contract.fetch('description')
          assert_nil contract.fetch('schemas')
        else
          expected_description = if status.fetch('status') == '400' && operation.fetch('path') == '/api/ideas/pdf'
                                   'Oversized parts must return 400.'
                                 elsif status.fetch('status') == '400'
                                   'Malformed request.'
                                 else
                                   'Oversized file rejected by parser.'
                                 end
          assert_equal expected_description, contract.fetch('description')
          assert_equal(
            { 'application/json' => error_schema },
            contract.fetch('schemas')
          )
        end
      end
    end

    pdf_uploads = operations.take(3)
    pdf_uploads.each do |operation|
      assert_equal %w[400 413], operation.fetch('statuses').map { |status| status.fetch('status') }.sort
      response_413 = operation.fetch('statuses').find { |status| status.fetch('status') == '413' }
      assert_equal true, response_413.dig('generated_contract', 'declared')
      assert_equal 'Oversized file rejected by parser.',
                   response_413.dig('generated_contract', 'description')
      assert_equal(
        { 'application/json' => { 'type' => 'object', 'properties' => { 'message' => { 'type' => 'string' } } } },
        response_413.dig('generated_contract', 'schemas')
      )
      assert_equal %w[aggregate_limit per_file_limit],
                   response_413.fetch('triggers').map { |trigger| trigger.fetch('kind') }.sort
      assert_equal 'not_observed', response_413.dig('runtime_body_evidence', 'status')
    end

    artifact = operations.last
    artifact_413 = artifact.fetch('statuses').find { |status| status.fetch('status') == '413' }
    assert_equal true, artifact_413.fetch('applicable')
    assert_equal false, artifact_413.dig('generated_contract', 'declared')
    assert_nil artifact_413.dig('generated_contract', 'description')
    assert_nil artifact_413.dig('generated_contract', 'schemas')
    assert_equal %w[aggregate_limit per_file_limit],
                 artifact_413.fetch('triggers').map { |trigger| trigger.fetch('kind') }.sort
    per_file_citations = artifact_413.fetch('triggers')
                                     .find { |trigger| trigger.fetch('kind') == 'per_file_limit' }
                                     .fetch('citations').map { |citation| citation.fetch('text') }
    aggregate_citations = artifact_413.fetch('triggers')
                                       .find { |trigger| trigger.fetch('kind') == 'aggregate_limit' }
                                       .fetch('citations').map { |citation| citation.fetch('text') }
    assert_includes per_file_citations,
                    'assertEquals(413, perFileResponse.statusCode(), responseText(perFileResponse));'
    assert_includes aggregate_citations,
                    'assertEquals(413, aggregateResponse.statusCode(), responseText(aggregateResponse));'

    all_statuses = operations.flat_map { |operation| operation.fetch('statuses') }
    assert all_statuses.all? { |status| status.dig('review', 'disposition') != 'accurate' }
    assert all_statuses.all? { |status| status.dig('review', 'status') == 'not_reviewed' }
  end

  def test_aggregate_only_evidence_does_not_cover_per_file_trigger
    report, = execute_tool
    fixture = topic6_fixture
    pdf_413 = fixture['operations'][0]['statuses'].find { |status| status['status'] == '413' }
    pdf_413['triggers'] = [pdf_413['triggers'].find { |trigger| trigger['kind'] == 'aggregate_limit' }]
    write('topic6.json', JSON.generate(fixture))
    report_without_review, = execute_tool
    binding = report_without_review.dig('topic6', 'operations', 0, 'statuses', 1, 'review', 'binding_sha256')
    pdf_413['review'] = {
      'disposition' => 'accurate',
      'rationale' => 'The aggregate request limit is described.',
      'binding_sha256' => binding
    }
    write('topic6.json', JSON.generate(fixture))

    updated, = execute_tool
    review = updated.dig('topic6', 'operations', 0, 'statuses', 1, 'review')
    assert_equal 'incomplete_evidence', review.fetch('status')
    assert_nil review.fetch('disposition')
    assert_equal ['per_file_limit'], review.fetch('missing_trigger_kinds')
  end

  def test_verified_oversized_part_400_conflict_is_retained_as_auditor_judgment
    fixture = topic6_fixture
    pdf_400 = fixture['operations'][0]['statuses'].find { |status| status['status'] == '400' }
    pdf_400['trigger'] = 'The oversized uploaded part is rejected by multipart parsing.'
    pdf_400['triggers'] = [{
      'kind' => 'per_file_limit',
      'description' => 'A single file exceeds 10 MB.',
      'citations' => [{ 'source' => 'runtime', 'text' => 'Per-file PDF parser ceiling is 10 MB for each uploaded file.' }]
    }]
    write('topic6.json', JSON.generate(fixture))
    initial, = execute_tool
    binding = initial.dig('topic6', 'operations', 0, 'statuses', 0, 'review', 'binding_sha256')
    pdf_400['review'] = {
      'disposition' => 'inaccurate',
      'rationale' => 'This 400 description assigns an oversized part to 400, while the verified parser-level response is 413.',
      'binding_sha256' => binding
    }
    write('topic6.json', JSON.generate(fixture))

    report, = execute_tool
    status_400 = report.dig('topic6', 'operations', 0, 'statuses', 0)
    status_413 = report.dig('topic6', 'operations', 0, 'statuses', 1)
    assert_equal 'Oversized parts must return 400.', status_400.dig('generated_contract', 'description')
    assert_equal 'Oversized file rejected by parser.', status_413.dig('generated_contract', 'description')
    assert_equal 'inaccurate', status_400.dig('review', 'disposition')
    assert_equal 'auditor; not machine-validated', status_400.dig('review', 'judgment_source')
    assert_includes status_413.fetch('triggers').map { |trigger| trigger.fetch('kind') }, 'per_file_limit'
  end

  def test_bound_accurate_review_is_stored_as_auditor_judgment_not_machine_result
    fixture = topic6_fixture
    write('topic6.json', JSON.generate(fixture))
    first, = execute_tool
    operation_index = 0
    status_index = 0
    binding = first.dig('topic6', 'operations', operation_index, 'statuses', status_index, 'review', 'binding_sha256')
    status = fixture['operations'][operation_index]['statuses'][status_index]
    status['review'] = {
      'disposition' => 'accurate',
      'rationale' => 'The declared description covers the cited malformed-metadata trigger.',
      'binding_sha256' => binding
    }
    write('topic6.json', JSON.generate(fixture))

    report, = execute_tool
    review = report.dig('topic6', 'operations', operation_index, 'statuses', status_index, 'review')
    assert_equal 'reviewed_by_auditor', review.fetch('status')
    assert_equal 'accurate', review.fetch('disposition')
    assert_equal 'auditor; not machine-validated', review.fetch('judgment_source')
    assert_equal true, review.fetch('binding_matches')
  end

  def test_changed_contract_invalidates_bound_semantic_review
    fixture = topic6_fixture
    write('topic6.json', JSON.generate(fixture))
    first, = execute_tool
    binding = first.dig('topic6', 'operations', 0, 'statuses', 0, 'review', 'binding_sha256')
    status = fixture['operations'][0]['statuses'][0]
    status['review'] = {
      'disposition' => 'accurate',
      'rationale' => 'Reviewed original description.',
      'binding_sha256' => binding
    }
    write('topic6.json', JSON.generate(fixture))
    openapi = openapi_fixture
    openapi['paths']['/api/ideas/pdf']['post']['responses']['400']['description'] = 'Changed description.'
    write('openapi.yaml', JSON.generate(openapi))

    report, = execute_tool
    review = report.dig('topic6', 'operations', 0, 'statuses', 0, 'review')
    assert_equal 'stale_or_unbound', review.fetch('status')
    assert_nil review.fetch('disposition')
    assert_equal false, review.fetch('binding_matches')
  end

  def test_changed_cited_source_invalidates_bound_semantic_review
    fixture = topic6_fixture
    write('topic6.json', JSON.generate(fixture))
    first, = execute_tool
    binding = first.dig('topic6', 'operations', 0, 'statuses', 0, 'review', 'binding_sha256')
    fixture['operations'][0]['statuses'][0]['review'] = {
      'disposition' => 'accurate',
      'rationale' => 'Reviewed the cited parser condition.',
      'binding_sha256' => binding
    }
    write('topic6.json', JSON.generate(fixture))
    write('runtime-evidence.txt', "#{File.read(@source_path)}Additional cited-source context.\n")

    report, = execute_tool
    review = report.dig('topic6', 'operations', 0, 'statuses', 0, 'review')
    assert_equal 'stale_or_unbound', review.fetch('status')
    assert_nil review.fetch('disposition')
    assert_equal false, review.fetch('binding_matches')
  end

  def test_tool_is_explicit_path_only_and_writes_only_in_supplied_output_directory
    report, stdout, = execute_tool
    assert_equal 'compliance-03-evidence/v1', report.fetch('format')
    assert_match(%r{\AWrote #{Regexp.escape(@output_dir)}/compliance-03-evidence\.json\n\z}, stdout)
    assert_equal ['compliance-03-evidence.json'], Dir.children(@output_dir)
    report_files = Dir.glob(File.join(@directory, '**', 'compliance-03-evidence.json'))
    assert_equal [File.join(@output_dir, 'compliance-03-evidence.json')], report_files
    assert_includes report.dig('interpretation', 'machine_validated'),
                    'controller query parameters are extracted from verb-matched Java controller methods'
  end

  def test_topic6_requires_all_four_uploads_and_both_status_rows
    fixture = topic6_fixture
    fixture['operations'].pop
    write('topic6.json', JSON.generate(fixture))
    _report, _stdout, stderr, status = execute_tool_result
    assert_equal 2, status.exitstatus
    assert_match(/missing required upload/, stderr)
  end

  private

  def write(name, content)
    path = File.join(@directory, name)
    File.write(path, content)
    path
  end

  def execute_tool
    report, stdout, stderr, status = execute_tool_result
    assert status.success?, "tool failed (#{status.exitstatus}): #{stderr}"
    assert File.file?(File.join(@output_dir, 'compliance-03-evidence.json'))
    [report, stdout, stderr]
  end

  def execute_tool_result
    FileUtils.rm_rf(@output_dir) if File.exist?(@output_dir)
    args = [
      RbConfig.ruby, SCRIPT,
      '--spec', @spec_path,
      '--openapi', @openapi_path,
      '--topic2', @topic2_path,
      '--topic6', @topic6_path,
      '--controller', "items=#{@list_controller}",
      '--controller', "projects=#{@project_controller}",
      '--source', "runtime=#{@source_path}",
      '--output-dir', @output_dir
    ]
    stdout, stderr, status = Open3.capture3(*args)
    report_path = File.join(@output_dir, 'compliance-03-evidence.json')
    report = File.file?(report_path) ? JSON.parse(File.read(report_path)) : nil
    [report, stdout, stderr, status]
  end

  def openapi_fixture
    paths = {}
    add_operation = lambda do |path, method, params = [], responses = {}|
      paths[path] ||= {}
      paths[path][method.downcase] = {
        'parameters' => params.map { |name| { 'name' => name, 'in' => 'query' } },
        'responses' => responses
      }
    end
    add_operation.call('/api/items', 'GET', %w[page size], {})
    add_operation.call('/api/items', 'POST', %w[page size], {})
    add_operation.call('/api/items/{id}', 'PUT', %w[contractOnly], {})
    add_operation.call('/api/projects/{id}/archive', 'GET', [], {})

    UPLOADS.each do |method, path|
      responses = {
        '400' => {
          'description' => path == '/api/ideas/pdf' ? 'Oversized parts must return 400.' : 'Malformed request.',
          'content' => { 'application/json' => { 'schema' => { 'type' => 'object', 'properties' => { 'message' => { 'type' => 'string' } } } } }
        }
      }
      unless path == '/api/projects/{projectId}/artifacts'
        responses['413'] = {
          'description' => 'Oversized file rejected by parser.',
          'content' => { 'application/json' => { 'schema' => { 'type' => 'object', 'properties' => { 'message' => { 'type' => 'string' } } } } }
        }
      end
      add_operation.call(path, method, [], responses)
    end
    { 'openapi' => '3.0.3', 'paths' => paths }
  end

  def error_schema
    { 'type' => 'object', 'properties' => { 'message' => { 'type' => 'string' } } }
  end

  def topic2_fixture
    {
      'operations' => [
        {
          'method' => 'GET',
          'path' => '/api/items',
          'specification' => {
            'basis' => 'explicit',
            'citations' => ['- GET /api/items lists items.'],
            'query_parameters' => %w[page size],
            'query_parameter_citations' => {
              'page' => ['- Every collection endpoint is paginated. The query parameters page and size control results.'],
              'size' => ['- Every collection endpoint is paginated. The query parameters page and size control results.']
            }
          },
          'implementation' => { 'controller' => 'items', 'method' => 'list' }
        },
        {
          'method' => 'POST',
          'path' => '/api/items',
          'specification' => {
            'basis' => 'same_route',
            'base_method' => 'GET',
            'base_path' => '/api/items',
            'citations' => ['- POST /api/items uses the same route as GET /api/items.'],
            'query_parameters' => [],
            'query_parameter_citations' => {}
          },
          'implementation' => { 'controller' => 'items', 'method' => 'create' }
        },
        {
          'method' => 'GET',
          'path' => '/api/projects/{id}/archive',
          'specification' => {
            'basis' => 'sibling',
            'base_method' => 'GET',
            'base_path' => '/api/projects/{id}',
            'suffix' => '/archive',
            'citations' => ['- GET /api/projects/{id}; sibling subresource /api/projects/{id}/archive.'],
            'query_parameters' => [],
            'query_parameter_citations' => {}
          },
          'implementation' => { 'controller' => 'projects', 'method' => 'archive' }
        },
        {
          'method' => 'PUT',
          'path' => '/api/items/{id}',
          'specification' => {
            'basis' => 'gap',
            'scope' => 'The cited collection entry is the complete approved item-route inventory for this operation.',
            'citations' => ['- GET /api/items lists items.'],
            'query_parameters' => [],
            'query_parameter_citations' => {}
          },
          'implementation' => { 'controller' => 'items', 'method' => 'update' }
        }
      ]
    }
  end

  def topic6_fixture
    {
      'operations' => UPLOADS.map do |method, path|
        protected = true
        rows = %w[400 413].map do |status|
          applicable = true
          trigger = if status == '400'
                      'Malformed multipart request or validation input.'
                    else
                      'Multipart size limit is exceeded.'
                    end
          triggers = if status == '413' && applicable
                       [
                         {
                           'kind' => 'per_file_limit',
                           'description' => 'A single PDF exceeds 10 MB.',
                           'citations' => [
                             { 'source' => 'runtime', 'text' => 'Per-file PDF parser ceiling is 10 MB for each uploaded file.' }
                           ] + if path == '/api/projects/{projectId}/artifacts'
                                 [{ 'source' => 'runtime', 'text' => 'assertEquals(413, perFileResponse.statusCode(), responseText(perFileResponse));' }]
                               else
                                 []
                               end
                         },
                         {
                           'kind' => 'aggregate_limit',
                           'description' => 'The complete multipart request exceeds 11 MB.',
                           'citations' => [
                             { 'source' => 'runtime', 'text' => 'Aggregate multipart request ceiling is 11 MB across all parts.' }
                           ] + if path == '/api/projects/{projectId}/artifacts'
                                 [{ 'source' => 'runtime', 'text' => 'assertEquals(413, aggregateResponse.statusCode(), responseText(aggregateResponse));' }]
                               else
                                 []
                               end
                         }
                       ]
                     elsif status == '400'
                       [{
                         'kind' => 'malformed_request',
                         'description' => 'Multipart or request validation fails.',
                         'citations' => [{ 'source' => 'runtime', 'text' => 'A malformed multipart request may produce a 400 response.' }]
                       }]
                     else
                       []
                     end
          {
            'status' => status,
            'applicable' => applicable,
            'trigger' => trigger,
            'triggers' => triggers,
            'runtime_body' => { 'status' => 'not_observed' }
          }
        end
        {
          'method' => method,
          'path' => path,
          'protected' => protected,
          'access_citation' => {
            'source' => 'runtime',
            'text' => 'All upload routes require an authenticated project role.'
          },
          'statuses' => rows
        }
      end
    }
  end

  def row_for(rows, method, path)
    rows.find { |row| row['method'] == method && row['path'] == path } || flunk("missing #{method} #{path}")
  end
end

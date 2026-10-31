# SubmIT - build, test and run the complete application.
# Run `make help` for available targets.

SHELL := /bin/bash
.DEFAULT_GOAL := help

ifneq (,$(wildcard .env))
include .env
export
endif

BACKEND  := backend
FRONTEND := frontend
FRONTEND_DEV := cd $(FRONTEND) && (npm ci || npm install) && npm run dev

.PHONY: help env install format-markdown build build-backend build-frontend test test-backend test-frontend \
        startup-config-check \
        check run run-testing run-testing-minimal run-testing-tgm testing-stack run-backend run-backend-testing \
        run-frontend openapi clean doc

help: ## Show this help
	@awk 'BEGIN {FS = ":.*##"} /^[a-zA-Z_-]+:.*##/ {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

doc: ## Generate local screenshot guides: make doc LANGUAGE=en|de
	node frontend/scripts/documentation/run.mjs --language "$(LANGUAGE)"

env: ## Create .env from .env.example if missing
	@test -f .env || { cp .env.example .env && echo "Created .env - review the values."; }

install: ## Install frontend dependencies
	cd $(FRONTEND) && npm ci || (cd $(FRONTEND) && npm install)

format-markdown: install ## Format Markdown files: make format-markdown FILES="path/to/file.md ..."
	@test -n "$(strip $(FILES))" || { echo 'Usage: make format-markdown FILES="path/to/file.md ..."' >&2; exit 2; }
	cd $(FRONTEND) && npx prettier --write $(addprefix ../,$(FILES))

build: build-backend build-frontend ## Build backend and frontend

build-backend: ## Assemble the backend (no tests)
	cd $(BACKEND) && ./gradlew assemble

build-frontend: install ## Typecheck and build the frontend
	cd $(FRONTEND) && npm run build

test: test-backend test-frontend ## Run all tests

test-backend: ## Run backend tests
	cd $(BACKEND) && ./gradlew test

test-frontend: install ## Typecheck and run frontend tests
	cd $(FRONTEND) && npm run typecheck && npm test

check: startup-config-check ## Full verification: startup config, backend checkstyle, build, tests
	cd $(BACKEND) && ./gradlew checkstyleMain
	$(MAKE) build test

startup-config-check: ## Verify sample, Compose and backend startup configuration wiring
	python3 scripts/check-startup-config.py

run: env ## Start backend and frontend dev server (Ctrl+C stops both)
	@trap 'kill 0' INT TERM EXIT; \
	$(MAKE) --no-print-directory run-backend & \
	$(MAKE) --no-print-directory run-frontend & \
	wait

run-testing: TESTING_FIXTURE_ENV=env -u DADB_TESTING_MINIMAL_FIXTURES
run-testing-minimal: TESTING_FIXTURE_ENV=env DADB_TESTING_MINIMAL_FIXTURES=true
run-testing-tgm: TESTING_FIXTURE_ENV=env DADB_TESTING_MINIMAL_FIXTURES=true DADB_TESTING_ALLOCATION_PIN_PREVIEW=false DADB_TESTING_APPLYING_GROUP_PREVIEW=false DADB_TESTING_ELIGIBLE_FORMAL_PROJECTS_PREVIEW=false DADB_TESTING_FORMAL_PROPOSAL_SEARCH_PREVIEW=false DADB_TESTING_GRADING_SELECTION_PREVIEW=false DADB_BOOTSTRAPADMIN_USERNAME= DADB_BOOTSTRAPADMIN_PASSWORD= dadb.testing.applying-group-preview=false dadb.testing.eligible-formal-project-preview=false dadb.testing.formal-proposal-search-preview=false dadb.testing.grading-selection-preview=false
run-testing: TESTING_PROFILE=testing
run-testing-minimal: TESTING_PROFILE=testing
run-testing-tgm: TESTING_PROFILE=testing,testing-tgm
run-testing: testing-stack ## Start backend with full testing fixtures and frontend dev server
run-testing-minimal: testing-stack ## Start testing backend with minimal fixtures and frontend dev server
run-testing-tgm: testing-stack ## Start the isolated TGM workbook testing profile and frontend dev server

TESTING_PROFILE ?= testing
TESTING_FIXTURE_ENV ?= env -u DADB_TESTING_MINIMAL_FIXTURES

testing-stack: env
	@trap 'kill 0' INT TERM EXIT; \
	(cd $(BACKEND) && $(TESTING_FIXTURE_ENV) ./gradlew bootRun --args='--spring.profiles.active=$(TESTING_PROFILE)') & \
	($(FRONTEND_DEV)) & \
	wait

run-backend: ## Start the backend on :8080
	$(call admin-config-warning)
	cd $(BACKEND) && ./gradlew bootRun

define admin-config-warning
	@test -n "$(DADB_BOOTSTRAPADMIN_USERNAME)" -a -n "$(DADB_BOOTSTRAPADMIN_PASSWORD)" || echo "Note: no initial admin configured (see .env.example)"
endef

.PHONY: admin-config-diagnostic
admin-config-diagnostic: ## Print the startup administrator diagnostic without starting the backend
	$(call admin-config-warning)

run-backend-testing: ## Start the backend on :8080 with the testing profile (seeded accounts)
	cd $(BACKEND) && env -u DADB_TESTING_MINIMAL_FIXTURES ./gradlew bootRun --args='--spring.profiles.active=testing'

run-frontend: ## Install dependencies and start the Vite dev server
	$(FRONTEND_DEV)

openapi: ## Regenerate contracts/openapi.yaml
	cd $(BACKEND) && ./gradlew generateOpenApi

clean: ## Remove build outputs
	cd $(BACKEND) && ./gradlew clean
	rm -rf $(FRONTEND)/dist

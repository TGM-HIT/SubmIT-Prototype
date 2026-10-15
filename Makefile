# FlightLog - build, test and run the complete application.
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
        check run run-testing run-testing-minimal testing-stack run-backend run-backend-testing \
        run-frontend openapi clean

help: ## Show this help
	@awk 'BEGIN {FS = ":.*##"} /^[a-zA-Z_-]+:.*##/ {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

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

check: ## Full verification: backend checkstyle, build, tests
	cd $(BACKEND) && ./gradlew checkstyleMain
	$(MAKE) build test

run: env ## Start backend and frontend dev server (Ctrl+C stops both)
	@trap 'kill 0' INT TERM EXIT; \
	$(MAKE) --no-print-directory run-backend & \
	$(MAKE) --no-print-directory run-frontend & \
	wait

run-testing: TESTING_FIXTURE_ENV=env -u DADB_TESTING_MINIMAL_FIXTURES
run-testing-minimal: TESTING_FIXTURE_ENV=env DADB_TESTING_MINIMAL_FIXTURES=true
run-testing: testing-stack ## Start backend with full testing fixtures and frontend dev server
run-testing-minimal: testing-stack ## Start testing backend with minimal fixtures and frontend dev server

testing-stack: env
	@trap 'kill 0' INT TERM EXIT; \
	(cd $(BACKEND) && $(TESTING_FIXTURE_ENV) ./gradlew bootRun --args='--spring.profiles.active=testing') & \
	($(FRONTEND_DEV)) & \
	wait

run-backend: ## Start the backend on :8080
	@test -n "$(FLIGHTLOG_ADMIN_USERNAME)" || echo "Note: no initial admin configured (see .env.example)"
	cd $(BACKEND) && ./gradlew bootRun

run-backend-testing: ## Start the backend on :8080 with the testing profile (seeded accounts)
	cd $(BACKEND) && env -u DADB_TESTING_MINIMAL_FIXTURES ./gradlew bootRun --args='--spring.profiles.active=testing'

run-frontend: ## Install dependencies and start the Vite dev server
	$(FRONTEND_DEV)

openapi: ## Regenerate contracts/openapi.yaml
	cd $(BACKEND) && ./gradlew generateOpenApi

clean: ## Remove build outputs
	cd $(BACKEND) && ./gradlew clean
	rm -rf $(FRONTEND)/dist

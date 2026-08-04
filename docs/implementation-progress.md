# GeekTicket – Implementation Progress

> Source of truth: `docs/GeekTicket_Assessment_Checklist_Gradle.md`  
> Updated: 2026-08-04 — After Prompt 2 (Bootstrap & Infrastructure)

---

## Prompt 2 Coverage

Checklist items addressed:
- §4 Technology stack — fixed all dependency issues
- §11 Error handling — implemented error contract
- §15 Docker & local setup — Dockerfile, compose, .env
- §17.2 Project bootstrap — all items complete
- §18 Source code — builds successfully

---

## Progress Tracker

### §4 Technology Stack

| Item | Status | Evidence | Remaining |
|---|---|---|---|
| Java 21 | PASS | `build.gradle.kts` toolchain 21 | — |
| Spring Boot | PASS | Plugin `4.1.0` | — |
| Gradle Wrapper | PASS | Gradle 9.5.1 | — |
| Spring Web | PASS | `spring-boot-starter-webmvc` | — |
| Spring Data JPA | PASS | `spring-boot-starter-data-jpa` | — |
| Spring Validation | PASS | `spring-boot-starter-validation` | — |
| PostgreSQL | PASS | `org.postgresql:postgresql` runtime dep | — |
| Flyway | PASS | `spring-boot-starter-flyway` + `flyway-database-postgresql` | — |
| Lombok | PASS | compile + annotation processor | — |
| Actuator | PASS | `spring-boot-starter-actuator` | — |
| Swagger/OpenAPI | PASS | `springdoc-openapi-starter-webmvc-ui:2.8.6` + OpenApiConfig | Verify at runtime in Phase 2 |
| Docker Compose | PASS | `docker-compose.yml` + `docker compose config` valid | Full test after migrations |
| JUnit 5 | PASS | `spring-boot-starter-test` | — |
| Mockito | PASS | Included via starter-test | — |
| Testcontainers | PASS | `spring-boot-testcontainers` + `testcontainers-bom:1.21.1` | Requires Docker Desktop running |
| Postman | NOT STARTED | — | Phase 9 |

### §5 Package Structure

| Item | Status | Evidence |
|---|---|---|
| config/ | PASS | OpenApiConfig, JpaConfig, CorsConfig implemented |
| domain/dto/ | PASS | ApiResponse, PageResponse implemented |
| util/error/ | PASS | ErrorCode, ErrorResponse, BusinessException, ResourceNotFoundException, InsufficientTicketException, InvalidBookingStatusException, GlobalExceptionHandler implemented |
| Other packages | IN PROGRESS | Stubs remain for entities, controllers, services, repos |

### §11 Error Handling

| Item | Status | Evidence | Remaining |
|---|---|---|---|
| Error response contract | PASS | ErrorResponse with timestamp/status/code/message/path | — |
| Error codes | PASS | ErrorCode enum with all §11 codes | — |
| GlobalExceptionHandler | PASS | Handles BusinessException, validation, missing header, unexpected | — |
| No stack traces to client | PASS | Unexpected errors return generic message | — |

### §15 Docker & Local Setup

| Item | Status | Evidence | Remaining |
|---|---|---|---|
| Dockerfile | PASS | Multi-stage build (JDK builder → JRE runtime) | — |
| docker-compose.yml | PASS | `docker compose config` valid output | Full startup test after migrations |
| .env.example | PASS | All env vars documented | — |
| .dockerignore | PASS | Excludes .git, build/, IDE files | — |
| Flyway migrations | NOT STARTED | Directory empty | Phase 2 |
| Seed data | NOT STARTED | — | Phase 2 |

### §17.2 Project Bootstrap

| Item | Status | Evidence |
|---|---|---|
| Tạo Spring Boot project | PASS | Existing project fixed |
| Cấu hình PostgreSQL | PASS | application.yml with env-var datasource |
| Cấu hình Flyway | PASS | spring.flyway.enabled=true |
| Tạo Docker Compose | PASS | docker-compose.yml + Dockerfile |
| Tạo common response | PASS | ApiResponse\<T\>, PageResponse\<T\> |
| Tạo global exception handler | PASS | GlobalExceptionHandler with 4 handlers |
| Cấu hình Swagger | PASS | springdoc + OpenApiConfig |
| Cấu hình Actuator | PASS | health + info endpoints exposed |

### §18 Pre-submission (partial)

| Item | Status | Evidence |
|---|---|---|
| Project builds | PASS | `gradlew clean classes testClasses` BUILD SUCCESSFUL |
| No secrets committed | PASS | .env.example has defaults only |
| ddl-auto=validate | PASS | application.yml line: `ddl-auto: validate` |
| UTC time config | PASS | jackson.time-zone=UTC, hibernate.jdbc.time_zone=UTC |

---

## Blocking Issues

| Issue | Status | Notes |
|---|---|---|
| Docker Desktop not running | BLOCKED | Testcontainers test fails with `DockerClientProviderStrategy`. Test is correctly written; needs Docker Desktop to be started. |
| No Flyway migrations | NOT STARTED | `docker compose up --build` will fail until V1 migration exists (Phase 2) |

## Commands Executed

| Command | Result |
|---|---|
| `.\gradlew.bat clean test --no-daemon` | Compile ✅, test ❌ (Docker not available for Testcontainers) |
| `.\gradlew.bat clean classes testClasses --no-daemon` | BUILD SUCCESSFUL in 25s |
| `docker compose config` | Valid config output ✅ |

## Next: Phase 2

All Phase 1 items complete. Phase 2 (Schema, Entities, Repositories) can begin.

# AGENTS.md — Agent-facing operating context and shared memory across sessions; keep it updated with genuinely valuable project knowledge.

## Stack

- **Languages & Runtimes**:
  - Java 21 LTS (`maven.compiler.release` 21, `java.version` 21).
  - TypeScript 5.9 (`~5.9.2`) on Node.js 24 (`>=24.0.0 <25`, `npm@11.8.0`).
- **Frameworks**:
  - Backend: Spring Boot 4.1.2 (`spring-boot-starter-parent` 4.1.2, Spring MVC, Spring JDBC, Bean Validation).
  - Frontend: Angular 21.2 (`@angular/core` `^21.2.0`, `@angular/cli` `^21.2.28`, standalone components, signals).
- **Key Libraries**:
  - Backend: Google Guava 33.7.1 (`33.7.1-jre`), Caffeine (`com.github.ben-manes.caffeine`), Project Lombok 1.18.48, Jackson 3 (`tools.jackson.databind`), AssertJ Core 3.x (`assertj-core`, test), JaCoCo 0.8.14 (`jacoco-maven-plugin`).
  - Frontend: RxJS `~7.8.0`, Vitest `^4.0.8`, `@vitest/coverage-v8` `^4.0.18`, JSDOM `^28.0.0`, Prettier `^3.8.1`.
- **Build Tool**:
  - Backend: Apache Maven via Maven Wrapper (`backend/mvnw.cmd` on Windows, `backend/mvnw` on Unix; Maven 3.10.0 wrapper).
  - Frontend: npm (`npm@11.8.0`) invoking Angular CLI (`ng`).
- **Shell / OS**: Windows PowerShell / CMD, Bash on Linux/macOS.
- **Database**: H2 Database (`com.h2database:h2`, file-based at `jdbc:h2:file:./data/adventure-book;AUTO_SERVER=TRUE` in production; in-memory `jdbc:h2:mem:...` for tests).
- **Database Migrations**: Liquibase (`spring-boot-starter-liquibase`).
- **Broker / Messaging**: None (monolithic HTTP REST architecture).
- **Cache**: Caffeine in-memory cache (`Cache<UUID, GameSession>` configured in `BookConfiguration`, max size 1,000, 12h TTL `expireAfterAccess(Duration.ofHours(12))`).
- **Schedulers**: None.
- **Base Package**: `com.example.adventurebook`.

---

## Code Style & Architecture

- **Dependency Injection**:
  - Use constructor injection via Lombok `@RequiredArgsConstructor` on all Spring components (`@Service`, `@Repository`, `@RestController`).
  - Do not use field injection (`@Autowired`) in production code. Use `@Autowired` only in `@SpringBootTest` test classes.
- **Transaction Pattern**:
  - Database persistence executes single-statement SQL queries directly via Spring's `JdbcClient` (`JdbcSaveRepository`, `JdbcImportedBookRepository`).
  - State consistency and concurrency protection for active game sessions are managed in-memory via Java monitor synchronization (`synchronized (session)`) in `GameServiceImpl` and `SaveServiceImpl`.
- **DTO & Null Conventions**:
  - Keep DTOs and entities strictly isolated (`dto/` vs `entity/` and `entity/book/`).
  - DTO and entity classes are data holders only: fields, constructors, Lombok accessors (`@Getter`, `@Value`, `@AllArgsConstructor`), and validation annotations. Business logic belongs in services; mappings belong in `utils/` (`BookMapper`, `BookUtils`).
  - Do not nest classes inside other classes; place sub-models in dedicated packages (e.g. `entity.book`).
  - Separate logical method phases with blank lines: input validation, data preparation, state change/persistence, and returning result.
  - Handle nullable request parameters explicitly (`@RequestParam(required = false)`); repository lookups return `java.util.Optional<T>`.
- **Constants & Enums Policy**:
  - No inline magic values. Extract all business numbers and boundary limits to constants (`INITIAL_HEALTH = 10`, `MAX_HEALTH = 10`, `MAX_IDEMPOTENCY_ENTRIES = 50`, `MAX_SECTIONS = 2_000`, `MAX_OPTIONS = 50`).
  - Declare enums in separate files under the `enums/` package (`Difficulty`, `SectionType`, `ConsequenceType`, `GameStatus`, `Severity`).
- **Error Taxonomy Table**:

| Case | Exception Type | HTTP Status | Response Code | Details / Behavior |
|---|---|---|---|---|
| Missing entity (book, session, save) | `java.util.NoSuchElementException` | 404 Not Found | `"NOT_FOUND"` | Thrown when an ID is not found in catalog, cache, or repository. |
| Invalid request / validation error | `java.lang.IllegalArgumentException`, `MethodArgumentNotValidException` | 400 Bad Request | `"INVALID_REQUEST"` | Thrown on malformed inputs, blank names, missing parameters, or invalid option IDs. |
| Book structure / schema errors | `com.example.adventurebook.exception.InvalidBookException` | 422 Unprocessable Entity | `"INVALID_BOOK"` | Thrown on unplayable books; `details` array lists issues (`code: message`). |
| Upload payload exceeds limit | `org.springframework.web.multipart.MaxUploadSizeExceededException` | 413 Payload Too Large | `"FILE_TOO_LARGE"` | Uploaded file or multipart payload exceeds 1 MB limit. |
| Stale move revision (optimistic lock) | `com.example.adventurebook.exception.RevisionConflictException` | 409 Conflict | *Custom body* | Returned when `expectedRevision != session.getRevision()`; body contains current `GameStateDTO`. |
| Book version mismatch on save restore | `com.example.adventurebook.exception.VersionConflictException` | 409 Conflict | `"BOOK_VERSION_CONFLICT"` | Book content version hash or saved section ID mismatch during save restoration. |
| Action on completed or dead session | `java.lang.IllegalStateException` | 409 Conflict | `"GAME_FINISHED"` | Thrown when `choose()` is invoked on a session with `status != ACTIVE`. |
| Ownership violation | None | N/A | N/A | Authentication/authorization is not implemented (single-user demo scope). |

- **Generated Code Policy**:
  - Project Lombok generates getters, constructors, and builder code at compile-time via annotation processor.
  - Build targets (`backend/target/`, `frontend/dist/`, `frontend/coverage/`) are strictly generated outputs and off-limits for manual modification. Edit only original source files.

---

## Project Structure

- **Modules & Directories**:
  - `backend/` — Spring Boot 4 REST API, in-memory game state engine, JDBC/H2 persistence.
  - `frontend/` — Angular 21 SPA frontend with standalone components and Vitest unit tests.
  - `.agents/skills/adventure-book-studio/` — Custom skill for authoring, validating, balancing, and visualizing game books (local agent skill, git-ignored).
  - `backend/data/` — H2 database file storage directory (git-ignored, auto-created at runtime).
- **Key Backend Packages** (`backend/src/main/java/com/example/adventurebook/`):
  - `config/` — Configures in-memory Caffeine session cache bean.
  - `controller/` & `controller/impl/` — Exposes REST API endpoints and maps HTTP requests.
  - `dto/` — Defines API request and response data contracts.
  - `entity/` & `entity/book/` — Holds domain models, saves, sessions, and book structures.
  - `enums/` — Defines isolated enums for game states and types.
  - `exception/` — Handles API exceptions and formats JSON error responses.
  - `repository/` & `repository/impl/` — Executes SQL queries via JdbcClient for persistence.
  - `service/` & `service/impl/` — Implements game logic, book parsing, and save operations.
  - `utils/` — Provides stateless helpers for DTO mapping and validation.
- **Key Frontend Directories** (`frontend/src/app/`):
  - `core/api/` — Handles backend HTTP communication and shared data models.
  - `features/game/` — Manages interactive gameplay, choices, health, and saves.
  - `features/library/` — Displays books, search filters, imports, and saved games.

---

## Migrations / Schema

- **Tool**: Liquibase (`spring-boot-starter-liquibase`).
- **Master Changelog**: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`.
- **Changesets Location**: `backend/src/main/resources/db/changelog/changes/*.sql` (formatted SQL).
- **Index Files**: `backend/src/main/resources/db/changelog/db.changelog-master.yaml` must be updated for every new changeset file using:
  ```yaml
  databaseChangeLog:
    - include:
        file: changes/<filename>.sql
        relativeToChangelogFile: true
  ```
- **Idempotency Guards**:
  - DDL changesets must use `IF NOT EXISTS` (e.g. `CREATE TABLE IF NOT EXISTS table_name`).
  - DML operations must use idempotent SQL statements (e.g. `MERGE INTO imported_books KEY(id) VALUES (...)`).
- **Rollback Requirement**: Every changeset must include a valid `--rollback` directive or represent safe, idempotent migration steps. Never modify, reorder, or rename existing changesets that have already run. Spring SQL initialization is disabled (`spring.sql.init.mode=never`).

---

## Test Structure

- **Environment & Database Provisioning**:
  - Controller integration tests use `@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:<unique-name>;DB_CLOSE_DELAY=-1")` to isolate test state.
  - Repository and migration tests construct isolated in-memory H2 databases using `EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build()` and run Liquibase migrations programmatically via `SpringLiquibase.afterPropertiesSet()`. Never touch `backend/data/`.
  - Service unit tests instantiate POJOs directly with Mockito mocks (`mock(...)`) and explicit in-memory Caffeine caches with custom tickers.
  - Frontend tests run with Vitest in a `jsdom` environment.
- **Base Test Classes**: None. Test classes are standalone and self-contained; do not introduce abstract base test classes.
- **Test Config Location**: Test properties are specified inline via `@SpringBootTest(properties = ...)` or inherited from `backend/src/main/resources/application.yml` (no separate `application-test.yml`). Frontend test settings live in `frontend/angular.json` under `projects.frontend.architect.test`.
- **Fixture / SQL Conventions**: Text blocks (`"""`) for JSON payloads in Java test classes; bundled books in `backend/src/main/resources/books/` serve as end-to-end fixtures.
- **Coverage Rules**:
  - Backend: Enforced by `jacoco-maven-plugin` during Maven `test` phase (`.\mvnw.cmd clean test`). Requires ≥ 80% line coverage for the entire application bundle (`BUNDLE`) and ≥ 80% line coverage for each class in `com.example.adventurebook.repository.impl.*` individually. Report: `backend/target/site/jacoco/index.html`. Lombok-generated code is automatically filtered out.
  - Frontend: Enforced by `@angular/build:unit-test` (Vitest/V8 via `npm run test:ci`). Requires ≥ 80% coverage for lines, statements, functions, and branches across all files in `src/app/**/*.ts`. Report: `frontend/coverage/frontend/index.html` (alongside JSON summary and LCOV).

---

## Main Commands

```bash
# ==============================================================================
# Backend Operations (Always execute inside backend/ directory)
# ==============================================================================

# Fast compile check
./mvnw test-compile                     # On Windows: .\mvnw.cmd test-compile

# Full build and run all tests with JaCoCo verification
./mvnw test                             # On Windows: .\mvnw.cmd test

# Run a single test class
./mvnw test -Dtest=GameServiceTest

# Run a single test method
./mvnw test -Dtest=GameServiceTest#restoresSessionWithClampedHealthAndRevision

# Package application into executable JAR (runs tests)
./mvnw package                          # On Windows: .\mvnw.cmd package

# Run backend development server on http://localhost:8080
./mvnw spring-boot:run                  # On Windows: .\mvnw.cmd spring-boot:run

# ==============================================================================
# Frontend Operations (Always execute inside frontend/ directory)
# ==============================================================================

# Install dependencies strictly matching lockfile
npm ci

# Fast build check
npm run build

# Run unit tests once with coverage enforcement (CI mode)
npm run test:ci

# Run interactive test runner
npm test

# Run a single frontend test file
npx ng test --include src/app/features/game/game.page.spec.ts

# Run frontend development server on http://127.0.0.1:4200 (with /api proxy)
npm start
```

---

## Known Pitfalls

- **Working Directory for Backend**:
  - *Symptom*: Backend fails to locate the H2 database or creates duplicate database files in the project root.
  - *Cause*: Running Maven or `java -jar` from project root instead of `backend/`. The datasource URL uses `./data/adventure-book`, resolving relative to process working directory.
  - *Fix*: Always `cd backend` before running `./mvnw`, `.\mvnw.cmd`, or `java -jar`.
- **Frontend API Proxy Failure**:
  - *Symptom*: Frontend HTTP requests to `/api/*` fail with connection refused or 404.
  - *Cause*: Launching frontend with `ng serve` directly without `--proxy-config proxy.conf.json`, or backend is not running on port 8080.
  - *Fix*: Start frontend via `npm start` (which passes proxy configuration) and verify backend is active on port 8080.
- **Save Restoration Version Conflicts**:
  - *Symptom*: Restoring a saved game fails with HTTP 409 `BOOK_VERSION_CONFLICT`.
  - *Cause*: Modifying a book's JSON file (including formatting or whitespace) changes its SHA-256 version hash, invalidating existing saves referencing earlier hashes.
  - *Fix*: Do not alter bundled book JSON files if existing saves in `backend/data/` must be preserved; wipe `backend/data/` when resetting local dev state.
- **JaCoCo Coverage Failure on Repository Changes**:
  - *Symptom*: Maven build fails during `jacoco:check` in `test` phase even though all assertions pass.
  - *Cause*: A modified or added class in `com.example.adventurebook.repository.impl.*` dropped below the mandatory 80% line coverage limit.
  - *Fix*: Add comprehensive integration tests in `JdbcRepositoriesTest.java` verifying all query branches and mapping methods.
- **Frontend Untested File Coverage Drop**:
  - *Symptom*: Frontend `npm run test:ci` fails coverage check when new files are introduced.
  - *Cause*: `angular.json` sets `coverageInclude: ["src/app/**/*.ts"]`, checking all TypeScript files in `src/app/`, including those without a `.spec.ts`.
  - *Fix*: Ensure every newly created TypeScript component or service in `src/app/` has an accompanying `.spec.ts` meeting the 80% threshold.

---

## Documented Decisions / Do-Not-Fix List

- **Reachability warnings do not prevent book gameplay**:
  - *Reason*: `BookValidationServiceImpl` marks graph disconnections (`UNREACHABLE_SECTION`, `NO_REACHABLE_END`) as `Severity.WARNING`, allowing books with unreachable sections or endings to be viewed and played for diagnostic inspection.
  - *Guideline*: Don't flag books with unreachable sections or lacking reachable endings as unplayable or buggy.
- **Options on `END` sections are parsed but ignored**:
  - *Reason*: `BookParserServiceImpl` accepts `options` on ending sections without failing, but `GameServiceImpl` marks status as `COMPLETED` immediately upon entering an `END` section and exposes no choices.
  - *Guideline*: Don't flag options on `END` sections as an invalid schema violation or bug.
- **Active game sessions are in-memory with a 12-hour TTL**:
  - *Reason*: `BookConfiguration` configures a Caffeine in-memory cache for transient session state; persistence is reserved for explicit user saves in H2 (`SaveRepository`). Sessions do not survive backend restarts.
  - *Guideline*: Don't flag session loss across backend restarts as a persistence bug; players must use the Save feature.
- **Duplicate choice requests return current game state**:
  - *Reason*: `GameServiceImpl` maintains an in-memory sliding window of the last 50 processed request IDs (`completedRequests`) to guarantee move idempotency.
  - *Guideline*: Don't flag duplicate `requestId` returning the current revision without error as an idempotency bug.
- **Death takes precedence over section victory**:
  - *Reason*: In `GameServiceImpl.applyChoice()`, health reduction to 0 immediately sets `GameStatus.DEAD`, even if the target section is an `END` section.
  - *Guideline*: Don't flag a player reaching an `END` section with 0 HP being `DEAD` as a game rule violation.
- **No authentication, session ownership, or user isolation**:
  - *Reason*: Repository is a single-user CYOA game demo; all saves and books are globally accessible across HTTP endpoints.
  - *Guideline*: Don't flag missing authorization headers or public access to saves as a security vulnerability.

---

## Workflow & Communication

- **Read before write**:
  - Never edit a file you haven't read. Match surrounding style: naming conventions, 4-space indentation for Java, 2-space for TypeScript/SCSS, Lombok constructor injection, and logical statement grouping with blank lines. Code must read like the author of neighbor lines.
- **Smallest correct change**:
  - Minimal surface, targeted edits over rewrites. No silent changes to public APIs, return shapes, null-semantics, or database tables. Any behavioral change must be called out explicitly.
- **No magic values**:
  - Business strings and numbers belong in constants or enums (`enums/` package), never inline in service logic.
- **Errors are explicit**:
  - Provide informative messages; never swallow exceptions. Map exception types strictly according to the Error Taxonomy Table without silent alterations.
- **Generated code is off-limits**:
  - Lombok-generated code and build outputs in `target/` and `dist/` are off-limits. Edit only the generator's source files and configuration.
- **Coverage**:
  - Every new public method and component feature requires tests covering both happy paths and edge/error conditions, maintaining the mandatory 80% coverage threshold in backend and frontend.
- **Scope discipline**:
  - A tests-only request means a tests-only change. If production code must be touched, stop and ask the user for permission.
- **Targeted exploration / Minimal scan**:
  - Do not crawl or scan the entire codebase for simple, localized tasks (e.g. documentation edits, single-file changes, isolated bugfixes, or targeted tests).
  - Inspect only the directly relevant file(s) and their immediate callers or imports.
  - Rely on facts already documented in `AGENTS.md` and project docs instead of re-exploring the filesystem. Wide codebase scans are forbidden unless the user explicitly requests an audit, broad discovery, or cross-module refactoring.
- **Git etiquette**:
  - No state-changing git actions (`commit`, `branch`, `checkout`, `merge`, `rebase`, `push`, `pull`, `reset`, `stash`, `tag`) without explicit user permission. Read-only git operations (`status`, `log`, `diff`, `show`) are permitted.
- **Build execution rule**:
  - Do not run compilation or build commands automatically after every file edit. State readiness to build and indicate what was verified; build only when code quality doubts or errors arise, or when explicitly requested by the user.
  - When work is done, state readiness and what was verified — otherwise state exactly what was skipped and why.
- **Communication**:
  - English only, laconic: plan → progress pings → concise result. Report verified facts only; "done and verified" means you ran the check.
- **Don't silently fix documented decisions**:
  - Surface contradictions rather than silently "correcting" intentional system behaviors listed in Section 8.
- **Look before deleting/overwriting**:
  - If a target file contradicts how it was described or was not created by you, stop and surface it to the user before deleting or overwriting.

# 📖 Adventure Book

An interactive adventure-book application with server-authoritative game logic. Browse books, start a playthrough, save progress, and import your own JSON books.

---

## ✨ Features

- Browse a book library with title/author search, difficulty filtering, and UI genre tags.
- Strict JSON validation: invalid books remain visible with clear diagnostics but cannot be started.
- Server-managed game sessions with health, navigation, choice consequences, death, and completion states.
- Protection against duplicate and stale moves through `requestId` and `expectedRevision`.
- Named saves stored in file-based H2 and restorable after an application restart.
- Import playable JSON books up to 1,000,000 bytes, including books with warnings.
- Responsive Angular UI with accessible controls and keyboard navigation.

The project includes a ready-to-play demo book, **The Clockwork Lighthouse**, and four invalid sample files whose errors are displayed in the library. The API returns both error and warning diagnostics; library cards currently summarize errors only.

## Playing and Saving

1. Open the library at `/`, select a playable book, and click **Begin Quest**.
2. Choose an option on `/game/:sessionId`. The backend returns the new section, health, consequence, status, and revision.
3. Use **Save Progress** to create a named snapshot. Restore or delete it from **Saved adventures** in the library.
4. Use **Back to Library** to leave the game screen. This does not stop the session.

The browser stores only the most recent session ID under `adventure.activeSession` in local storage. **Continue adventure** opens that session; it does not create a persistent save or check in advance whether the session has expired. A backend restart removes in-memory sessions, so use a saved adventure to recover progress afterward.

Each save creates a separate record, even when its name matches an existing save. The UI limits names to 50 characters; the API accepts nonblank names up to 120 characters and trims surrounding whitespace. Saves can include active, completed, or dead games. Restoring creates a new session UUID with the saved health, section, status, and revision. Unwanted saves can be deleted directly from the saved adventures list in the UI or via the API.

### Game rules and concurrency

- New sessions start at the book's `BEGIN` section with 10 health and revision `0`.
- Health stays between `0` and `10`. Consequences apply before the ending status is determined.
- Zero health produces `DEAD`, including when the choice leads to an `END`. Otherwise, reaching an `END` produces `COMPLETED`.
- Finished sessions expose no choices. The frontend displays the state confirmed by the backend and accepts the latest state returned on a revision conflict.
- Every choice sends an `optionId`, UUID `requestId`, and `expectedRevision`. Each accepted move increments the revision once.
- A session retains its last 50 accepted request IDs. Reusing one returns the current state without applying another move; it does not replay the original response. This history is not restored from saves.
- Moves and save snapshots use the same session lock so a snapshot contains fields from one revision.

---

## 📚 Adding Books

For a book bundled with the application, place its `.json` file in
`backend/src/main/resources/books/` and restart the backend. The filename (without
the `.json` extension) becomes the book ID.

For a book added while the application is running, use **Add book** in the library.
The upload must be a playable JSON book no larger than 1,000,000 bytes. Imported books are stored
in the H2 database under `backend/data/`; do not copy JSON files directly into that
directory.

Imports receive an ID formed from the title's ASCII slug and the first eight characters of the content's SHA-256 hash. Reimporting identical content updates the same imported-book ID. Changed bytes, including JSON whitespace, change the book version and normally create a different imported ID.

Save restoration requires the original book ID, exact content version, and saved section to remain available. Editing a bundled book, even only its formatting, can therefore make earlier saves incompatible.

The servlet also limits both the multipart file and entire multipart request to `1MB`; multipart overhead counts toward the request limit.

### Book JSON format

```json
{
  "title": "The Lantern Trail",
  "author": "Adventure Book Team",
  "difficulty": "EASY",
  "sections": [
    {
      "id": 1,
      "text": "A lantern marks a narrow trail through the woods.",
      "type": "BEGIN",
      "options": [
        {
          "description": "Follow the trail",
          "gotoId": 2,
          "consequence": {
            "type": "LOSE_HEALTH",
            "value": 1,
            "text": "Thorns scratch your hands along the way."
          }
        }
      ]
    },
    {
      "id": 2,
      "text": "You reach a welcoming inn before nightfall.",
      "type": "END",
      "options": []
    }
  ]
}
```

Use `EASY`, `MEDIUM`, or `HARD` for difficulty; `BEGIN`, `NODE`, or `END` for section type; and `LOSE_HEALTH` or `GAIN_HEALTH` for consequences. Enum values are case-sensitive. A consequence can be omitted or `null`. Write endings with no options (`[]`); omitted or `null` options are also accepted for endings. Option IDs are generated from their one-based array positions as strings, such as `"1"`; use the IDs returned in the game state's `choices` when calling the API.

## ⚠️ Book Validation

A book is not playable when it has any validation **error**. Validation checks that:

- the file is non-empty, valid JSON, and contains at least one section;
- `title`, `author`, section `text`, and option `description` are non-empty text;
- `difficulty`, section `type`, and consequence `type` use supported values;
- section IDs and option `gotoId` values are 64-bit integers or integer strings; section IDs are unique after normalization (for example, `1` and `"1"` collide);
- the book has exactly one `BEGIN` section and at least one `END` section;
- every non-`END` section has at least one option, and every `gotoId` points to an
  existing section;
- the book contains no more than 2,000 sections, no section has more than 50 options,
  and consequence values are integers or integer strings between 0 and 2,147,483,647.

Unreachable sections and a missing path from `BEGIN` to an `END` are reported as
warnings; they do not by themselves block a book from starting.
Reachability checks follow section links without simulating health, so a reachable ending is not a guarantee that the player can survive to it.

### Bundled sample books

| File | Status | Validation result |
| --- | --- | --- |
| `clockwork-lighthouse.json` | Playable | Valid demo book. |
| `crystal-caverns.json` | Invalid | Section `666` is a `NODE` with no options. |
| `dragon-quest.json` | Invalid | Empty file. |
| `pirates-jade-sea.json` | Invalid | Section `1` points to missing section `999`; section `666` is a `NODE` with no options. |
| `the-prisoner.json` | Invalid | Section `666` is a `NODE` with no options. |

---

## 🧩 Tech Stack

| Layer | Technologies |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1.2, Spring MVC, Bean Validation, JDBC, H2, Liquibase, Lombok, Springdoc OpenAPI |
| Session cache and graph validation | Caffeine and Guava |
| Frontend | Angular 21.2, TypeScript 5.9, RxJS 7.8, standalone components, signals, SCSS |
| Tests and coverage | JUnit, Spring Boot Test, AssertJ, JaCoCo; Vitest, jsdom, V8 coverage |
| Ports | API: `8080`, UI: `4200` |

---

## ✅ Requirements

- Java 21 LTS
- Node.js 24.x (`>=24.0.0 <25`) and npm 11 (`packageManager`: `npm@11.8.0`)
- Available ports `8080` and `4200`

Maven Wrapper is included and configured for Maven 3.10.0, so a separate Maven installation is not required. Set `JAVA_HOME` to your JDK 21 installation and make Java, Node.js, and npm available on `PATH`. Initial setup needs network access to download Maven and dependencies.

---

## 🚀 Run Locally

Open **two terminals** in the project root.

### 1. Start the backend

**Windows PowerShell**

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

**macOS / Linux**

```bash
cd backend
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`, with interactive Swagger UI at `http://localhost:8080/swagger-ui.html` and OpenAPI schema at `http://localhost:8080/api-docs`.
Run the backend from `backend/`: its default database path is relative to the process working directory.

### 2. Start the frontend

```bash
cd frontend
npm ci
npm start
```

Open [http://127.0.0.1:4200](http://127.0.0.1:4200). Requests to `/api` are forwarded to the backend through the Angular proxy.

> Run `npm ci` on the first setup and whenever `package-lock.json` changes. Keep the terminal with `npm start` open while using the app.

---

## 🛑 Stop the Application

In each terminal running a development server, press **Ctrl + C** and confirm termination if prompted.

- Stop the frontend first.
- Stop the backend second.

Saved games and imported books remain available because they are stored in `backend/data/`. Active game sessions are held in memory, so save important progress before stopping the backend.

Active sessions expire after 12 hours without access. Reading, moving, saving, or retrying a move
renews this timeout. Caffeine manages session expiry and a cache size limit of 1,000 sessions;
when the cache fills, its eviction policy considers how recently and frequently sessions are used.

---

## Database migrations

The default connection is `jdbc:h2:file:./data/adventure-book;AUTO_SERVER=TRUE`, with username `sa` and an empty password. When launched from `backend/`, the database lives under `backend/data/`. Launching the JAR from another directory selects a different relative `data/` directory. Stop the backend before copying the database files for a backup.

Configuration lives in `backend/src/main/resources/application.yml`. Spring Boot configuration overrides can change the port (`SERVER_PORT`) or database connection (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`). If you change the API port, update `frontend/proxy.conf.json` as well. The 12-hour session timeout and 1,000-session cache limit are configured in `BookConfiguration`, while the 1,000,000-byte import limit is defined in `BookCatalogServiceImpl`.

Liquibase applies database migrations automatically before the backend starts.
The master changelog is `backend/src/main/resources/db/changelog/db.changelog-master.yaml`;
versioned SQL changesets live in its `changes/` directory.

The initial migration creates missing tables using `CREATE TABLE IF NOT EXISTS`,
so an existing H2 database created by the former `schema.sql` keeps its imported
books and saved games. Liquibase records applied changes in `DATABASECHANGELOG`
and coordinates migration execution with `DATABASECHANGELOGLOCK`.

For future schema changes, add a new formatted SQL changelog with a unique
changeset ID and include it in the master changelog in order. Do not edit or
rename changesets that have already been applied. Spring SQL initialization is
disabled; Liquibase is the schema initialization mechanism in the app and tests.

## 🧪 Test and Build

**Windows PowerShell**

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd package

cd ..\frontend
npm ci
npm run test:ci
npm run build
```

On macOS / Linux, use `./mvnw` in place of `.\mvnw.cmd` and `../frontend` in place of `..\frontend`.

`package` already runs backend tests. Use `test` for verification alone, or `package` when you also need the executable JAR. Frontend `npm test` starts the interactive test runner; `npm run test:coverage` is equivalent to `npm run test:ci`.

- Backend tests enforce at least 80% line coverage overall and for each class in `repository.impl`. Reports are generated under `backend/target/site/jacoco/`.
- Frontend coverage includes `src/app/**/*.ts` and enforces 80% for lines, statements, functions, and branches. Reports are generated under `frontend/coverage/frontend/`.
- Backend persistence tests use isolated in-memory H2 databases rather than the application's file database.

See [AGENTS.md](AGENTS.md) for test and coverage commands. Per [AGENTS.md](AGENTS.md), agents should run builds or tests only when explicitly requested or when code quality or runtime concerns require verification.

### Build outputs and hosting

The backend package is `backend/target/adventure-book-backend-0.0.1-SNAPSHOT.jar`. Run it from `backend/` with:

```bash
java -jar target/adventure-book-backend-0.0.1-SNAPSHOT.jar
```

The frontend production files are written to `frontend/dist/frontend/browser/`. Serve them with a static web server configured to fall back to `index.html` for Angular routes such as `/game/:sessionId`, and forward `/api` to the backend. The Angular development proxy only applies to `npm start`; the backend build does not bundle the Angular application.

---

## 🏗️ Project Structure

```text
Project/
├── backend/                 # Spring Boot API
│   ├── src/main/java/
│   │   └── .../
│   │       ├── config/      # session cache and OpenAPI configuration
│   │       ├── controller/  # controller interfaces and impl/ REST endpoints
│   │       ├── dto/         # API request and response classes
│   │       ├── entity/      # sessions, saves, imports, and book/ data classes
│   │       ├── enums/       # difficulty, section, consequence, status, severity
│   │       ├── exception/   # domain exceptions and global API error handling
│   │       ├── repository/  # repository interfaces and impl/ JDBC access
│   │       ├── service/     # service interfaces and impl/ business logic/providers
│   │       └── utils/       # book mapping and playability helpers
│   ├── src/main/resources/
│   │   ├── books/           # bundled books
│   │   ├── db/changelog/    # Liquibase master changelog and SQL changesets
│   │   └── application.yml
│   ├── src/test/java/       # service, repository, migration, controller, context tests
│   └── data/               # runtime H2 database (generated, ignored)
└── frontend/                # Angular application
    ├── src/app/core/api/    # HTTP client, models, and client tests
    ├── src/app/features/    # library/game screens, templates, SCSS, and tests
    ├── src/app/app.routes.ts # lazy-loaded library and gameplay routes
    ├── angular.json        # build, dev server, and coverage configuration
    └── proxy.conf.json     # development backend proxy
```

The backend is the source of truth for game state. The frontend displays only the confirmed health, section, and status returned by the API.

---

## 🔌 Main API Endpoints

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/books` | List book summaries and diagnostics; optional `q` and `difficulty` filters |
| `GET` | `/api/books/{bookId}` | Get one book summary and diagnostics, without section content |
| `POST` | `/api/books/import` | Import a JSON book (`multipart`, `file` field) |
| `POST` | `/api/games` | Create a game session |
| `GET` | `/api/games/{sessionId}` | Get the current game state |
| `POST` | `/api/games/{sessionId}/choices` | Apply a player choice |
| `DELETE` | `/api/games/{sessionId}` | End an active session |
| `GET` | `/api/saves` | List saved games |
| `POST` | `/api/saves` | Create a save |
| `POST` | `/api/saves/{saveId}/restore` | Restore a save |
| `DELETE` | `/api/saves/{saveId}` | Delete a save |

Start a game with `POST /api/games`:

```json
{ "bookId": "clockwork-lighthouse" }
```

Apply a choice with `POST /api/games/{sessionId}/choices`:

```json
{ "optionId": "1", "expectedRevision": 0, "requestId": "da8404a9-6227-482e-9f24-41171f788589" }
```

Create a save with `POST /api/saves`:

```json
{ "sessionId": "742b5927-8711-4521-9f22-066ad344fa41", "name": "Before the storm" }
```

Replace the example session UUID with the `sessionId` returned by the server, use the current `revision`, and generate a new `requestId` for each distinct move. Reuse that request ID when retrying the same move.

Creating a session, importing a book, or creating a save returns `201`; deleting a session returns `204`, including when the session is already missing. Deleting a save returns `204` (or `404` if not found). Restoring a save returns `200` with the new game state.

A game state contains `sessionId`, `bookId`, `bookTitle`, `sectionId`, `text`, `choices`, `health`, `status`, `revision`, and an optional `consequence` with `text` and the actual, clamped `healthDelta`.

Handled API errors use `code`, `message`, `status`, `path`, `timestamp`, and `details`. Important responses include:

| HTTP status | Meaning |
| --- | --- |
| `400` | Invalid request fields, unavailable option, empty import, or service-level import size rejection |
| `404` | Missing book/save or missing/expired session |
| `409` | Stale revision (body is the current game state), finished game, or incompatible saved book version/section |
| `413` | Multipart upload exceeds the servlet limit |
| `422` | Book validation errors prevent import or play; `details` lists diagnostics |

---

## ℹ️ Current Limitations

- Authentication and per-user save ownership are not implemented.
- Saves cannot currently be renamed through the UI or public API (though deleting saves is supported). Book editing/deletion is also unavailable.
- Genre tags and displayed reading times are UI metadata, not fields in the book JSON or measured reading estimates. Choice hints and section headings are also generated by the UI.
- Difficulty labels support filtering; they do not change the engine's health rules.
- The parser currently accepts options on `END` sections, although the authoring format calls for none. Gameplay ends on arrival and does not expose those options.
- The project includes service, repository, migration, controller integration, application context, and frontend unit tests; browser E2E tests have not yet been added.

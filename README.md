# Bank App

A banking application built for **JUMP by Cognixia**, developed in phases. Each phase adds one competency to the same domain — customers, accounts, deposits, withdrawals — so the application grows from a single-file console program into a full-stack, cloud-deployed service.

The repository deliberately keeps every phase's code rather than replacing it, so the progression is visible.

## Status

| Phase | Scope | State |
|---|---|---|
| 1 | Console app — OOD, collections, SOLID | Complete |
| 2 | REST API — Spring Boot, layered architecture, MongoDB Atlas | Complete |
| 3 | TDD — JUnit + Mockito at all three layers | Complete — 27 tests, no network |
| 4 | API testing — Postman collection | Complete — 18 requests, 27 assertions |
| 5 | API docs — Swagger / OpenAPI | Complete |
| 8 | React front end — Vite, react-router | Complete — full CRUD against the API |
| 9 | DevOps — CI/CD, Docker, Kubernetes | Next |
| 10–14 | Security, cloud, IaC, observability, microservices | Planned |

## Prerequisites

- **JDK 25** (built against Eclipse Temurin 25.0.4). Phase 1 needs nothing else.
- **MongoDB Atlas account** (the free M0 tier is sufficient) for Phase 2.
- **Node.js 20+** (built against 24.17) for Phase 8.
- Maven is *not* required — the Spring project ships the `mvnw` wrapper.

## Repository layout

```
Banking_App/
├── src/bank/                 Phase 1 — console app, plain javac, no build tool
├── bankapi/                  Phase 2 — Spring Boot REST API
│   ├── src/main/java/com/bank/
│   │   ├── model/            Customer — plain data, JSON and BSON mapping
│   │   ├── repository/       storage access, the only package that knows about MongoDB
│   │   ├── service/          business rules
│   │   └── controller/       HTTP translation only
│   ├── postman/              importable API collection with assertions
│   └── mvnw / mvnw.cmd       Maven wrapper
└── bankui/                   Phase 8 — React front end
    ├── src/services/         api.js — the only code that knows the API is HTTP
    ├── src/components/       pieces — Navbar, CustomerList, CustomerRow, AddCustomerForm
    ├── src/pages/            route destinations — Home, Customers, EditCustomer,
    │                         About, Contact, Login, NotFound
    └── vite.config.js        dev server + /api proxy
```

The three applications are independent — `javac`, Maven and npm — and no build interferes with another.

---

## Phase 1 — console app

Compiles with `javac` directly. There is no build file by design, since Phase 1 predates the introduction of a build tool.

```bash
javac -d out src/bank/*.java
java -cp out bank.Main
```

Runs once, top to bottom: log in against hardcoded seed data, then either the admin or the customer flow depending on the role. It does not loop back to a menu — the whole flow is meant to be visible in a single pass.

### Design notes

`Account` implements `AccountOperations` but stays abstract and never declares `withdraw()`, so the obligation falls to the first concrete subclass. `deposit()` *is* concrete on `Account`, because money in behaves identically everywhere — only `withdraw()` varies, and that difference (savings refuses overdraft, checking permits it down to a limit) is the polymorphism the phase exists to demonstrate.

Both methods return `boolean` meaning *"did the rules allow it"*, not *"did it work"*. A refusal is an in-contract outcome, which is what keeps `SavingsAccount` substitutable for `CheckingAccount` despite being stricter.

---

## Phase 2 — REST API

Spring Boot 4.1.1 on Java 25, backed by MongoDB Atlas.

### 1. Configure the database connection

The connection string is **never committed**. It is read from an environment variable at startup:

```properties
spring.mongodb.uri=${MONGODB_URI}
spring.mongodb.database=bankdb
```

Create an Atlas M0 cluster, add a database user, allow your IP under **Network Access**, then copy the driver connection string (**Connect → Drivers → Java 5.2+**) and set it once:

```powershell
[Environment]::SetEnvironmentVariable("MONGODB_URI", "mongodb+srv://USER:PASSWORD@your-cluster.mongodb.net/?appName=Cluster0", "User")
```

Restart your terminal or IDE afterwards so the new variable is visible.

Notes:
- Leave the path between `/` and `?` empty so it doesn't override `spring.mongodb.database`.
- URL-encode special characters in the password (`@` → `%40`, `#` → `%23`, `/` → `%2F`).
- If `MONGODB_URI` is unset, the application **fails at startup** with `Could not resolve placeholder 'MONGODB_URI'`. That is deliberate — a server that starts without a database and only fails on the first request is harder to diagnose than one that refuses to start.

> **Boot 4 note:** the older `spring.data.mongodb.uri` key is deprecated at `level=error` and is *ignored silently*. The client then falls back to `mongodb://localhost/test` and fails with a connection refused that looks like a network problem. Most tutorials online are Boot 3 and still show the old key.

### 2. Run

```bash
cd bankapi
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

On first start against an empty collection, three customers are seeded. Seeding is skipped when the collection already contains documents, so deleted records stay deleted across restarts.

### 3. Endpoints

Base URL: `http://localhost:8080/api/v1`

| Method | Path | Success | Failure |
|---|---|---|---|
| `GET` | `/customers` | `200` + array | — |
| `GET` | `/customers/{id}` | `200` + customer | `404` unknown id · `400` non-numeric id |
| `POST` | `/customers` | `201` + `Location` header | `409` id already taken · `400` invalid body |
| `PUT` | `/customers/{id}` | `200` + updated customer | `404` unknown id · `400` invalid body or id mismatch |
| `DELETE` | `/customers/{id}` | `204` no content | `404` unknown id |

`PUT` is a **full replacement**, and the body's `id` must match the path's. A mismatch is a `400` rather than a silent preference for one side — either half could be the typo, and guessing wrong overwrites the wrong record.

Ids are assigned by hand, not generated. `409` rather than `400` on a duplicate is deliberate: the failure is state-dependent, since the same request would have succeeded before that id existed and will succeed again after it is deleted.

Validation errors return the status and path but **do not name the offending field**, to avoid handing out free reconnaissance. `server.error.include-binding-errors` is left at its default of `never`.

### 4. API documentation

With the app running:

- **Interactive UI** — <http://localhost:8080/swagger-ui.html>. Fire requests at the API from the browser, with the request and response shapes filled in.
- **Raw OpenAPI 3.1 spec** — <http://localhost:8080/v3/api-docs>.

Every handler declares its real status codes via `@ApiResponses`. That is load-bearing rather than decorative: springdoc infers the response *shape* from a method's return type but cannot infer its *status*, so an unannotated `ResponseEntity<Void>` publishes `200` for a method that returns `204`. Left undocumented, the spec doesn't go blank — it goes wrong.

The Bean Validation constraints on `Customer` appear in the generated schema automatically (`@NotNull` → `required`, `@NotBlank` → `minLength: 1`), so the constraints the server enforces are also the constraints the spec advertises.

> `springdoc-openapi` has **no Spring Boot 4 release**. Version 2.8.6 targets Boot 3 and was verified working against 4.1.1 by hand. Its version is pinned explicitly in `pom.xml` because Boot's BOM does not manage third-party artifacts. One known gap: `@Positive` is not reflected in the schema.

---

## Phase 8 — front end

React 19.2 on Vite 8.2, plain JavaScript. It is a **separate application** from `bankapi`, with its own build and its own port — not bundled into Spring's `static/`.

That is a deployment decision, not a stylistic one. Phase 9's pipeline targets independently deployable units, and bundling would mean a CSS change requires recompiling Java. The cost is that the two are genuinely different origins in production, so the API must send CORS headers for real. See the note at the end of this section.

### 1. Run

The API must already be running (see Phase 2). Then, in a second terminal:

```bash
cd bankui
npm install       # first time only
npm run dev
```

<http://localhost:5173> — create, edit and delete customers against the live database.

### 2. What it does

| Route | View |
|---|---|
| `/` | landing page — what the app does today, and what is coming |
| `/customers` | customer table, plus the create form |
| `/customers/{id}/edit` | edit one customer's username and full name |
| `/about` | static placeholder |
| `/contact` | static placeholder |
| `/login` | placeholder — collects credentials and authenticates nobody |
| anything else | the client-side 404 |

Every endpoint in the API table above is reachable from the UI. The id field is editable when **creating** — ids are assigned by hand in this API — and is deliberately not editable when **editing**, because the path is the record's identity. Changing an id is not an edit; it is a delete plus a create, which the API already exposes separately.

`/login` renders a real form and submits nowhere. That is deliberate rather than unfinished: the tempting placeholder is to accept any credentials and set an `isLoggedIn` flag, which is not a weak login but *not a login at all* — the API would still serve every endpoint to anyone who asks, and the check would be a variable a user flips in DevTools. Authentication is a server decision, and it arrives in Phase 10.

### 3. How it is structured

Three directories, each answering a different question:

| Directory | Holds | Test for belonging |
|---|---|---|
| `services/` | every HTTP call | does it know the API is reached over the network? |
| `pages/` | route destinations | does a `<Route>` render it? |
| `components/` | the pieces pages are built from | could you render it with made-up props? |

**`services/api.js` is the front end's repository layer.** It is the only file that knows the API lives at `/api/v1` over HTTP; components ask for `getCustomers()` and never see a `fetch`. The base path is written once, and the rules that are easy to forget at a call site — that `fetch` does not reject on a 500, that Spring answers `415` without a `Content-Type` header — are enforced in one place.

It normalises *transport* but deliberately not *meaning*. Failures are thrown as an `ApiError` carrying `.status`, because `409` and `400` say different things to a user and only the caller knows which matter on its screen. Same division as the API's own service returning a `boolean` and letting the controller pick the status code.

**Container and presentational components.** `CustomersPage` owns the state, calls the API, and decides what a failure means. `CustomerList` takes an array and draws a table — no state, no fetching, no idea where the data came from. Splitting them means the table is reusable anywhere, and everything with a *decision* in it lives in one file.

### 4. The dev proxy, and why it is temporary

`vite.config.js` forwards `/api` to `localhost:8080`:

```js
server: { proxy: { '/api': 'http://localhost:8080' } }
```

The browser therefore only ever talks to `:5173`, and Vite relays to Spring server-side where the same-origin policy does not apply. This is a **development convenience, not the deployed topology**. Once the two are served separately the requests are genuinely cross-origin and the API has to allow them explicitly — configured in Phase 9, along with the other half of the same debt: `BrowserRouter` uses the History API, so a deep link to `/customers/2/edit` is a real `GET` for that path and the server must answer `index.html` rather than 404. Vite does both for free in development, which is exactly why they are easy to forget.

### 5. Build

```bash
npm run build     # → bankui/dist
npm run lint
```

---

## Testing

Import `bankapi/postman/bankapi.postman_collection.json` into Postman and use **Run collection** — 18 requests, 27 assertions, covering the happy path plus every error status above.

The same file runs headless, which is how it will be wired into CI:

```bash
npx newman run bankapi/postman/bankapi.postman_collection.json
```

The happy-path folder creates and then deletes a customer, so the collection is repeatable against a persistent database.

## Architecture

```
bankui (React)  →  controller → service → repository → MongoDB Atlas
   :5173             :8080                     ↑
   ────────┬────────                the only layer that knows
    HTTP, JSON, and                 which database is behind it
    nothing else
```

Each layer talks only to the one below it. The controller never touches the repository, and the repository never formats a response.

The front end sits outside that chain entirely. Everything it knows about the server is five URLs and their status codes — the contract published at `/v3/api-docs`. It has no build-time dependency on the API and no shared code with it, which is what makes them separately deployable in Phase 9.

Those five URLs live in exactly one file, `bankui/src/services/api.js`. That is the front-end mirror of the same rule the repository package follows on the server: one layer knows how the thing behind it is reached, and everything above it is written in the application's own vocabulary. It is also what makes the contract auditable — the complete set of requests this UI can make is a nine-line block at the bottom of that file, so a gap like the missing filter/search shows up as an absence you can see.

`CustomerRepository` **wraps** a Spring Data `MongoRepository` rather than being replaced by one. That costs an extra class of mostly delegation, and buys two things: the service keeps speaking the application's vocabulary instead of Spring Data's, and `MongoRepository`'s two dozen other methods — `deleteAll()` among them — stay out of reach of business logic.

The payoff is measurable. Storage moved from an in-memory `LinkedHashMap` to a replica set in Atlas, and `CustomerService` and `CustomerController` did not change by a single line — verifiable with `git diff`.

Responsibilities are split so that each check lives in the only layer that can make it:

- **Controller** — path id versus body id. The only layer that sees both, and the only one that can answer `400`.
- **Service** — business rules, such as id uniqueness. Reports facts (`boolean`, `Optional`), never status codes, so the same service could back a CLI or a message consumer.
- **Repository** — existence. Reports absence as an empty `Optional`; the controller decides that this means `404`.
- **MongoDB** — the unique index on `_id`. Makes duplicates impossible regardless of what any layer above does.

## Branching model

Feature branches over a chain of long-lived stage branches, each cut from the tip of the previous one:

```
api-backend  ──►  database  ──►  frontend  ──►  deployed
   (ph 2)        (ph 4.5, 3–5)      (ph 8)     (ph 9, 11–12)
```

`api-backend` ends where the API works against a hardcoded in-memory repository — that stub is what proves the layering. `database` is where storage becomes real (MongoDB Atlas) and gets proven, which is why the testing and documentation phases live there rather than with the API. `frontend` adds `bankui/` without changing a line of Java.

`main` tracks the newest **completed** stage, so it always runs. Active work never happens on it directly. Fixes merge **forward only** — a change made on an earlier stage branch is merged into the later one, never cherry-picked backward.

## Roadmap

| # | Phase | Focus |
|---|---|---|
| 0 | Discovery / BA | Vision, BRD, wireframes, UML, architecture diagrams |
| 1 | Console app | Programming logic, collections, OOD, SOLID |
| 2 | REST API | CRUD, MVC, controller → service → repository |
| 3 | TDD | JUnit, Mockito, tests at all three layers |
| 4 | API testing | Postman collections |
| 5 | API docs | Swagger / OpenAPI |
| ~~6~~ | ~~Databases~~ | Cut — Postgres/SQL dropped for time. MongoDB Atlas is the only store. |
| ~~7~~ | ~~ORM~~ | Cut with Phase 6 — JPA maps relational tables, of which there are none. |
| 8 | Frontend | React — components, props, routing, hooks |
| 9 | DevOps | CI/CD, GitHub Actions, Docker, Kubernetes |
| 10 | Security | Hashing, salting, BCrypt, AuthN/AuthZ, JWT |
| 11 | Cloud | AWS concepts and deployment |
| 12 | IaC | Terraform |
| 13 | Optimization | Latency, profiling, application monitoring |
| 14 | Distributed design | Microservices, Apache Kafka |

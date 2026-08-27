# Bank App

A banking application built for **JUMP by Cognixia**, developed in phases. Each phase adds one competency to the same domain — customers, accounts, deposits, withdrawals — so the application grows from a single-file console program into a full-stack, cloud-deployed service.

The repository deliberately keeps every phase's code rather than replacing it, so the progression is visible.

## Status

| Phase | Scope | State |
|---|---|---|
| 1 | Console app — OOD, collections, SOLID | Complete |
| 2 | REST API — Spring Boot, layered architecture, MongoDB Atlas | Complete |
| 3 | TDD — JUnit + Mockito at all three layers | In progress |
| 4–5 | Postman collection, Swagger / OpenAPI | Planned |
| 8 | React front end | Planned |
| 9–14 | CI/CD, security, cloud, IaC, observability, microservices | Planned |

## Prerequisites

- **JDK 25** (built against Eclipse Temurin 25.0.4). Phase 1 needs nothing else.
- **MongoDB Atlas account** (the free M0 tier is sufficient) for Phase 2.
- Maven is *not* required — the Spring project ships the `mvnw` wrapper.

## Repository layout

```
Banking_App/
├── src/bank/                 Phase 1 — console app, plain javac, no build tool
└── bankapi/                  Phase 2 — Spring Boot REST API
    ├── src/main/java/com/bank/
    │   ├── model/            Customer — plain data, JSON and BSON mapping
    │   ├── repository/       storage access, the only package that knows about MongoDB
    │   ├── service/          business rules
    │   └── controller/       HTTP translation only
    ├── postman/              importable API collection with assertions
    └── mvnw / mvnw.cmd       Maven wrapper
```

The two applications are independent, and neither build interferes with the other.

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

## Testing

Import `bankapi/postman/bankapi.postman_collection.json` into Postman and use **Run collection** — 18 requests, 27 assertions, covering the happy path plus every error status above.

The same file runs headless, which is how it will be wired into CI:

```bash
npx newman run bankapi/postman/bankapi.postman_collection.json
```

The happy-path folder creates and then deletes a customer, so the collection is repeatable against a persistent database.

## Architecture

```
client → controller → service → repository → MongoDB Atlas
                                     ↑
                          the only layer that knows
                          which database is behind it
```

Each layer talks only to the one below it. The controller never touches the repository, and the repository never formats a response.

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

`api-backend` ends where the API works against a hardcoded in-memory repository — that stub is what proves the layering. `database` is where storage becomes real (MongoDB Atlas) and gets proven, which is why the testing and documentation phases live there rather than with the API.

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

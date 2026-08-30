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
| 9 | DevOps — CORS, GitHub Actions CI, Docker, Compose | Complete — Kubernetes deferred |
| 10 | Security — hashing, BCrypt, AuthN/AuthZ, JWT | **Not started.** Read [Security](#security--what-is-and-is-not-enforced) before drawing any conclusion about this application's protections |
| 11 | Cloud — AWS deployment | In progress — front end on S3 + CloudFront, API on EC2, **the two are not yet connected** |
| 12 | IaC — Terraform | In progress alongside Phase 11 — the infrastructure was written as Terraform from the start rather than clicked together and codified afterwards |
| 13–14 | Observability, microservices | Planned |

Alongside those phases, and belonging to none of them, the domain caught up with the architecture: a full **`Account` vertical** (model → repository → service → controller, with deposit and withdraw), **customer roles**, a **sign-in endpoint**, and a front end rebuilt around **admin and customer dashboards**. That work is on the `app_branch` branch and is **not yet merged into `main`**.

> **Deployment status, stated plainly because a live URL invites the wrong assumption.** The deployed front end is a **build made before** the sign-in and dashboard work, and the deployed distribution **cannot currently reach its API**. The live site is therefore not a demonstration of this repository's current state, and nothing about the application should be judged from it until both are refreshed. See [Phase 11](#phase-11--cloud-in-progress).

## Security — what is and is not enforced

**This application has no authorisation. Any caller can read or drain any account.** The money-moving endpoints take an account id in the path and nothing else — no credential, no token, no session — so `POST /api/v1/accounts/{id}/withdraw?amount=…` succeeds for anyone who can reach the API. `GET /api/v1/accounts` returns every account in the system. The same is true of every customer endpoint: any caller can list, edit or delete any customer.

This is **a known and deliberate scope decision, not an oversight.** It is a training exercise, authentication and authorisation are Phase 10 of the roadmap below, and shipping the gap stated is the trade being made. It is recorded here because the alternative — a banking demo that says nothing about it — would leave a reader to assume protections that do not exist.

**Why it is not partially fixed.** Enforcing *"this is your account"* requires knowing who is asking, and nothing in a request carries that. `POST /api/v1/customers/signin` compares a username and password and returns the customer, but it **issues no session and no token**, so the very next request is anonymous again. Adding a `customerId` to a path would only let a caller *name* someone else's account — it reads as a check while enforcing nothing, which is worse than the honest gap. The missing piece is one feature: an identity that survives a request.

Everything protective in this application falls into exactly one of three categories, and conflating them overstates the posture:

| | Holds against `curl`? | What it actually is |
|---|---|---|
| **Enforced** | **Yes** | Facts about system state, needing no identity: **roles cannot be set or changed through any endpoint**; **a balance is never client-supplied** — no request body in this API carries one, so an account opens at zero and only `deposit` and `withdraw` ever move it; **overdraft floors and whole-number amounts**; **username uniqueness on both create and update**; **a savings account cannot gain an overdraft**, coerced in the constructor; account create rejecting an unknown customer and a duplicate account id; the unique `_id` index. |
| **Product boundary** | **No** | **The React app only — the API is unchanged.** The sign-in gate, the redirect to `/login`, the admin-only customer list and edit page, dashboard routing. These shape what the front end *offers*. They are not access control, and `curl` never sees them. |
| **Not protected at all** | — | **Every account, every balance, every deposit and withdrawal, and every customer record.** |

**The middle row is the one that invites the mistake**: it looks like security in a browser and is worth nothing to any client that is not the browser. The front-end source says so at each gate rather than leaving it to be inferred.

**Two defects were found and closed during testing, and they are worth recording because they were *not* the scoped gap above.** Both were failures to validate a request rather than failures to identify a caller, so neither was blocked on Phase 10 and neither was covered by the decision to ship without authorisation. Using a real, decided, documented gap as cover for two ordinary bugs is the mistake that was available here, and the distinction is the reason it was not made:

- **Account routes accepted a client-supplied `balance`, on all three of them.** Both create routes and `PUT /accounts/{id}` bound the `Account` entity directly, which carries constraints on `id` and `type` and none on the money fields. An arbitrarily large opening balance could be created and then withdrawn — **the correct withdrawal validation bypassed by funding the account rather than by defeating the guard** — and a fractional balance admitted, which is the one thing the whole-number money rule exists to exclude. **Closed by narrow request records with no balance field.** A balance smuggled into the body now binds to nothing: the request returns `201` and stores zero. It is not rejected, it is **unrepresentable** — no constraint to get wrong, and no error path to keep correct.
- **`PUT /customers/{id}` did not check username uniqueness, though create did.** Renaming one customer onto another's username succeeded, and sign-in for that username then failed with a `500`, since the lookup returns one `Optional` and two documents matched. **Any caller could lock a real user out of sign-in permanently**, with no way for the victim to undo it. **Closed** — the rule is enforced on both writers, and the endpoint now answers `409`. A rule is only as enforced as its least-guarded writer.

Neither was caught by the test suite, and the reason is the same in both: every existing test sends a complete, well-formed body. **A pass-through's test passes precisely because it is a pass-through**, and a suite that exercises only the payloads the author had in mind cannot see a validation that was never written.

Two further consequences of Phase 10 being unreached, stated rather than left to discovery:

- **Passwords are stored in plaintext.** Deliberately: a fake hash now would *look* protected, which is worse than storing none while the phase that introduces BCrypt has not arrived. They are never returned by any endpoint.
- **No password can be changed.** A password is set at creation and never again — there is no route and no request field that carries one. That is a gap, not a guarantee, and it belongs with hashing rather than being fixed separately: a change-password route before hashing would exist only to move a plaintext secret around.

> **The part worth carrying to another project.** These endpoints were tested early and **passed** — the overdraft rules were right, the guards held, refusals came back as designed. Nobody asked *who was allowed to make the request*, because at that point nothing in the application had authentication, so anonymous access was not a gap in the design, it was the ambient condition. **A missing control is hardest to see when it is missing everywhere.** It only became visible once everything around it was locked down. That is the argument for asking *"who may do this?"* as a question separate from *"does this do the right thing?"* — the second was asked and answered well, and answering it well is what made the first easy to skip.

## Prerequisites

- **JDK 25** (built against Eclipse Temurin 25.0.4). Phase 1 needs nothing else.
- **MongoDB Atlas account** (the free M0 tier is sufficient) for Phase 2.
- **Node.js 20+** (built against 24.17) for Phase 8.
- **Docker Desktop** for Phase 9. Hardware virtualization must be enabled in the BIOS.
- Maven is *not* required — the Spring project ships the `mvnw` wrapper.

## Repository layout

```
Banking_App/
├── .github/workflows/ci.yml  Phase 9 — CI, one job per application
├── compose.yaml              Phase 9 — mongo + api + ui, one command
├── src/bank/                 Phase 1 — console app, plain javac, no build tool
├── bankapi/                  Phase 2 — Spring Boot REST API
│   ├── src/main/java/com/bank/
│   │   ├── model/            Customer, Account, Role, AccountType — plain data
│   │   ├── repository/       storage access, the only package that knows about MongoDB
│   │   ├── service/          business rules
│   │   ├── controller/       HTTP translation, plus the narrow per-operation
│   │   │                     request records the handlers bind
│   │   └── config/           WebConfig — CORS
│   ├── postman/              importable API collection with assertions
│   ├── Dockerfile            multi-stage, layered jar, non-root
│   └── mvnw / mvnw.cmd       Maven wrapper
├── bankui/                   Phase 8 — React front end
│   ├── src/services/         api.js — the only code that knows the API is HTTP
│   │                         viewer.js — the signed-in customer id, and nothing else
│   ├── src/components/       pieces — Navbar, dashboards, account and customer lists,
│   │                         forms, and the RequireSignIn / RequireAdmin route guards
│   ├── src/pages/            route destinations — Home, Login, Register, Dashboard,
│   │                         MyDashboard, EditCustomer, About, Contact, NotFound
│   ├── Dockerfile            multi-stage, node builds → nginx serves
│   ├── nginx.conf            SPA fallback, cache rules, /api reverse proxy
│   ├── deploy.sh             build → two-pass S3 upload → CloudFront invalidation
│   └── vite.config.js        dev server + /api proxy
└── terraform/                Phases 11–12 — S3, CloudFront, OAC, EC2 API instance
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

**Customers.**

| Method | Path | Success | Failure |
|---|---|---|---|
| `GET` | `/customers` | `200` + array — optional `?role=ADMIN\|CUSTOMER` filter | — |
| `GET` | `/customers/{id}` | `200` + customer | `404` unknown id · `400` non-numeric id |
| `POST` | `/customers` | `201` + `Location` header | `409` username taken · `400` invalid body |
| `POST` | `/customers/signin` | `200` + customer | `401` no such user *or* wrong password · `400` invalid body |
| `PUT` | `/customers/{id}` | `200` + updated customer | `404` unknown id · `409` username taken · `400` invalid body |
| `DELETE` | `/customers/{id}` | `204` no content | `404` unknown id |

**Accounts**, added later with the domain work rather than in Phase 2. Amounts are whole numbers, and account ids *are* chosen by the caller.

**No request body in this API carries a balance.** An account opens at zero and money arrives through `deposit`, which is the guarded path. That is deliberate and is the fix for a real defect — see [Security](#security--what-is-and-is-not-enforced). The overdraft floor *is* the caller's, because how far below zero an account may go is a genuine choice made when it is opened; it is constrained to zero or negative, since a positive limit is a floor *above* zero and would make the account refuse ordinary withdrawals. A savings account is coerced to a zero floor whatever is sent.

| Method | Path | Success | Failure |
|---|---|---|---|
| `GET` | `/accounts` | `200` + array | — |
| `GET` | `/accounts/{id}` | `200` + account | `404` unknown id |
| `POST` | `/accounts?customerId=` | `201` + `Location` header — body is `id`, `type`, optional `overdraftLimit` | `400` invalid body or missing `customerId` · `404` unknown customer · `409` account id taken |
| `PUT` | `/accounts/{id}` | `200` + updated account — body is `type`, optional `overdraftLimit` | `400` invalid body · `404` unknown id |
| `DELETE` | `/accounts/{id}` | `204` no content | `404` unknown id |
| `GET` | `/customers/{customerId}/accounts` | `200` + array | `404` unknown customer |
| `POST` | `/customers/{customerId}/accounts` | `201` + `Location` header — same body as `POST /accounts` | `400` invalid body · `404` unknown customer · `409` account id taken |
| `DELETE` | `/customers/{customerId}/accounts/{accountId}` | `204` no content | `404` unknown customer, or that customer does not own that account |
| `POST` | `/accounts/{id}/deposit?amount=` | `200` + the account as stored | `400` zero, negative or fractional amount · `404` unknown id · `409` the rules refused it |
| `POST` | `/accounts/{id}/withdraw?amount=` | `200` + the account as stored | `400` zero, negative or fractional amount · `404` unknown id · `409` would breach the account's floor |

**None of these endpoints requires a credential.** `signin` proves one and persists nothing — see [Security](#security--what-is-and-is-not-enforced) before reading the two tables as a description of a protected API.

**Customer ids are assigned by the server, not by the caller.** The create body carries username, password and full name **and nothing else** — there is no field for an id or a role, so neither can be chosen, smuggled in, or accidentally required. That is structural rather than a check: nothing strips a role, because a role cannot arrive. The `409` is therefore a **username** collision, which is the only thing two creates can now collide on, and `409` rather than `400` is deliberate: the failure is state-dependent, since the same request would have succeeded before that username existed and will succeed again after it is released.

`PUT /customers/{id}` is a **full replacement of the fields a client both reads and owns** — username and full name. It carries no id, so the path is the only identity and there is **no mismatch to check**; and it carries no role, so no route in this API can change one. That rule has four applications and no exceptions: `password` fails the *read* half (a client is never given it, so it cannot be required to send it back), while `id`, `role` and `accountIds` fail the *own* half.

> **Why the request bodies are narrow records rather than the entity.** Binding the shared `Customer` for every operation produced three bugs at once, because model-level validation applies to *every* route that binds the model and so cannot be correct for all of them: `@NotNull role` broke every create from the UI; `password` marked both required and write-only made editing impossible — two individually reasonable annotations that were jointly unsatisfiable. **All three passed the unit suite**, because every existing test sent a complete body and none sent only the fields a client actually owns. A suite that exercises only full payloads cannot see a constraint that is impossible to satisfy partially. `CreateCustomerRequest`, `UpdateCustomerRequest` and `SignInRequest` each state exactly what one operation accepts, so no constraint has to serve two callers.
>
> **`Account` was still bound directly in three places, and it produced exactly the predicted failure** — the money-creation defect in the security section above. It now binds `CreateAccountRequest` and `UpdateAccountRequest` on all three. The rule that decides what belongs in one of these records has four applications and no exceptions: **a full replacement covers the fields a client both reads and owns.** `password` fails the read half. `id`, `role`, `accountIds` and `balance` fail the own half.

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

| Route | Access | View |
|---|---|---|
| `/login` | public | sign-in form — posts to `/customers/signin` and, on a match, remembers the customer id |
| `/register` | public | create an account — username, password, full name; the server assigns the id and the role |
| `/` | signed in | landing page — what the app does today, and what is coming |
| `/dashboard` | signed in | redirects to `/dashboard/{your id}` |
| `/dashboard/{id}` | signed in | one customer's accounts, with deposit and withdraw |
| `/customers/{id}/edit` | admin | edit one customer's username and full name |
| `/about`, `/contact` | signed in | static placeholders |
| anything else | signed in | the client-side 404 |

A signed-out visitor reaching anything outside the two public routes is redirected to `/login`, and the nav shows only **Sign in** and **Create an account** — a link that cannot be followed is worse than no link, because the redirect that follows reads as a bug rather than as a rule.

**There is no `/customers` route, and its absence is the fix rather than a tidy-up.** It was a standalone page listing every customer, with a create form and a delete button on every row, sitting behind a gate that asked whether somebody was signed in and never *who* — so any registered customer could type the address and get the administrative surface. The list now lives inside the admin dashboard and has no address of its own. **The surest gate on a URL is that nothing answers it**; removing the nav link was a consequence of removing the route, never a substitute for it.

The **id is not editable anywhere.** On create, the server assigns it. On edit, the path is the record's identity — changing an id is not an edit, it is a delete plus a create, which the API already exposes separately.

**None of this is a security control**, and the source says so at each gate. The sign-in state is a customer id in `sessionStorage` — never the password, never the role, because caching a role would make the client the holder of its own claim about what it may see. The API answers every one of these endpoints to any caller with no credential, so these gates stop a person browsing and stop nobody else. See [Security](#security--what-is-and-is-not-enforced).

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

The browser therefore only ever talks to `:5173`, and Vite relays to Spring server-side where the same-origin policy does not apply. This is a **development convenience, not the deployed topology**. Once the two are served separately the requests are genuinely cross-origin and the API has to allow them explicitly — paid in **Phase 9 §1**, along with the other half of the same debt: `BrowserRouter` uses the History API, so a deep link to `/customers/2/edit` is a real `GET` for that path and the server must answer `index.html` rather than 404 (**Phase 9 §3**). Vite does both for free in development, which is exactly why they are easy to forget.

### 5. Build

```bash
npm run build     # → bankui/dist
npm run lint
```

---

## Phase 9 — DevOps

Four steps, in the order they unblock each other: pay the two debts Phase 8 left, then get a pipeline verifying every push, then package both halves as images, then run the whole thing with one command.

### 1. CORS — the first Phase 8 debt

The dev proxy hid a real problem: once the two applications are served separately they are genuinely different origins, and the browser blocks the request unless the **API** grants permission. Note the direction — the front end cannot grant itself access, which is the entire point.

`bankapi/src/main/java/com/bank/config/WebConfig.java` implements `WebMvcConfigurer`:

```java
registry.addMapping("/api/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET", "POST", "PUT", "DELETE")
        .allowedHeaders("Content-Type")
        .allowCredentials(false)
        .maxAge(3600);
```

The origins come from `bankapi.cors.allowed-origins`, which has a committed default of `http://localhost:5173` and is overridden per environment by the `BANKAPI_CORS_ALLOWED_ORIGINS` environment variable — Spring's relaxed binding maps the two automatically, so no `${}` indirection is needed in the properties file.

That split is the config rule this project follows:

| Kind of value | Where it lives | Example |
|---|---|---|
| Secret | Environment variable only | `MONGODB_URI` |
| Environment-specific, not secret | Committed default, overridden by env | CORS origins |
| Same everywhere | `application.properties` | database name |

Two details worth knowing, both of which look like bugs the first time:

- `Content-Type: application/json` makes the request **preflighted**. The browser sends an `OPTIONS` first and never sends the real request if that fails, so a broken CORS config shows up as a request that appears not to have happened at all.
- `.allowCredentials(false)` makes Spring **omit** `Access-Control-Allow-Credentials` entirely rather than send `false`. CORS defines no false value — absence *is* the denial. An empty column in DevTools is the header working correctly.

CORS is enforced by the browser, not by the server. It is not authorization: `curl` ignores it completely, which is why the API still validates everything it is sent.

### 2. Continuous integration

`.github/workflows/ci.yml`, on every push to any branch plus pull requests to `main`. Two jobs, sharing no state, so they run in parallel and a red build says which application broke without anyone opening a log.

| Job | Steps |
|---|---|
| **API (Java 25)** | checkout → Temurin 25 with `~/.m2` cached → `./mvnw -B clean test` → `-DskipTests package` → upload the jar |
| **UI (Node 24)** | checkout → Node 24 with npm cache → `npm ci` → `npm run lint` → `npm run build` → upload `dist` |

The point of CI is not that it runs the tests — you can do that locally. It is that it runs them somewhere that is *not* your machine: clean checkout, no IDE, none of the environment variables you set six weeks ago and forgot. The API job sets no environment variables at all, deliberately, because the suite is offline by design; if a test ever starts needing a live Atlas, this job goes red, which is the correct outcome.

`npm ci`, not `npm install`. `ci` installs exactly what `package-lock.json` pins and fails if the lock and `package.json` disagree; `install` may resolve something newer and quietly rewrite the lock. A build that can silently change its own dependencies is not reproducible.

The uploaded artifacts are the `code → artifact → image → container → pod` chain becoming real: the jar and the bundle are downloadable things a later job can consume instead of rebuilding.

> **The failure worth recording.** The first three runs died on `./mvnw: Permission denied`. Git tracks a file's executable bit, but NTFS has no such permission, so re-staging `mvnw` from a Windows working tree reverts the mode to `644` — a bug that **cannot be reproduced on the machine that caused it**. GitHub Desktop stages through its own layer and reverted the fix twice more. The index is corrected *and* the workflow runs `chmod +x ./mvnw`, because a pipeline should not be one stray `git add` away from red.
>
> A separate diagnosis in the same session — that the file had been committed with CRLF — was wrong. The byte count that suggested it came from PowerShell's `>` redirection, which rewrites line endings and adds a BOM. `git ls-files --eol` reported `i/lf w/lf`: LF all along. `.gitattributes` was kept anyway, as prevention rather than as the fix.

### 3. Docker

Both applications ship as images, built multi-stage so the toolchain never reaches production.

```bash
docker build -t bankapi:local ./bankapi
docker build -t bankui:local ./bankui
```

| Image | Base | Size |
|---|---|---|
| `bankapi` | `eclipse-temurin:25-jre` | 544 MB |
| `bankui` | `nginx:alpine` | 93.7 MB |

That gap is the argument for deploying them separately: the two halves of this application have wildly different runtime costs, and bundling the UI into Spring's `static/` would have paid the JVM's price for serving 230 kB of JavaScript.

**Layered jars.** Rather than copying the fat jar, the build stage runs `java -Djarmode=tools -jar target/*.jar extract --layers --launcher` and copies the four layers in order of how often they change — dependencies first, application classes last. A code change then rewrites only the top layer: **131 kB pushed instead of 37 MB.**

> **Boot 4 renames**, both undocumented in most tutorials: the jarmode is `tools` (Boot 3 used `layertools`), and the launcher is `org.springframework.boot.loader.launch.JarLauncher` — Boot 3 had no `.launch` segment. Verified against the extracted `MANIFEST.MF` rather than trusted from a blog.

Both runtime stages drop privileges (`spring`, uid 1001) and use an exec-form `ENTRYPOINT`, so the JVM is PID 1 and receives `SIGTERM` directly — shell-form would wrap it in `/bin/sh`, which does not forward signals, and every shutdown would be a 10-second wait followed by `SIGKILL`.

**`bankui/nginx.conf`** carries the second Phase 8 debt and one addition:

- `try_files $uri $uri/ /index.html` — the SPA fallback. `BrowserRouter` uses the History API, so refreshing on `/customers/2/edit` is a genuine `GET` for a path with no file behind it. Without this it 404s and the router — the only thing that knows what that URL means — never loads. The trade is that a real typo now returns `200`, which is why `App.jsx` carries a `path="*"` route.
- Opposite cache rules for `/assets/` (fingerprinted, `immutable`, one year) and `index.html` (`no-cache`, meaning *revalidate*, not *do not store*). `add_header` only — `expires` emits a `Cache-Control` of its own, and using both concatenates two sets of directives into one header.
- A `location /api/` reverse proxy. nginx is already in the request path serving the bundle, so routing here costs no new service and makes the two halves same-origin, which means no preflight at all. `proxy_pass` goes through a variable plus `resolver 127.0.0.11`, because a literal hostname is resolved **once at startup** and nginx refuses to start if it fails — the image would be unable to run anywhere the host `api` does not exist.

### 4. The whole stack

```bash
docker compose up -d --build     # → http://localhost:8081
docker compose up -d --build api # after changing Java — see below
docker compose down              # stop; data survives
docker compose down -v           # ...and delete the database volume
```

> **Rebuild the service you changed, and rebuild it before you debug.** `up -d` alone reuses the existing image, so a source change that is not rebuilt leaves the **old contract running against the new client** — which presents as a plain `400` and reads as a bug in code that is already correct. It cost a diagnosis here exactly once: the running image predated a request-body change, so the same call succeeded with a field the new front end had stopped sending. **Confirm by looking inside the artifact rather than at the source** — list the classes in the image and check that the type your change added is present. The source will agree with you; the container is what has to.

Three services on a private network: `mongo` (no published port — nothing outside the network has business reaching it), `api` on `8080`, and `ui` on `8081`. Compose gives each a DNS entry under its service name, which is what makes `mongodb://mongo:27017` and `proxy_pass http://api:8080` resolve.

`depends_on` alone only waits for a container to **start**, not for the database to accept connections, so `mongo` carries a `mongosh` healthcheck and `api` waits on `condition: service_healthy`. Startup logs show it working: `mongo-1 Waiting → Healthy → api-1 Starting`.

Verified end to end through nginx:

| Check | Result |
|---|---|
| `/`, `/customers`, `/customers/2/edit`, `/about`, `/contact`, `/login`, `/nonsense` | `200`, 458 bytes each — the fallback serving one bundle for every route |
| `/assets/index-*.js` | 230 947 bytes, `max-age=31536000, immutable` |
| `index.html` on every route | `no-cache` — `try_files` does an internal redirect, so `location = /index.html` re-matches |
| `POST /api/v1/customers` | `201` with a `Location` header, browser → nginx → api → mongo |
| `down`, `up`, re-read the record | still present — the named volume outlives the containers |

A local MongoDB rather than Atlas, so the stack is self-contained: no credential, no network, and a database that can be thrown away. The application cannot tell the difference — it speaks the same wire protocol either way, which is the repository layer's isolation paying off. `CustomerRepository.seedIfEmpty()` reseeds three customers into an empty collection, so `down -v && up -d` is a one-command reset to a known state — which is exactly the shape the newman suite needs.

### 5. Deliberately not done

Recorded so the gaps are on the record rather than implied by silence:

- **newman in CI.** It needs a running API and a live database — a service container and a start-wait-run-stop sequence. That is integration testing, a different shape from the two build jobs, and it waits on the compose stack it can now reuse.
- **Any deploy step.** This pipeline builds and verifies; it does not ship. CD comes after there is somewhere to ship to (Phase 11).
- **Kubernetes.** Listed in the roadmap and absent from the course curriculum, which provisions S3 + CloudFront + API Gateway + Lambda and nothing else. Whether it becomes a standalone competency or stays conceptual is an open decision, not a silent omission.

---

## Phase 11 — cloud (in progress)

Both halves are deployed and **they are not yet connected.** All of the infrastructure is Terraform (`terraform/` — `main.tf`, `api.tf`, `variables.tf`, `outputs.tf`), written that way from the start rather than clicked together in the console and codified afterwards, which front-runs Phase 12.

| Half | Where | State |
|---|---|---|
| `bankui/dist` | S3 + CloudFront | Live, but serving a **stale build** — see below |
| `bankapi` | EC2 `t3.micro`, AL2023, backed by Atlas | Running; reachable from the distribution's prefix list only |

**The S3 bucket is private and verified sealed** — the REST endpoint returns `403`, the website endpoint `404`, and only CloudFront can read it through an Origin Access Control. That matters beyond tidiness: **a public bucket is a second front door**, and anything reaching the objects directly bypasses every cache rule, response header, access log and future WAF rule. A control that can be walked around is not a control, and the bypass does not appear in the distribution's own metrics. The API's security group applies the same reasoning, accepting traffic from the CloudFront managed prefix list rather than from the internet.

**The SPA fallback is a `403 → 200 → /index.html` error mapping, and the status code is the trap.** A private bucket does **not** answer `404` for a missing key — telling a caller whether an object exists is information S3 will not give someone with no read permission, so absence and denial are deliberately indistinguishable and both arrive as `403`. Most tutorials map `404`, which against this configuration silently does nothing, and the deep link then fails in a way that looks like a routing bug.

### Two things are outstanding, and they stack

1. **The `/api` CloudFront behaviour is written but not applied.** `main.tf` contains the `ordered_cache_behavior` for `/api/*`; the live distribution does not. So `/api/v1/customers` returns `200` with `text/html` — the SPA fallback catching the path — and the browser tries to parse HTML as JSON. **Note that the symptom is a `200`**: checking the API by status code alone reports success for every path under `/api`. The content type and the byte count are what discriminate.
2. **The deployed bundle predates the sign-in and dashboard work.** The live asset is byte-for-byte the build from before that rework, so the deployed nav still reads Home / Customers / About / Contact / Log in and the `/customers` page still exists there. **Nothing observed on the live site is evidence about the current code.**

Fix (1) before (2). A fresh bundle over an unapplied behaviour gives a site that *looks* current and still cannot reach its API — the sign-in form would render and then fail on submit, and that failure reads as bad credentials rather than as a missing origin.

Two known operational hazards, recorded rather than discovered later:

- **No Elastic IP was available, so the instance's public DNS is not stable across a stop/start.** A restart moves the address the distribution points at. The symptom (API unreachable) looks nothing like the cause (the origin moved).
- **`minimum_protocol_version` is pinned to `TLSv1`**, a consequence of using the default CloudFront certificate. It cannot be raised without a custom ACM certificate in `us-east-1` — a property of the certificate, not a setting somebody forgot to tighten.

The live URL is deliberately not printed in this file. The endpoints it fronts have no authorisation, and publishing a document that describes that gap alongside the address it applies to would be handing over both halves at once.

---

## Testing

Import `bankapi/postman/bankapi.postman_collection.json` into Postman and use **Run collection** — 18 requests, 27 assertions.

> **The collection is behind the API and is not a coverage claim.** It contains **no request against any `/accounts` endpoint and none against `/customers/signin`** — the entire account surface, including deposit and withdraw, is untested by it. It also predates the change that moved customer id assignment to the server, so its create-path assertions describe the older contract. Read it as the Phase 2–5 customer-CRUD suite it was written as, not as a check on the tables above.

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

The front end sits outside that chain entirely. Everything it knows about the server is a list of URLs and their status codes — the contract published at `/v3/api-docs`. It has no build-time dependency on the API and no shared code with it, which is what makes them separately deployable in Phase 9.

Those URLs live in exactly one file, `bankui/src/services/api.js`. That is the front-end mirror of the same rule the repository package follows on the server: one layer knows how the thing behind it is reached, and everything above it is written in the application's own vocabulary. It is also what makes the contract auditable — the complete set of requests this UI can make is a short block of one-line exports, so a gap like the missing filter/search shows up as an absence you can see.

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
stage:    api-backend  ──►   database     ──►  frontend  ──►   deployed
branch:   rest_api     ──►   db_with_api  ──►  react     ──►   CI_CD
phases:     (ph 2)         (ph 4.5, 3–5)        (ph 8)       (ph 9, 11–12)
```

**The top row is stage *descriptions*; the middle row is the branch names that actually exist.** Earlier versions of this file printed only the stage names and wrote about them as though they were branches — they never were. `git branch` shows `rest_api`, `db_with_api`, `react` and `CI_CD`, plus `main` and `console-app`. The mapping is spelled out rather than corrected quietly, because anyone who read the earlier wording and went looking for a `database` branch found nothing and had to work out why.

`rest_api` ends where the API works against a hardcoded in-memory repository — that stub is what proves the layering. `db_with_api` is where storage becomes real (MongoDB Atlas) and gets proven, which is why the testing and documentation phases live there rather than with the API. `react` adds `bankui/` without changing a line of Java.

One branch sits outside the chain: **`app_branch`** carries the `Account` vertical and the customer/roles/sign-in rework. It is domain work rather than a competency level, so the stage model has no slot for it. It is **unmerged** — noted as an exception rather than absorbed into a chain it does not belong to.

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
| 9 | DevOps | CI/CD, GitHub Actions, Docker, Compose. Kubernetes deferred — see Phase 9 §5 |
| 10 | Security | Hashing, salting, BCrypt, AuthN/AuthZ, JWT |
| 11 | Cloud | AWS concepts and deployment |
| 12 | IaC | Terraform |
| 13 | Optimization | Latency, profiling, application monitoring |
| 14 | Distributed design | Microservices, Apache Kafka |

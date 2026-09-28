# GearUp Backend (Java)

The backend of **GearUp**, a car rental platform: customers browse cars, book them and see
their bookings; staff manage bookings, rentals, payments and the fleet.

This is a plain-Java redesign of the original Python/FastAPI backend, built for SE411
(Software Construction) following the [Twelve-Factor App](https://12factor.net/) methodology.
It uses **no web framework**: only the JDK's built-in HTTP server, JDBC, and five small
libraries. It serves the same API (same URLs and JSON) and the same web pages as the
Python version.

All the code is in the [`backend-java/`](backend-java) folder. **Run every command in this
README from inside that folder** (`cd backend-java`).

- [Requirements](#requirements)
- [Configure](#configure)
- [Build](#build)
- [Set up the database (admin processes)](#set-up-the-database-admin-processes)
- [Run](#run)
- [Deploy (Docker / Render)](#deploy-docker--render)
- [API](#api)
- [Project structure](#project-structure)
- [The twelve factors in this code](#the-twelve-factors-in-this-code)
- [Course topics in this code](#course-topics-in-this-code)
- [Concurrency demo: 10 simultaneous bookings](#concurrency-demo-10-simultaneous-bookings)
- [Changes from the Python backend](#changes-from-the-python-backend)
- [Testing status](#testing-status)
- [Known limitations](#known-limitations)

---

## Requirements

| Tool | Version | Notes |
|---|---|---|
| JDK | **23 or newer** | The build refuses older JDKs with a clear message. |
| Maven | 3.9.11 | Not needed on your PATH: use the included wrapper `./mvnw` (`mvnw.cmd` on Windows). |
| PostgreSQL | 17 | The production database runs 17.6; use the same major version locally (Factor X). |

**Eclipse:** *File → Import → Maven → Existing Maven Projects*, choose the `backend-java` folder.

## Configure

All configuration comes from **environment variables** (Factor III). Nothing is hardcoded and
no secrets are in the repository. The app stops at startup with a clear message if a required
variable is missing.

| Variable | Required | Example | Meaning |
|---|---|---|---|
| `PORT` | no (default `8080`) | `8080` | HTTP port to listen on |
| `GEARUP_DB_URL` | yes | `jdbc:postgresql://localhost:5432/gearup` | JDBC URL of the PostgreSQL database |
| `GEARUP_DB_USER` | yes | `postgres` | Database user |
| `GEARUP_DB_PASSWORD` | yes | | Database password |
| `GEARUP_JWT_SECRET` | yes | output of `openssl rand -base64 48` | Key that signs login tokens, at least 32 characters |

[`.env.example`](backend-java/.env.example) lists them all. Copy it to `.env` (which is git-ignored) and fill
in real values. Java does not read `.env` files itself, so load it into your shell first:

**PowerShell**
```powershell
Get-Content .env | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    Set-Item "env:$($name.Trim())" $value.Trim()
}
```

**Git Bash / Linux / macOS**
```bash
set -a; . ./.env; set +a
```

**Eclipse:** *Run → Run Configurations → Java Application → Environment* tab, add each variable.

## Build

```bash
cd backend-java
./mvnw package
```

This compiles the code, runs the unit tests and produces **one executable file**,
`target/gearup-backend.jar`, which contains the app and all its dependencies (Factor V).

## Set up the database (admin processes)

Start a local PostgreSQL 17 in Docker (the same major version as production):

```bash
docker run -d --name gearup-db -e POSTGRES_PASSWORD=dev -e POSTGRES_DB=gearup -p 5432:5432 postgres:17
```

Then run the one-off admin commands. They are in the same JAR and use the same environment
variables as the server (Factor XII):

```bash
java -jar target/gearup-backend.jar create-schema     # create missing tables and enum types (safe to repeat)
java -jar target/gearup-backend.jar seed-cars         # add the 10-car starting fleet (safe to repeat)
java -jar target/gearup-backend.jar create-staff admin@example.com Sara Admin admin
```

`create-staff <email> <first-name> <last-name> <employee|admin>` asks for the password at a
hidden prompt, so it never ends up in shell history, scripts or the repository.

## Run

```bash
java -jar target/gearup-backend.jar
```

Open <http://localhost:8080>. The same process serves the web pages and the API.
Stop it with **Ctrl+C**: the shutdown hook lets running requests finish (up to 5 seconds).

> In Eclipse, the red *Terminate* button kills the process without running shutdown hooks.
> Run the JAR from a terminal to see the graceful shutdown.

## Deploy (Docker / Render)

[`Dockerfile`](backend-java/Dockerfile) builds the JAR in one stage (JDK 25, Maven 3.9.11) and runs it in a
second, smaller stage that contains only a Java runtime and the JAR. No configuration is baked
into the image.

On **Render** (Java runs there as a Docker service):

1. *New → Web Service*, connect the GitHub repository.
2. **Language:** Docker. **Root Directory:** `backend-java`. **Dockerfile Path:** `./Dockerfile`.
3. **Region:** Singapore, the closest region to the Supabase database (ap-south-1, Mumbai).
4. **Environment variables:** `GEARUP_DB_URL`, `GEARUP_DB_USER`, `GEARUP_DB_PASSWORD` and
   `GEARUP_JWT_SECRET` (a new random value). Do not set `PORT`; Render sets it and the app
   binds to it.
5. **Health Check Path:** `/health`.

Render stops old instances with SIGTERM, which triggers the graceful shutdown hook.

## API

Same URLs and JSON field names as the Python API, so the existing frontend works unchanged.
Errors are always `{"detail": "..."}` with a meaningful status code.

| Method | Path | Who |
|---|---|---|
| GET | `/health` | anyone |
| GET | `/cars` (filters: `category`, `seats`, `transmission`, `min_price`, `max_price`, `start_date`, `end_date`) | anyone |
| POST | `/auth/signup`, `/auth/login`, `/auth/staff_login`, `/auth/reset_by_license`, `/auth/logout` | anyone |
| GET | `/auth/me` | any logged-in user |
| POST | `/reservations/create_auth` | customer |
| GET | `/reservations/my_reservations` | customer |
| PUT | `/cars/{id}` | staff |
| GET | `/reservations` | staff |
| PUT | `/reservations/{id}` | staff |
| POST | `/reservations/auto_update_statuses` | staff |
| POST | `/rentals/start`, `/rentals/close` | staff |
| GET | `/invoices?limit=100` | staff |
| POST | `/payments/pay` | staff |
| GET | `/dashboard/kpis`, `/dashboard/revenue` | staff |

Protected routes need `Authorization: Bearer <token>` (from a login response).
No or invalid token → **401**. A valid token of the wrong kind (e.g. a customer on a staff
route) → **403**. "Staff" means role `employee` or `admin`.

## Project structure

Inside `backend-java/`:

```
src/main/java/com/gearup/
  Main.java        entry point: picks serve / create-schema / seed-cars / create-staff, wires objects together
  config/          AppConfig: reads and checks environment variables
  http/            GearUpServer (HttpServer + thread pool), Router, Request/Response, Json, StaticFiles
  api/             one class per URL group; registers routes as lambdas
  auth/            PasswordHasher, TokenService (JWT), AuthGuard, LoggedInUser (sealed) and its two kinds
  service/         business rules and SQL: Auth, Car, Booking, Rental, Payment, Invoice, Dashboard
  db/              Database (transactions), Transaction (query helpers), RowMapper
  model/           records and enums shared by the layers
  exception/       ApiException and its subclasses (400, 401, 403, 404, 409)
  admin/           AdminTasks: the one-off admin commands
src/main/resources/
  db/schema.sql, db/seed-cars.sql     used by the admin commands
  static/                             the web pages
  simplelogger.properties             logging goes to stdout
src/test/java/                        unit tests (JUnit 5)
tools/ConcurrentBookingDemo.java      concurrency demo, see below
```

A request flows **Router → route lambda → (AuthGuard) → service → Database**, and any
exception flows back up to the Router, which is the one place that turns it into a response.

## The twelve factors in this code

| # | Factor | Where it is implemented |
|---|---|---|
| I | **Codebase**: one repo, many deploys | This repository. Every environment runs the same code; only the environment variables differ. |
| II | **Dependencies**: explicitly declared | [`pom.xml`](backend-java/pom.xml) pins every dependency to an exact version: slf4j-api and slf4j-simple 2.0.16, Gson 2.11.0, PostgreSQL JDBC 42.7.13, jBCrypt 0.4, and JUnit 5.11.4 (tests only). The Maven wrapper pins Maven 3.9.11. |
| III | **Config**: in the environment | [`AppConfig`](backend-java/src/main/java/com/gearup/config/AppConfig.java) reads `PORT` and `GEARUP_*` only and fails fast if one is missing; [`.env.example`](backend-java/.env.example) documents them. The old Spring `application.properties` with a hardcoded password was removed. |
| IV | **Backing services**: attached resources | [`Database`](backend-java/src/main/java/com/gearup/db/Database.java) finds PostgreSQL only through `GEARUP_DB_URL`/`_USER`/`_PASSWORD`. Moving from local Docker to Supabase is a config change, not a code change. |
| V | **Build, release, run**: separate stages | Build: `./mvnw package` creates one JAR (shade plugin in `pom.xml`). Release: that JAR plus an environment. Run: `java -jar gearup-backend.jar`. |
| VI | **Processes**: stateless | No sessions or bookings are kept in memory. Logins are signed tokens ([`TokenService`](backend-java/src/main/java/com/gearup/auth/TokenService.java)); all data and all locks live in PostgreSQL. |
| VII | **Port binding**: self-contained | [`GearUpServer`](backend-java/src/main/java/com/gearup/http/GearUpServer.java) uses `com.sun.net.httpserver.HttpServer` bound to `$PORT`; it also serves the web pages ([`StaticFiles`](backend-java/src/main/java/com/gearup/http/StaticFiles.java)). No external web server. |
| VIII | **Concurrency**: scale out with processes | A fixed pool of **10** worker threads is the server's executor. To handle more load, run more copies; `SELECT ... FOR UPDATE` row locks keep that safe across processes. |
| IX | **Disposability**: fast start, graceful stop | Starts in about 0.1–0.2 s (measured 79–223 ms). A shutdown hook calls `server.stop(5)` and `pool.shutdown()`. Exceptions are caught and logged at the request boundary in [`Router`](backend-java/src/main/java/com/gearup/http/Router.java). |
| X | **Dev/prod parity** | Same JDK level everywhere (enforced by the build), same pinned dependencies, PostgreSQL in every environment, same [`schema.sql`](backend-java/src/main/resources/db/schema.sql). |
| XI | **Logs**: event streams | SLF4J to **stdout** only ([`simplelogger.properties`](backend-java/src/main/resources/simplelogger.properties)); no log files. Example: `BookingService` logs `Booking {} confirmed for car {}`. |
| XII | **Admin processes**: one-off commands | `create-schema`, `seed-cars` and `create-staff` in [`AdminTasks`](backend-java/src/main/java/com/gearup/admin/AdminTasks.java) run from the same JAR and config as the server. |

## Course topics in this code

| Topic | Examples |
|---|---|
| **Generics** | `Transaction.queryList(String, RowMapper<T>, Object...)`, `Database.inTransaction(TransactionWork<T>)`, `LabeledEnum.fromLabel(Class<E>, String)` with the bound `E extends Enum<E> & LabeledEnum`, `AuthenticatedHandler<U extends LoggedInUser>`, `Request.queryParam(String, Function<String, T>)` |
| **Lambdas** | Every route is a lambda (`router.get("/cars", request -> ...)`); row mappers (`row -> row.getString("res_id")`); method references (`Integer::parseInt`, `LocalDate::parse`); `AuthGuard.staffOnly(...)` wraps one lambda in another |
| **Maven** | `pom.xml`, the Maven wrapper, the shade plugin (one JAR) and the enforcer plugin (JDK version) |
| **Exception handling** | The `ApiException` hierarchy maps errors to HTTP codes; `DatabaseException` wraps the checked `SQLException`; transactions roll back on any exception; one catch point in `Router` |
| **Logging (SLF4J)** | A `Logger` per class with `{}` placeholders; passwords and secrets are never logged (records mask them in `toString()`) |
| **Advanced OO** | `sealed interface LoggedInUser permits CustomerUser, StaffUser`; records for all data; enums with behaviour (`ReservationStatus.carStatus()`, `PaymentStatus.forAmounts(...)`); pattern matching `instanceof`; constructor injection in `Main` |
| **Concurrency** | `Executors.newFixedThreadPool(10)` with a named `ThreadFactory` and `AtomicInteger`; graceful `shutdown()` + `awaitTermination()`; thread-safety decisions documented in the code (shared thread-safe `Gson`, a new `Mac` per call, one connection per transaction); database row locks (`FOR UPDATE`) against double booking and double payment; the demo below uses `CountDownLatch` and `Future` |

## Concurrency demo: 10 simultaneous bookings

Ten threads try to book **the same car for the same dates at the same instant**. Without
protection, all ten could pass the "is the car free?" check before any of them inserts a
booking. `BookingService.book` locks the car's row with `SELECT ... FOR UPDATE` first, so the
requests queue up in the database: the first books the car, and the other nine then see that
booking and get **409 Conflict**.

With the server running against a database (after `create-schema` and `seed-cars`):

```bash
java tools/ConcurrentBookingDemo.java                            # http://localhost:8080, car CAR-1
java tools/ConcurrentBookingDemo.java http://localhost:8080 CAR-3
```

The demo needs no build step: the JDK runs the source file directly. It signs up a throwaway
customer, picks random dates far in the future, starts 10 threads that wait on a
`CountDownLatch`, releases them all at once, and counts the responses. Expected output:

```
Responses by HTTP status: {200=1, 409=9}
PASS: exactly one booking succeeded; the other 9 got 409 Conflict.
```

It exits with code 0 on PASS and 1 otherwise. It creates only records marked `TEST`
(`@example.invalid` emails), so they are easy to find and delete afterwards.

**Result against the production database (2026-09-27), on a TEST car:**

```
Responses by HTTP status: {200=1, 409=9}
PASS: exactly one booking succeeded; the other 9 got 409 Conflict.
```

The server log shows the lock at work: the ten requests arrived together, the booking finished
after 4.3 s, and the nine 409s then completed one after another, between 4.8 s and 9.6 s, as
each waited its turn for the car's row lock.

## Changes from the Python backend

The API is kept compatible, with these deliberate changes:

**Security and correctness**
- **Staff endpoints now require a staff token.** In the Python version, `PUT /cars`,
  `GET/PUT /reservations`, `/reservations/auto_update_statuses`, `/rentals/*`, `/invoices` and
  `/dashboard/*` had no login check at all. They now return 401 without a token and 403 for
  customers.
- **No more double bookings.** Python checked availability and then inserted without a lock,
  so two simultaneous requests could book the same car. Now the car row is locked first.
  Payments, rental start/close and the expiry job lock their rows the same way.
- **Staff tokens carry a `role` claim.** Staff tokens issued by the Python backend have no
  role, so staff must sign in again; the staff page now redirects to login on 403 as well as 401.
- **No hardcoded secrets.** The Python code fell back to `"dev-secret-change-me"` for the JWT
  key, and the old Spring code had the database password in `application.properties`.

**Business rules**
- A car under maintenance can no longer be booked (409).
- A booking only moves a car to Reserved if it is Available (Python could overwrite Rented).
- Closing a rental keeps a partly-paid invoice as `partial` (Python reset it to `unpaid`).
- Negative damage/refuel fees are rejected; payment amounts can have at most 2 decimals.
- Money in error messages always uses ASCII digits, even on a server with an Arabic locale.

**Database**
- `schema.sql` does not create `CHECK (start_date >= CURRENT_DATE)` (named `no_past_start` in
  the original database). PostgreSQL re-checks CHECK constraints on every UPDATE, so once a
  booking's start date had passed, *any* change to it failed, including starting, closing or
  cancelling it. This was confirmed on the production database: a no-op
  `UPDATE ... SET status = status` on a past-dated reservation was refused with
  `23514 ... violates check constraint "no_past_start"` (in a transaction that was rolled
  back). The constraint was removed from the production database on 2026-09-27; only
  `min_one_day` (`end_date > start_date`) remains. The rule is enforced when a booking is
  created instead: by `BookingService` here and by the request validator in the Python backend.
- Readable IDs (`CAR-12`) are `GENERATED ALWAYS AS (...) STORED` columns.

**Compatibility kept**
- Existing passwords keep working: `PasswordHasher` reads passlib's `bcrypt_sha256` (v2 and v1)
  and plain bcrypt hashes, and new hashes are in passlib's format, so the Python backend can
  read them too. Tokens are compatible with PyJWT when both use the same secret.

**Frontend** (copied from the Python project, with these changes; see the git history)
- `config.js` calls the API on the page's own origin.
- `staff.html` treats 403 like 401 (back to the login page).
- `staff.html` reloads the car list after a reservation status change, and "Save Changes" on a
  car sends only the fields that were changed, so a stale status can no longer overwrite the
  server's.
- The booking dialog has its white background again (its form was missing `class="card"`),
  and the "You need to sign in" hint no longer shows for signed-in users (a CSS `display`
  rule overrode the `hidden` attribute).
- CORS is no longer needed because pages and API share one origin, so `ALLOWED_ORIGINS` is gone.
  The token lifetime is fixed at 120 minutes (`JWT_EXPIRES_MIN` is gone).

## Testing status

What has and has not been verified.

### Verified against the production database (2026-09-27)

All six tables were backed up before testing. Writes used only records marked `TEST`, which
were deleted afterwards; a fresh snapshot then had **identical row counts and SHA-256
checksums** to the backup for all six tables.

- **Schema matches the code (read-only):** all six enum types and their values (including
  `payment_method`), every table's columns, and the generated `CAR-`/`RES-`/... ids.
- **Existing password hashes are in formats the code reads:** bcrypt_sha256 v2 for 9 customers
  and both employees, plain bcrypt for 1 customer (the Python customer login rejected that
  format; the Java one accepts it). Real users' logins were not tried, since their passwords
  are unknown; the passlib-generated test vectors cover these formats.
- **Every GET endpoint** returns 200 with the field names the frontend uses, on real data.
- **40 of 40 write checks passed** on TEST records: sign-up (and 409 on a duplicate email), login,
  password reset, staff login (and 401 on the wrong role), `create-staff`, booking with the
  right total, 409 on an overlap, date search hiding a booked car, both reservation lists,
  car update, rental start (and 400 when repeated), partial and full payments (and 400 on
  overpaying or paying twice), rental close with a damage fee keeping the invoice `partial`,
  a manual cancel, the dashboard, and 403 for every wrong-role call.
- **The concurrency demo passed** (see [above](#concurrency-demo-10-simultaneous-bookings)).
- **The `no_past_start` problem was real** (see [Changes](#changes-from-the-python-backend)); the
  constraint has since been dropped, and only `min_one_day` remains (checked read-only).
- The log line `Booking {} confirmed for car {}` appeared once for each successful booking,
  and the server logged no errors.

### Second round against the same database (2026-09-27)

This round covered the admin commands and the web pages. The database was backed up first,
and the seeded cars and `TEST` records were deleted afterwards. A row-by-row comparison with
the backup then found **exactly one difference: reservation RES-4 changed from Active to
Cancelled**, the expected result of `auto_update_statuses` described below.

- **`create-schema`, run twice** on the existing database: "Schema is up to date" both times
  (exit code 0), and the enum types and constraints were unchanged afterwards.
- **`seed-cars`, run twice:** 10 cars added, then 0. The 10 seeded cars were then deleted,
  after a check that none of their plate numbers existed before and none had a reservation.
- **The web pages in a browser (Java server on JDK 25, real database):**
  - customer side: sign-up page, home page car list, booking the TEST car through the
    reservation dialog (total 300 SAR for 3 days), "My reservations" showing it as
    Reserved/Unpaid, sign-out, and signing back in on the login page;
  - staff side, as a TEST admin: staff login, dashboard KPIs, reservation and car lists,
    changing the TEST reservation to Active, recording a payment (invoice became `partial`),
    and editing the TEST car's price.
- **`auto_update_statuses`** (called automatically by the staff page) returned
  `{"updated": 1}`: RES-4, an Active reservation that ended on 2025-11-26 and was never paid,
  was closed as Cancelled.
- **A bug found in the staff page, since fixed** (inherited from the Python version, not a
  backend bug): after a reservation's status changed, the "Manage Cars" cards were not
  refreshed, and "Save Changes" sent the card's stale status along with the price. In the
  test, the backend correctly set the TEST car to Rented when its reservation became Active,
  and then saving a price change from the stale card set it back to Reserved. Now the car
  list reloads after a status change, and "Save Changes" sends only the fields that were
  actually changed (checked in the browser with a stubbed server).

### Further checks (2026-09-28)

- **`auto_update_statuses` closing a paid reservation:** a `TEST` reservation that was Active,
  fully paid and ended two days earlier became **Completed**, and its car went from Rented to
  **Available**. The `TEST` records were deleted afterwards, and a row-by-row comparison with a
  backup taken just before found no differences.
- **Logging in with a password hashed by the Python backend's library:** a bcrypt_sha256 hash
  generated with passlib 1.7.4 was stored on an existing admin account, and that account
  signed in successfully on the deployed Java backend (Render).
- **Logging in as a customer created on the deployed Java site:** `POST /auth/login` returned
  200 with a token. The same credentials correctly got 401 on the staff login.

### Verified without a database

- **The build on JDK 25** (Temurin 25.0.4): `./mvnw clean package` with the committed
  `pom.xml` and no overrides. The JDK-version rule passes, `javac` compiles with `release 23`
  (class files are Java 23, major version 67), all tests pass, and the JAR starts and answers
  `/health`.
- **25 unit tests pass** (`./mvnw package` runs them):
  - `PasswordHasherTest` (9): uses hashes **produced by passlib 1.7.4**, the Python backend's
    own library: bcrypt_sha256 v2 (4 and 12 rounds), a non-ASCII (Arabic) password, legacy v1,
    and plain `$2b$` bcrypt. Checked that the tests fail if the v2 algorithm is broken, and that
    hashes made by the Java code verify in passlib.
  - `TokenServiceTest` (7): round trips, a token **signed by PyJWT 2.9.0**, expiry, wrong
    secret, and a customer token edited to claim the admin role.
  - `LabeledEnumTest` (5) and `PaymentStatusTest` (4).
- **Over HTTP, without a database:**
  - every protected endpoint returns 401 without a token and 403 with the wrong kind of token;
  - input validation returns 400 with a clear message;
  - unknown paths return 404 and wrong methods 405;
  - database failures become a logged 500.
- **Static files:** the web pages are served, and path tricks such as `/../db/schema.sql` get 404.
- **In a browser:** the staff page with an old role-less token redirects to the login page.
- **Configuration:** a missing variable or a short JWT secret stops startup with a clear message.
- 20 simultaneous requests were spread across the worker threads.
- **Graceful shutdown with Ctrl+C** (JDK 25, Windows PowerShell): the shutdown hook ran on the
  `shutdown-hook` thread and the port was released. With no requests in flight, the stop took
  12 ms, because `server.stop(5)` only waits while requests are still running:

  ```
  04:58:34.156 [shutdown-hook] INFO GearUpServer - Shutting down, waiting up to 5 s for in-flight requests
  04:58:34.168 [shutdown-hook] INFO GearUpServer - Shutdown complete
  ```

### Not verified yet

- **`create-schema` on an empty database.** It has only been run against the existing
  tables, where it correctly changes nothing; creating everything from scratch needs a fresh
  (for example local Docker) database.

## Known limitations

- **A new database connection per request.** There is no connection pool (such as HikariCP),
  to keep the dependency list small. Against the production Supabase database, opening a
  connection takes 1.4–2.8 s while a query on an open connection takes about 0.2 s, so most
  requests take about 2 s.
- **Password reset** only needs the email and license number, as in the Python version.
- **Tokens cannot be revoked** before they expire (120 minutes), because the server keeps no
  session state.
- **Payments:** the staff page hides the payment panel from employees, but the API lets both
  employees and admins record payments, as in the Python version.

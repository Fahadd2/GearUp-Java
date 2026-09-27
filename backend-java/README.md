# GearUp Backend (Java)

The backend of **GearUp**, a car rental platform: customers browse cars, book them and see
their bookings; staff manage bookings, rentals, payments and the fleet.

This is a plain-Java redesign of the original Python/FastAPI backend, built for SE411
(Software Construction) following the [Twelve-Factor App](https://12factor.net/) methodology.
It uses **no web framework**: only the JDK's built-in HTTP server, JDBC, and five small
libraries. It serves the same API (same URLs and JSON) and the same web pages as the
Python version.

- [Requirements](#requirements)
- [Configure](#configure)
- [Build](#build)
- [Set up the database (admin processes)](#set-up-the-database-admin-processes)
- [Run](#run)
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

[`.env.example`](.env.example) lists them all. Copy it to `.env` (which is git-ignored) and fill
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
java -jar target/gearup-backend.jar create-staff admin@gearup.sa Fahad Admin admin
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
| II | **Dependencies**: explicitly declared | [`pom.xml`](pom.xml) pins every dependency to an exact version: slf4j-api and slf4j-simple 2.0.16, Gson 2.11.0, PostgreSQL JDBC 42.7.13, jBCrypt 0.4, and JUnit 5.11.4 (tests only). The Maven wrapper pins Maven 3.9.11. |
| III | **Config**: in the environment | [`AppConfig`](src/main/java/com/gearup/config/AppConfig.java) reads `PORT` and `GEARUP_*` only and fails fast if one is missing; [`.env.example`](.env.example) documents them. The old Spring `application.properties` with a hardcoded password was removed. |
| IV | **Backing services**: attached resources | [`Database`](src/main/java/com/gearup/db/Database.java) finds PostgreSQL only through `GEARUP_DB_URL`/`_USER`/`_PASSWORD`. Moving from local Docker to Supabase is a config change, not a code change. |
| V | **Build, release, run**: separate stages | Build: `./mvnw package` creates one JAR (shade plugin in `pom.xml`). Release: that JAR plus an environment. Run: `java -jar gearup-backend.jar`. |
| VI | **Processes**: stateless | No sessions or bookings are kept in memory. Logins are signed tokens ([`TokenService`](src/main/java/com/gearup/auth/TokenService.java)); all data and all locks live in PostgreSQL. |
| VII | **Port binding**: self-contained | [`GearUpServer`](src/main/java/com/gearup/http/GearUpServer.java) uses `com.sun.net.httpserver.HttpServer` bound to `$PORT`; it also serves the web pages ([`StaticFiles`](src/main/java/com/gearup/http/StaticFiles.java)). No external web server. |
| VIII | **Concurrency**: scale out with processes | A fixed pool of **10** worker threads is the server's executor. To handle more load, run more copies; `SELECT ... FOR UPDATE` row locks keep that safe across processes. |
| IX | **Disposability**: fast start, graceful stop | Starts in about 0.1–0.2 s (measured 79–223 ms). A shutdown hook calls `server.stop(5)` and `pool.shutdown()`. Exceptions are caught and logged at the request boundary in [`Router`](src/main/java/com/gearup/http/Router.java). |
| X | **Dev/prod parity** | Same JDK level everywhere (enforced by the build), same pinned dependencies, PostgreSQL in every environment, same [`schema.sql`](src/main/resources/db/schema.sql). |
| XI | **Logs**: event streams | SLF4J to **stdout** only ([`simplelogger.properties`](src/main/resources/simplelogger.properties)); no log files. Example: `BookingService` logs `Booking {} confirmed for car {}`. |
| XII | **Admin processes**: one-off commands | `create-schema`, `seed-cars` and `create-staff` in [`AdminTasks`](src/main/java/com/gearup/admin/AdminTasks.java) run from the same JAR and config as the server. |

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
  production). PostgreSQL re-checks CHECK constraints on every UPDATE, so once a booking's start
  date has passed, *any* change to it fails, including starting, closing or cancelling it. This
  was confirmed on the production database: a no-op `UPDATE ... SET status = status` on a
  reservation that started 355 days ago was refused with `23514 ... violates check constraint
  "no_past_start"` (in a transaction that was rolled back). All 6 existing reservations are
  affected, in both the Python and the Java backend. `BookingService` enforces the rule when a
  booking is created instead. Removing it from production is a separate decision:
  `ALTER TABLE public.reservations DROP CONSTRAINT no_past_start;`
- Readable IDs (`CAR-12`) are `GENERATED ALWAYS AS (...) STORED` columns.

**Compatibility kept**
- Existing passwords keep working: `PasswordHasher` reads passlib's `bcrypt_sha256` (v2 and v1)
  and plain bcrypt hashes, and new hashes are in passlib's format, so the Python backend can
  read them too. Tokens are compatible with PyJWT when both use the same secret.

**Frontend** (copied from the Python project, then two edits; see the git history)
- `config.js` calls the API on the page's own origin.
- `staff.html` treats 403 like 401 (back to the login page).
- CORS is no longer needed because pages and API share one origin, so `ALLOWED_ORIGINS` is gone.
  The token lifetime is fixed at 120 minutes (`JWT_EXPIRES_MIN` is gone).

## Testing status

An honest summary of what has and has not been verified.

### Verified against the production database (2026-09-27)

Rules followed: all six tables were backed up to CSV (outside the repository) first; no
`create-schema`/`seed-cars`, no DROP/ALTER/TRUNCATE, no change to any existing row or password;
writes used only records marked `TEST`, which were deleted afterwards. A fresh snapshot after
cleanup had **identical row counts and SHA-256 checksums** to the backup for all six tables.

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
- **The `no_past_start` problem is real** (see [Changes](#changes-from-the-python-backend)).
- The log line `Booking {} confirmed for car {}` appeared once for each successful booking,
  and the server logged no errors.

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

- **`create-schema` and `seed-cars`** have not been run anywhere: not on production (the
  tables and data already exist), and there is no local Docker database yet.
- **`POST /reservations/auto_update_statuses`** was not called, because it changes real
  reservations. Because of `no_past_start`, it is expected to fail with a 500 whenever there is
  an Active reservation that has ended. Note that the staff page calls it on every load.
- **The web pages in a browser against real data.** Only the API was exercised; the staff
  page was not opened, because it calls `auto_update_statuses` on load.

## Known limitations

- **A new database connection per request, which makes every request slow against a remote
  database.** There is no connection pool (such as HikariCP), to keep the dependency list
  small. Measured against the production Supabase database: opening a connection takes
  1.4–2.8 s, while a query on an open connection takes about 0.2 s, so most requests take
  about 2 s. A connection pool would fix this.
- **Password reset** only needs the email and license number, as in the Python version.
  A real system would send a reset link by email.
- **Tokens cannot be revoked** before they expire (120 minutes), a normal trade-off of
  stateless tokens.
- **Payments:** the staff page hides the payment panel from employees, but the API lets both
  employees and admins record payments, as in the Python version.

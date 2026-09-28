# GearUp Backend (Java)

The backend of **GearUp**, a car rental platform: customers browse cars, book them and see
their bookings; staff manage bookings, rentals, payments and the fleet.

It is written in plain Java for SE411 (Software Construction), following the
[Twelve-Factor App](https://12factor.net/) methodology. It uses **no web framework**: only the
JDK's built-in HTTP server, JDBC, and five small libraries. One process serves both the JSON
API and the web pages.

All the code is in the [`backend-java/`](backend-java) folder. **Run every command in this
README from inside that folder** (`cd backend-java`).

- [Requirements](#requirements)
- [Configure](#configure)
- [Build](#build)
- [Set up the database (admin processes)](#set-up-the-database-admin-processes)
- [Run](#run)
- [Docker](#docker)
- [API](#api)
- [Project structure](#project-structure)
- [The twelve factors in this code](#the-twelve-factors-in-this-code)
- [Course topics in this code](#course-topics-in-this-code)
- [Concurrency demo: 10 simultaneous bookings](#concurrency-demo-10-simultaneous-bookings)
- [Design decisions](#design-decisions)
- [Testing](#testing)
- [Known limitations](#known-limitations)

---

## Requirements

| Tool | Version | Notes |
|---|---|---|
| JDK | **23 or newer** | The build refuses older JDKs with a clear message. |
| Maven | 3.9.11 | Included: the wrapper `./mvnw` (`mvnw.cmd` on Windows) downloads it automatically. |
| PostgreSQL | 17 | The production database runs 17.6; use the same major version locally (Factor X). |

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
docker run -d --name gearup-db -e POSTGRES_PASSWORD=dev -e POSTGRES_DB=gearup -p 127.0.0.1:5432:5432 postgres:17
```

`127.0.0.1:` makes the database reachable only from this computer. Without it, Docker opens
port 5432 to the whole network, with the easy-to-guess password `dev`.

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

Open <http://localhost:8080>. On shutdown, running requests get up to 5 seconds to finish.

## Docker

[`Dockerfile`](backend-java/Dockerfile) builds the JAR in one stage (JDK 25, Maven 3.9.11) and
runs it in a second, smaller stage that contains only a Java runtime and the JAR. No
configuration is baked into the image: the environment variables above are passed in when the
container starts, and the app listens on `$PORT`. `GET /health` can serve as a health check,
and stopping the container (SIGTERM) triggers the graceful shutdown.

## API

Requests and responses are JSON with snake_case field names. Errors are always
`{"detail": "..."}` with a meaningful status code.

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
| III | **Config**: in the environment | [`AppConfig`](backend-java/src/main/java/com/gearup/config/AppConfig.java) reads `PORT` and `GEARUP_*` only and fails fast if one is missing; [`.env.example`](backend-java/.env.example) documents them. |
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

## Design decisions

**Security and correctness**
- **Staff-only endpoints** (`PUT /cars`, `GET/PUT /reservations`,
  `/reservations/auto_update_statuses`, `/rentals/*`, `/invoices`, `/payments/pay`,
  `/dashboard/*`) require a staff token: 401 without a token, 403 for customers.
- **No double bookings.** `BookingService` locks the car's row (`SELECT ... FOR UPDATE`) before
  checking for overlapping bookings, so simultaneous requests cannot both book the same car.
  Payments, rental start/close and the expiry job lock their rows the same way.
- **Staff tokens carry a `role` claim** (`employee` or `admin`); tokens without it are
  customer tokens. The staff page sends the user back to the login page on 401 or 403.
- **No secrets in the code.** The database credentials and the token key come only from
  environment variables.

**Business rules**
- A car under maintenance cannot be booked (409).
- A booking moves a car to Reserved only if it is Available, never overwriting Rented.
- Closing a rental recalculates the invoice status from the payments made, so a partly-paid
  invoice stays `partial`.
- Negative damage/refuel fees are rejected; payment amounts can have at most 2 decimals.
- Money in error messages always uses ASCII digits, even on a server with an Arabic locale.

**Database**
- **No `CHECK (start_date >= CURRENT_DATE)` constraint.** PostgreSQL re-checks CHECK
  constraints on every UPDATE, so such a constraint would block any change to a booking once
  its start date had passed, including starting, closing or cancelling it. The rule is enforced
  by `BookingService` when a booking is created. `min_one_day` (`end_date > start_date`) is
  kept.
- Readable IDs (`CAR-12`) are `GENERATED ALWAYS AS (...) STORED` columns.

**Passwords and tokens**
- `PasswordHasher` stores passwords as `bcrypt_sha256` (the format used by the passlib
  library: HMAC-SHA256, then bcrypt) and also accepts older `bcrypt_sha256` v1 and plain
  bcrypt hashes.
- Login tokens are standard HS256 JSON Web Tokens that expire after 120 minutes.

**Frontend**
- The pages and the API share one origin, so no CORS configuration is needed; `config.js`
  calls the API on the page's own origin.
- On the staff page, "Save Changes" on a car sends only the fields that were changed, and the
  car list reloads after a reservation's status changes, so a stale value on the page cannot
  overwrite the server's.

## Testing

**Unit tests** (25, run by `./mvnw package`)
- `PasswordHasherTest`: hashes produced by passlib 1.7.4 (bcrypt_sha256 v2 and v1, plain
  bcrypt, a non-ASCII password); hashes made by the Java code also verify in passlib.
- `TokenServiceTest`: round trips, a token signed by PyJWT, expiry, a wrong secret, and a
  customer token edited to claim the admin role.
- `LabeledEnumTest` and `PaymentStatusTest`.

**Integration tests**, run against a fresh PostgreSQL 17 database and against the production
database (using only records marked `TEST`, deleted afterwards):
- `create-schema` builds the whole schema on an empty database, and running it again changes
  nothing; `seed-cars` adds the fleet once.
- 40 checks across the API: sign-up, login, password reset, staff login, booking (totals,
  overlaps, date search), reservation lists and status changes, rental start and close, partial
  and full payments, invoices, the dashboard, and 401/403 on every protected route.
- `auto_update_statuses` closes ended reservations as Completed (paid) or Cancelled (unpaid)
  and makes their cars Available again.
- The [concurrency demo](#concurrency-demo-10-simultaneous-bookings) gives `{200=1, 409=9}`.
- The customer and staff web pages work end to end in a browser.
- Accounts with existing passlib-generated password hashes can log in.

**Other checks**
- The build passes on JDK 25 (compiled for release 23).
- Unknown paths return 404, wrong methods 405, and database failures a logged 500; path
  tricks such as `/../db/schema.sql` cannot reach files outside the web pages.
- A missing or invalid environment variable stops startup with a clear message.
- The shutdown hook stops the server gracefully and releases the port.

## Known limitations

- **A new database connection per request.** There is no connection pool (such as HikariCP),
  to keep the dependency list small. Against the production Supabase database, opening a
  connection takes 1.4–2.8 s while a query on an open connection takes about 0.2 s, so most
  requests take about 2 s.
- **Password reset** only needs the email and license number.
- **Tokens cannot be revoked** before they expire (120 minutes), because the server keeps no
  session state.
- **Payments:** the staff page hides the payment panel from employees, but the API lets both
  employees and admins record payments.

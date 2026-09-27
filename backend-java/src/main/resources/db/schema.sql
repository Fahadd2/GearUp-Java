-- GearUp database schema (PostgreSQL 16).
--
-- Factor XII: run as a one-off admin process from the same build and config as the app:
--     java -jar target/gearup-backend.jar create-schema
--
-- Safe to run more than once: objects that already exist are skipped, so it can also be
-- run against the existing production database. The whole script runs in one transaction.


-- ---------------------------------------------------------------------------
-- Enum types (CREATE TYPE has no IF NOT EXISTS, so duplicates are ignored instead)
-- ---------------------------------------------------------------------------
DO $$ BEGIN
    CREATE TYPE public.car_category AS ENUM ('small', 'large');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE public.transmission_type AS ENUM ('auto', 'manual');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE public.car_status AS ENUM ('Available', 'Reserved', 'Rented', 'Maintenance');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE public.reservation_status AS ENUM ('Reserved', 'Active', 'Completed', 'Cancelled');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE public.payment_status AS ENUM ('unpaid', 'partial', 'paid');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE public.payment_method AS ENUM ('cash', 'card', 'transfer');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;


-- ---------------------------------------------------------------------------
-- Tables. Readable IDs such as CAR-12 are generated from an identity column.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.customers (
    license_no     text NOT NULL,
    first_name     text NOT NULL,
    last_name      text NOT NULL,
    email          text NOT NULL UNIQUE,
    phone          text,
    license_expiry date NOT NULL,
    date_of_birth  date,
    address_street text,
    address_city   text,
    password_hash  text,
    created_at     timestamp with time zone DEFAULT now(),
    CONSTRAINT customers_pkey PRIMARY KEY (license_no)
);

CREATE TABLE IF NOT EXISTS public.employees (
    emp_seq       integer GENERATED ALWAYS AS IDENTITY NOT NULL UNIQUE,
    emp_id        text GENERATED ALWAYS AS ('EMP-' || emp_seq::text) STORED,
    first_name    text NOT NULL,
    last_name     text NOT NULL,
    email         text NOT NULL UNIQUE,
    phone         text,
    role          text NOT NULL CHECK (role IN ('employee', 'admin')),
    hire_date     date NOT NULL,
    password_hash text NOT NULL,
    created_at    timestamp with time zone DEFAULT now(),
    CONSTRAINT employees_pkey PRIMARY KEY (emp_id)
);

CREATE TABLE IF NOT EXISTS public.cars (
    car_seq       integer GENERATED ALWAYS AS IDENTITY NOT NULL UNIQUE,
    car_id        text GENERATED ALWAYS AS ('CAR-' || car_seq::text) STORED,
    plate_no      text NOT NULL UNIQUE,
    brand         text NOT NULL,
    model         text NOT NULL,
    year          integer NOT NULL CHECK (year >= 1980),
    category      public.car_category NOT NULL,
    fuel_type     text NOT NULL,
    color         text NOT NULL,
    seats         integer NOT NULL CHECK (seats > 0),
    transmission  public.transmission_type NOT NULL,
    price_per_day numeric NOT NULL CHECK (price_per_day > 0),
    status        public.car_status NOT NULL DEFAULT 'Available',
    photo_url     text,
    created_at    timestamp with time zone DEFAULT now(),
    CONSTRAINT cars_pkey PRIMARY KEY (car_id)
);

-- Note: "start_date >= CURRENT_DATE" is deliberately NOT a CHECK constraint here.
-- PostgreSQL re-checks CHECK constraints on every UPDATE, so once a booking's start date
-- had passed, even changing its status (start, close, cancel) would fail. The rule is
-- enforced by BookingService when a booking is created instead.
CREATE TABLE IF NOT EXISTS public.reservations (
    res_seq             integer GENERATED ALWAYS AS IDENTITY NOT NULL UNIQUE,
    res_id              text GENERATED ALWAYS AS ('RES-' || res_seq::text) STORED,
    customer_license_no text NOT NULL,
    car_id              text NOT NULL,
    start_date          date NOT NULL,
    end_date            date NOT NULL,
    status              public.reservation_status NOT NULL DEFAULT 'Reserved',
    created_at          timestamp with time zone DEFAULT now(),
    created_by_emp_id   text,
    CONSTRAINT reservations_pkey PRIMARY KEY (res_id),
    CONSTRAINT reservations_customer_license_no_fkey
        FOREIGN KEY (customer_license_no) REFERENCES public.customers (license_no),
    CONSTRAINT reservations_car_id_fkey FOREIGN KEY (car_id) REFERENCES public.cars (car_id),
    CONSTRAINT fk_res_created_by FOREIGN KEY (created_by_emp_id) REFERENCES public.employees (emp_id)
);

CREATE TABLE IF NOT EXISTS public.invoices (
    inv_seq          integer GENERATED ALWAYS AS IDENTITY NOT NULL UNIQUE,
    inv_id           text GENERATED ALWAYS AS ('INV-' || inv_seq::text) STORED,
    reservation_id   text NOT NULL UNIQUE,
    issue_date       date NOT NULL DEFAULT CURRENT_DATE,
    total_amount     numeric NOT NULL CHECK (total_amount >= 0),
    payment_status   public.payment_status NOT NULL DEFAULT 'unpaid',
    created_at       timestamp with time zone DEFAULT now(),
    issued_by_emp_id text,
    CONSTRAINT invoices_pkey PRIMARY KEY (inv_id),
    CONSTRAINT invoices_reservation_id_fkey FOREIGN KEY (reservation_id) REFERENCES public.reservations (res_id),
    CONSTRAINT fk_inv_issued_by FOREIGN KEY (issued_by_emp_id) REFERENCES public.employees (emp_id)
);

CREATE TABLE IF NOT EXISTS public.payments (
    pay_seq            integer GENERATED ALWAYS AS IDENTITY NOT NULL UNIQUE,
    pay_id             text GENERATED ALWAYS AS ('PAY-' || pay_seq::text) STORED,
    invoice_id         text NOT NULL,
    method             public.payment_method NOT NULL,
    amount             numeric NOT NULL CHECK (amount > 0),
    paid_at            timestamp with time zone NOT NULL DEFAULT now(),
    reference          text,
    recorded_by_emp_id text NOT NULL,
    CONSTRAINT payments_pkey PRIMARY KEY (pay_id),
    CONSTRAINT payments_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.invoices (inv_id),
    CONSTRAINT fk_pay_recorded_by FOREIGN KEY (recorded_by_emp_id) REFERENCES public.employees (emp_id)
);

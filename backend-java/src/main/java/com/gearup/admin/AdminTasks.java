package com.gearup.admin;

import com.gearup.auth.PasswordHasher;
import com.gearup.db.Database;
import com.gearup.db.DatabaseException;
import com.gearup.model.LabeledEnum;
import com.gearup.model.StaffRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * One-off admin processes (Factor XII).
 *
 * <p>They ship in the same JAR and read the same environment variables as the web server,
 * so they always run against the same code and database as the release they belong to:
 *
 * <pre>
 *   java -jar gearup-backend.jar create-schema
 *   java -jar gearup-backend.jar seed-cars
 *   java -jar gearup-backend.jar create-staff &lt;email&gt; &lt;first-name&gt; &lt;last-name&gt; &lt;employee|admin&gt;
 * </pre>
 */
public final class AdminTasks {

    private static final Logger log = LoggerFactory.getLogger(AdminTasks.class);

    private static final String CREATE_STAFF_USAGE =
            "Usage: create-staff <email> <first-name> <last-name> <employee|admin>";
    private static final int MIN_STAFF_PASSWORD_LENGTH = 8;

    private AdminTasks() {
    }

    /** Creates the enum types and tables that do not exist yet. */
    public static void createSchema(Database database) {
        String script = readResource("/db/schema.sql");
        database.inTransaction(tx -> {
            tx.executeScript(script);
            return null;
        });
        log.info("Schema is up to date");
    }

    /** Adds the initial car fleet, skipping cars whose plate number is already in the database. */
    public static void seedCars(Database database) {
        String insertFleet = readResource("/db/seed-cars.sql");
        int added = database.inTransaction(tx -> tx.update(insertFleet));
        log.info("Seeded car fleet: {} new car(s) added", added);
    }

    /**
     * Adds a staff account. The password is typed at a hidden prompt, so it never appears in
     * the command line, shell history, source code or environment (Factor III).
     *
     * @param args email, first name, last name and role
     * @throws IllegalArgumentException if the arguments are wrong or the email is already used
     */
    public static void createStaff(Database database, PasswordHasher passwords, List<String> args) {
        if (args.size() != 4) {
            throw new IllegalArgumentException(CREATE_STAFF_USAGE);
        }
        String email = args.get(0).strip();
        String firstName = args.get(1);
        String lastName = args.get(2);
        StaffRole role = LabeledEnum.fromLabel(StaffRole.class, args.get(3));
        if (!email.contains("@")) {
            throw new IllegalArgumentException("'" + email + "' is not an email address. " + CREATE_STAFF_USAGE);
        }

        String password = readPassword(email);
        if (password.length() < MIN_STAFF_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Staff passwords must be at least " + MIN_STAFF_PASSWORD_LENGTH + " characters");
        }
        String passwordHash = passwords.hash(password);

        try {
            String empId = database.inTransaction(tx -> tx.queryOne("""
                            INSERT INTO public.employees (first_name, last_name, email, role, hire_date, password_hash)
                            VALUES (?, ?, ?, ?, CURRENT_DATE, ?)
                            RETURNING emp_id""",
                    row -> row.getString("emp_id"),
                    firstName, lastName, email, role, passwordHash).orElseThrow());
            log.info("Created {} account {} for {}", role.label(), empId, email);
        } catch (DatabaseException e) {
            if (e.isUniqueViolation()) {
                throw new IllegalArgumentException("An employee with email " + email + " already exists");
            }
            throw e;
        }
    }

    /** Reads a password without echoing it when run in a terminal; falls back to stdin (e.g. in an IDE). */
    private static String readPassword(String email) {
        Console console = System.console();
        if (console != null) {
            char[] typed = console.readPassword("Password for %s (input hidden): ", email);
            if (typed == null) {
                throw new IllegalArgumentException("No password entered");
            }
            return new String(typed);
        }

        log.warn("No interactive console: reading the password for {} from standard input", email);
        try {
            BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line = stdin.readLine();
            if (line == null) {
                throw new IllegalArgumentException("No password entered");
            }
            return line;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the password", e);
        }
    }

    private static String readResource(String path) {
        try (InputStream in = AdminTasks.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Resource " + path + " is missing from the JAR");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }
}

package com.gearup.admin;

import com.gearup.db.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * One-off admin processes (Factor XII).
 *
 * <p>They ship in the same JAR and read the same environment variables as the web server,
 * so they always run against the same code and database as the release they belong to:
 *
 * <pre>
 *   java -jar gearup-backend.jar create-schema
 *   java -jar gearup-backend.jar seed-cars
 * </pre>
 */
public final class AdminTasks {

    private static final Logger log = LoggerFactory.getLogger(AdminTasks.class);

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

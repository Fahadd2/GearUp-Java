package com.gearup;

import com.gearup.admin.AdminTasks;
import com.gearup.api.CarRoutes;
import com.gearup.api.HealthRoutes;
import com.gearup.config.AppConfig;
import com.gearup.config.ConfigException;
import com.gearup.db.Database;
import com.gearup.db.DatabaseException;
import com.gearup.http.GearUpServer;
import com.gearup.http.Router;
import com.gearup.service.CarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Entry point of the GearUp backend. The first argument picks what to run:
 *
 * <pre>
 *   java -jar gearup-backend.jar                  start the web server
 *   java -jar gearup-backend.jar create-schema    admin: create missing tables and types
 *   java -jar gearup-backend.jar seed-cars        admin: add the initial car fleet
 * </pre>
 *
 * Every command reads the same environment variables (Factors III and XII).
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String USAGE = "Available commands: serve (default), create-schema, seed-cars";

    private Main() {
    }

    public static void main(String[] args) {
        String command = args.length > 0 ? args[0] : "serve";
        try {
            AppConfig config = AppConfig.fromEnvironment();
            Database database = new Database(config);
            switch (command) {
                case "serve" -> serve(config, database);
                case "create-schema" -> AdminTasks.createSchema(database);
                case "seed-cars" -> AdminTasks.seedCars(database);
                default -> {
                    log.error("Unknown command '{}'. {}", command, USAGE);
                    System.exit(2);
                }
            }
        } catch (ConfigException e) {
            log.error("Configuration error: {}", e.getMessage());
            System.exit(1);
        } catch (DatabaseException e) {
            log.error("Command '{}' failed: {}", command, e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            log.error("Could not start the server: {}", e.getMessage());
            System.exit(1);
        }
    }

    private static void serve(AppConfig config, Database database) throws IOException {
        long startNanos = System.nanoTime();

        // Wire services to the database and routes to services (plain constructor injection).
        CarService carService = new CarService(database);

        Router router = new Router();
        HealthRoutes.register(router);
        CarRoutes.register(router, carService);

        GearUpServer server = new GearUpServer(config.port(), router);
        // Factor IX: stop gracefully on SIGTERM / Ctrl+C.
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown-hook"));
        server.start();

        long startupMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("GearUp backend listening on port {} (started in {} ms)", config.port(), startupMs);
    }
}

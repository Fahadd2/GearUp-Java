package com.gearup;

import com.gearup.admin.AdminTasks;
import com.gearup.api.AuthRoutes;
import com.gearup.api.CarRoutes;
import com.gearup.api.HealthRoutes;
import com.gearup.auth.AuthGuard;
import com.gearup.auth.PasswordHasher;
import com.gearup.auth.TokenService;
import com.gearup.config.AppConfig;
import com.gearup.config.ConfigException;
import com.gearup.db.Database;
import com.gearup.db.DatabaseException;
import com.gearup.http.GearUpServer;
import com.gearup.http.Router;
import com.gearup.service.AuthService;
import com.gearup.service.CarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;

/**
 * Entry point of the GearUp backend. The first argument picks what to run:
 *
 * <pre>
 *   java -jar gearup-backend.jar                  start the web server
 *   java -jar gearup-backend.jar create-schema    admin: create missing tables and types
 *   java -jar gearup-backend.jar seed-cars        admin: add the initial car fleet
 *   java -jar gearup-backend.jar create-staff &lt;email&gt; &lt;first&gt; &lt;last&gt; &lt;employee|admin&gt;
 * </pre>
 *
 * Every command reads the same environment variables (Factors III and XII).
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String USAGE = "Available commands: serve (default), create-schema, seed-cars, create-staff";

    private Main() {
    }

    public static void main(String[] args) {
        String command = args.length > 0 ? args[0] : "serve";
        List<String> commandArgs = args.length > 1 ? Arrays.asList(args).subList(1, args.length) : List.of();
        try {
            AppConfig config = AppConfig.fromEnvironment();
            Database database = new Database(config);
            switch (command) {
                case "serve" -> serve(config, database);
                case "create-schema" -> AdminTasks.createSchema(database);
                case "seed-cars" -> AdminTasks.seedCars(database);
                case "create-staff" -> AdminTasks.createStaff(database, new PasswordHasher(), commandArgs);
                default -> throw new IllegalArgumentException("Unknown command '" + command + "'. " + USAGE);
            }
        } catch (ConfigException e) {
            log.error("Configuration error: {}", e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException e) {
            log.error("{}", e.getMessage());
            System.exit(2);
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

        // Wire the objects together with plain constructor injection.
        TokenService tokens = new TokenService(config.jwtSecret(), Clock.systemUTC());
        AuthGuard guard = new AuthGuard(tokens);
        AuthService authService = new AuthService(database, new PasswordHasher(), tokens);
        CarService carService = new CarService(database);

        Router router = new Router();
        HealthRoutes.register(router);
        AuthRoutes.register(router, authService, guard);
        CarRoutes.register(router, carService, guard);

        GearUpServer server = new GearUpServer(config.port(), router);
        // Factor IX: stop gracefully on SIGTERM / Ctrl+C.
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown-hook"));
        server.start();

        long startupMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("GearUp backend listening on port {} (started in {} ms)", config.port(), startupMs);
    }
}

package com.gearup;

import com.gearup.api.HealthRoutes;
import com.gearup.config.AppConfig;
import com.gearup.config.ConfigException;
import com.gearup.http.GearUpServer;
import com.gearup.http.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Entry point of the GearUp backend.
 *
 * <pre>
 *   java -jar gearup-backend.jar          start the web server
 * </pre>
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) {
        String command = args.length > 0 ? args[0] : "serve";
        try {
            AppConfig config = AppConfig.fromEnvironment();
            switch (command) {
                case "serve" -> serve(config);
                default -> {
                    log.error("Unknown command '{}'. Available commands: serve", command);
                    System.exit(2);
                }
            }
        } catch (ConfigException e) {
            log.error("Configuration error: {}", e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            log.error("Could not start the server: {}", e.getMessage());
            System.exit(1);
        }
    }

    private static void serve(AppConfig config) throws IOException {
        long startNanos = System.nanoTime();

        Router router = new Router();
        HealthRoutes.register(router);

        GearUpServer server = new GearUpServer(config.port(), router);
        // Factor IX: stop gracefully on SIGTERM / Ctrl+C.
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown-hook"));
        server.start();

        long startupMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("GearUp backend listening on port {} (started in {} ms)", config.port(), startupMs);
    }
}

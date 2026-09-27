import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Concurrency demo: 10 customers try to book the same car for the same dates at the same instant.
 * Exactly one booking must succeed (200) and the other nine must be refused (409 Conflict).
 *
 * <p>Run it against a running GearUp server that has a database, from the backend-java folder:
 * <pre>
 *   java tools/ConcurrentBookingDemo.java                              (http://localhost:8080, CAR-1)
 *   java tools/ConcurrentBookingDemo.java http://localhost:8080 CAR-3
 * </pre>
 *
 * <p>It needs no build: the JDK runs the source file directly. It signs up a fresh demo customer,
 * picks random dates far in the future (so repeated runs do not clash), and uses a
 * {@link CountDownLatch} as a starting gun so all ten requests leave at the same moment.
 * Exits with code 0 if the result is exactly 1 success and 9 conflicts, otherwise 1.
 */
public class ConcurrentBookingDemo {

    private static final int ATTEMPTS = 10;
    private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length > 0 ? args[0] : "http://localhost:8080";
        String carId = args.length > 1 ? args[1] : "CAR-1";

        // HttpClient is thread-safe, so all ten threads share one client.
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        String token = signUpDemoCustomer(http, baseUrl);
        LocalDate start = LocalDate.now().plusDays(ThreadLocalRandom.current().nextInt(200, 2000));
        LocalDate end = start.plusDays(2);
        String body = "{\"car_id\":\"" + carId + "\",\"start_date\":\"" + start + "\",\"end_date\":\"" + end + "\"}";
        System.out.printf("Booking %s from %s to %s with %d simultaneous requests...%n", carId, start, end, ATTEMPTS);

        CountDownLatch startingGun = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
        List<Future<HttpResponse<String>>> results = new ArrayList<>();
        for (int i = 0; i < ATTEMPTS; i++) {
            results.add(pool.submit(() -> {
                startingGun.await(); // every thread waits here until the gun fires
                return http.send(post(baseUrl + "/reservations/create_auth", body, token),
                        HttpResponse.BodyHandlers.ofString());
            }));
        }
        startingGun.countDown(); // fire: all ten requests are released at once

        Map<Integer, Integer> countByStatus = new TreeMap<>();
        for (Future<HttpResponse<String>> result : results) {
            HttpResponse<String> response = result.get();
            countByStatus.merge(response.statusCode(), 1, Integer::sum);
            if (response.statusCode() == 200) {
                System.out.println("  success: " + response.body());
            }
        }
        pool.shutdown();

        System.out.println("Responses by HTTP status: " + countByStatus);
        boolean correct = countByStatus.equals(Map.of(200, 1, 409, ATTEMPTS - 1));
        System.out.println(correct
                ? "PASS: exactly one booking succeeded; the other " + (ATTEMPTS - 1) + " got 409 Conflict."
                : "FAIL: expected {200=1, 409=" + (ATTEMPTS - 1) + "}.");
        System.exit(correct ? 0 : 1);
    }

    /** Creates a throwaway customer (unique email and license) and returns their login token. */
    private static String signUpDemoCustomer(HttpClient http, String baseUrl) throws Exception {
        long unique = System.currentTimeMillis();
        String body = "{\"first_name\":\"Demo\",\"last_name\":\"Customer\","
                + "\"email\":\"demo" + unique + "@example.com\",\"license_no\":\"DEMO" + unique + "\","
                + "\"license_expiry\":\"" + LocalDate.now().plusYears(3) + "\","
                + "\"date_of_birth\":\"1995-01-01\",\"password\":\"demo-password\"}";
        HttpResponse<String> response;
        try {
            response = http.send(post(baseUrl + "/auth/signup", body, null), HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException e) {
            System.err.println("Cannot reach the GearUp server at " + baseUrl + ". Is it running?");
            System.exit(1);
            return null; // not reached
        }

        Matcher token = TOKEN.matcher(response.body());
        if (response.statusCode() != 200 || !token.find()) {
            System.err.println("Could not sign up a demo customer: HTTP " + response.statusCode() + " " + response.body());
            System.err.println("Is the server running with a database (create-schema and seed-cars done)?");
            System.exit(1);
        }
        return token.group(1);
    }

    private static HttpRequest post(String url, String json, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request.build();
    }
}

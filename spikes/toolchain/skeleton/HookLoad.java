import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * T-037: a small load against the hook responder, run as a single-file program (no build). Each
 * request is a fresh SYNTHETIC authorisation at the demo shop that matches nothing, so it walks the
 * whole path (deduplication lookup, view query, call log insert) and leaves one call-log row.
 *
 * <p>Usage: java HookLoad.java http://localhost:8082 1000 8 (url, requests, concurrency)
 */
public final class HookLoad {

    public static void main(String[] args) throws Exception {
        URI url = URI.create(args[0] + "/hook/authorisations");
        int requests = Integer.parseInt(args[1]);
        int concurrency = Integer.parseInt(args[2]);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

        run(client, url, Math.min(50, requests), concurrency); // warm-up: JIT and connection pool
        long[] millis = run(client, url, requests, concurrency);
        Arrays.sort(millis);
        System.out.printf(
                "hook responder: %d requests at concurrency %d: p50 %d ms, p95 %d ms, p99 %d ms, max %d ms, over 300 ms: %d%n",
                millis.length,
                concurrency,
                percentile(millis, 50),
                percentile(millis, 95),
                percentile(millis, 99),
                millis[millis.length - 1],
                Arrays.stream(millis).filter(m -> m > 300).count());
    }

    private static long[] run(HttpClient client, URI url, int requests, int concurrency) throws Exception {
        AtomicInteger failures = new AtomicInteger();
        List<Future<Long>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(concurrency)) {
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> timeOne(client, url, failures)));
            }
        }
        long[] millis = new long[requests];
        for (int i = 0; i < requests; i++) {
            millis[i] = futures.get(i).get();
        }
        if (failures.get() > 0) {
            throw new IllegalStateException(failures.get() + " hook calls failed");
        }
        return millis;
    }

    private static long timeOne(HttpClient client, URI url, AtomicInteger failures) throws Exception {
        String body =
                "{\"processorAuthorisationId\":\"load-" + UUID.randomUUID()
                        + "\",\"amount\":\"12.34\",\"currency\":\"EUR\",\"merchantReference\":\"demo-shop\",\"occurredAt\":\""
                        + Instant.now() + "\"}";
        HttpRequest request =
                HttpRequest.newBuilder(url)
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
        long started = System.nanoTime();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        if (response.statusCode() != 200) {
            failures.incrementAndGet();
        }
        return elapsed;
    }

    private static long percentile(long[] sorted, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }
}

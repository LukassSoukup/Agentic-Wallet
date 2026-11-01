package dev.agenticwallet.spike.backend.support;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * The demo shop, network and processor simulators, started once per test JVM as their own process
 * from the packaged simulators jar (the reactor builds it before this module), exactly as they run
 * on the Pi. In-process they would share this module's classpath and pick up the backend's {@code
 * application.yaml}. SYNTHETIC throughout; the hook responder address points nowhere, so the
 * processor applies its fallback.
 */
public final class TestSimulators {

    private static final Path JAR =
            Path.of("..", "simulators", "target", "simulators-0.0.1-SNAPSHOT-exec.jar")
                    .toAbsolutePath()
                    .normalize();
    private static final Duration START_WITHIN = Duration.ofSeconds(90);

    public static final String URL = start();

    private TestSimulators() {}

    private static String start() {
        if (!Files.exists(JAR)) {
            throw new IllegalStateException(
                    "the simulators jar is missing at "
                            + JAR
                            + "; build the simulators module first");
        }
        try {
            int port = freePort();
            Path log = Path.of("target", "simulators.log").toAbsolutePath();
            Process process =
                    new ProcessBuilder(
                                    ProcessHandle.current().info().command().orElse("java"),
                                    "-Xmx128m",
                                    "-XX:+UseSerialGC",
                                    "-XX:TieredStopAtLevel=1",
                                    "-jar",
                                    JAR.toString(),
                                    "--server.port=" + port,
                                    "--skeleton.processor.hook-responder-url=http://localhost:1",
                                    "--skeleton.processor.hook-timeout=PT1S")
                            .redirectErrorStream(true)
                            .redirectOutput(log.toFile())
                            .start();
            Runtime.getRuntime().addShutdownHook(new Thread(process::destroy));
            String url = "http://localhost:" + port;
            waitUntilHealthy(url, process, log);
            return url;
        } catch (IOException | InterruptedException failed) {
            throw new IllegalStateException("the simulators did not start", failed);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void waitUntilHealthy(String url, Process process, Path log)
            throws IOException, InterruptedException {
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest health =
                HttpRequest.newBuilder(URI.create(url + "/actuator/health"))
                        .timeout(Duration.ofSeconds(2))
                        .build();
        Instant deadline = Instant.now().plus(START_WITHIN);
        while (Instant.now().isBefore(deadline) && process.isAlive()) {
            try {
                HttpResponse<Void> response =
                        http.send(health, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException notYet) {
                // the process is still starting; the deadline bounds the wait
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException(
                "the simulators did not become healthy within " + START_WITHIN + "; see " + log);
    }
}

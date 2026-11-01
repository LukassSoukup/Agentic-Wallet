package dev.agenticwallet.spike.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Checklist (b) and (e): mutual TLS from SSL bundles. The MCP client presents a client certificate
 * from a bundle; the server reloads its certificate on a file swap; and whether a client built from
 * a bundle reloads too is recorded by the last test (it is a finding, not a requirement).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "server.ssl.bundle=server",
            "server.ssl.client-auth=need",
            "spring.ssl.bundle.watch.file.quiet-period=PT1S",
            "spring.ssl.bundle.jks.server.reload-on-update=true",
            "spring.ssl.bundle.jks.client.reload-on-update=true",
            "spring.ssl.bundle.jks.server.keystore.type=PKCS12",
            "spring.ssl.bundle.jks.server.truststore.type=PKCS12",
            "spring.ssl.bundle.jks.client.keystore.type=PKCS12",
            "spring.ssl.bundle.jks.client.truststore.type=PKCS12",
            "spring.ssl.bundle.jks.server.keystore.password=" + TestCertificates.PASSWORD,
            "spring.ssl.bundle.jks.server.truststore.password=" + TestCertificates.PASSWORD,
            "spring.ssl.bundle.jks.client.keystore.password=" + TestCertificates.PASSWORD,
            "spring.ssl.bundle.jks.client.truststore.password=" + TestCertificates.PASSWORD
        })
@Import(MutualTlsBundleTest.WhoAmI.class)
class MutualTlsBundleTest {

    private static final TestCertificates.Material PKI;

    static {
        try {
            PKI = TestCertificates.generate(Files.createTempDirectory("t008-pki"));
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("could not create the synthetic test PKI", e);
        }
    }

    @DynamicPropertySource
    static void bundles(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.ssl.bundle.jks.server.keystore.location", () -> uri(PKI.serverKeyStore()));
        registry.add(
                "spring.ssl.bundle.jks.server.truststore.location", () -> uri(PKI.trustStore()));
        registry.add(
                "spring.ssl.bundle.jks.client.keystore.location", () -> uri(PKI.clientKeyStore()));
        registry.add(
                "spring.ssl.bundle.jks.client.truststore.location", () -> uri(PKI.trustStore()));
    }

    private static String uri(Path path) {
        return path.toUri().toString();
    }

    /**
     * Test-only endpoint: the serial of the client certificate the server saw. The nested annotated
     * controller is registered with the configuration; a bean method as well would map it twice.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class WhoAmI {
        @RestController
        static class Controller {
            @GetMapping("/whoami")
            String whoAmI(HttpServletRequest request) {
                X509Certificate[] chain =
                        (X509Certificate[])
                                request.getAttribute("jakarta.servlet.request.X509Certificate");
                return chain[0].getSerialNumber().toString(16);
            }
        }
    }

    @LocalServerPort int port;
    @Autowired SslBundles bundles;

    @Test
    void mcpClientPresentsAClientCertificateFromABundle() {
        SSLContext clientContext = bundles.getBundle("client").createSslContext();
        var transport =
                HttpClientStreamableHttpTransport.builder("https://localhost:" + port)
                        .endpoint("/mcp")
                        .customizeClient(builder -> builder.sslContext(clientContext))
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
        try (McpSyncClient client =
                McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build()) {
            client.initialize();
            McpSchema.CallToolResult result =
                    client.callTool(
                            McpSchema.CallToolRequest.builder(AgentConfigurationTool.NAME)
                                    .arguments(Map.of())
                                    .build());
            assertThat(result.isError()).isNotEqualTo(true);
        }
    }

    @Test
    void serverReloadsItsCertificateWhenTheFileChanges() throws Exception {
        BigInteger before = serverCertificateSerial();

        TestCertificates.reissue(PKI, "server");

        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(serverCertificateSerial()).isNotEqualTo(before));
    }

    @Test
    void recordsWhetherABundleBackedClientReloadsItsCertificate() throws Exception {
        AtomicInteger updates = new AtomicInteger();
        bundles.addBundleUpdateHandler("client", bundle -> updates.incrementAndGet());
        RestClient client = restClientFrom(bundles.getBundle("client"));
        String before = client.get().uri("/whoami").retrieve().body(String.class);

        TestCertificates.reissue(PKI, "client");
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(updates.get()).isPositive());

        String after = client.get().uri("/whoami").retrieve().body(String.class);
        String rebuilt =
                restClientFrom(bundles.getBundle("client"))
                        .get()
                        .uri("/whoami")
                        .retrieve()
                        .body(String.class);

        // Characterisation (T-008 finding): the client built before the swap still presents the old
        // certificate (the JDK client may also reuse its pooled TLS connection, which this test
        // does
        // not separate); a client rebuilt from the bundle presents the new one.
        assertThat(after).isEqualTo(before);
        assertThat(rebuilt).isNotEqualTo(before);
    }

    private RestClient restClientFrom(SslBundle bundle) {
        return RestClient.builder()
                .baseUrl("https://localhost:" + port)
                .requestFactory(
                        ClientHttpRequestFactoryBuilder.jdk()
                                .build(HttpClientSettings.ofSslBundle(bundle)))
                .build();
    }

    private BigInteger serverCertificateSerial() throws IOException {
        SSLContext context = bundles.getBundle("client").createSslContext();
        try (SSLSocket socket =
                (SSLSocket) context.getSocketFactory().createSocket("localhost", port)) {
            socket.startHandshake();
            X509Certificate peer = (X509Certificate) socket.getSession().getPeerCertificates()[0];
            return peer.getSerialNumber();
        }
    }
}

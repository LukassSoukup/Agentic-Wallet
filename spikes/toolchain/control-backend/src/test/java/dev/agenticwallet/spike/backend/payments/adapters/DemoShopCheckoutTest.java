package dev.agenticwallet.spike.backend.payments.adapters;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.agenticwallet.spike.backend.payments.PaymentsProperties;
import dev.agenticwallet.spike.core.payments.Checkout;
import dev.agenticwallet.spike.core.payments.Credential;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * The adapter translates every failure into the port's modes (ADR-0009 B3, B6): a merchant that
 * answers after the read timeout is {@code NoAnswer} (UNKNOWN, never FAILED), a 4xx is refused with
 * a code, a 5xx is {@code NoAnswer}, and an unknown session is {@code NotFound}.
 */
class DemoShopCheckoutTest {

    private HttpServer merchant;
    private DemoShopCheckout checkout;

    @BeforeEach
    void startMerchant() throws IOException {
        merchant = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        merchant.createContext(
                "/sessions/late/complete",
                exchange ->
                        answerAfter(
                                exchange,
                                1500,
                                200,
                                "{\"sessionId\":\"late\",\"state\":\"COMPLETED\",\"authorisationId\":\"a\"}"));
        merchant.createContext(
                "/sessions/taken/complete",
                exchange ->
                        answerAfter(
                                exchange,
                                0,
                                409,
                                "{\"type\":\"urn:x:session-already-completed\"}"));
        merchant.createContext(
                "/sessions/broken/complete", exchange -> answerAfter(exchange, 0, 500, "{}"));
        merchant.createContext("/sessions/gone", exchange -> answerAfter(exchange, 0, 404, "{}"));
        merchant.start();
        String url = "http://localhost:" + merchant.getAddress().getPort();
        PaymentsProperties properties =
                new PaymentsProperties(
                        url,
                        url,
                        Duration.ofMillis(500),
                        Duration.ofMillis(500),
                        Duration.ofMillis(300),
                        Duration.ofMillis(100),
                        Duration.ofSeconds(1),
                        Duration.ofMinutes(15));
        checkout = new DemoShopCheckout(properties, RestClient.builder());
    }

    @AfterEach
    void stopMerchant() {
        merchant.stop(0);
    }

    @Test
    void aLateAnswerIsNoAnswerNeverACompletionOrARefusal() {
        Checkout.CompletionResult result = checkout.complete("late", new Credential("syn"), "key");
        assertThat(result).isInstanceOf(Checkout.CompletionResult.NoAnswer.class);
    }

    @Test
    void translatesRefusalsServerErrorsAndUnknownSessions() {
        assertThat(checkout.complete("taken", new Credential("syn"), "key"))
                .isEqualTo(new Checkout.CompletionResult.Refused("session-already-completed"));
        assertThat(checkout.complete("broken", new Credential("syn"), "key"))
                .isInstanceOf(Checkout.CompletionResult.NoAnswer.class);
        assertThat(checkout.status("gone")).isInstanceOf(Checkout.SessionStatus.NotFound.class);
    }

    private static void answerAfter(
            HttpExchange exchange, long delayMillis, int status, String body) throws IOException {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders()
                .add(
                        "Content-Type",
                        status >= 400 ? "application/problem+json" : "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}

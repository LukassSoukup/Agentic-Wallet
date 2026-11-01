package dev.agenticwallet.spike.backend.support;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL 18 container for the whole test JVM (Testcontainers 2.0, aarch64 image). Started
 * once; Ryuk removes it when the JVM ends.
 */
public final class TestPostgres {

    public static final String IMAGE = "postgres:18-alpine";

    public static final PostgreSQLContainer INSTANCE =
            new PostgreSQLContainer(IMAGE).withCommand("postgres", "-c", "transaction_timeout=5s");

    static {
        INSTANCE.start();
    }

    private TestPostgres() {}
}

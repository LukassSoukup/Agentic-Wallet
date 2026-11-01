package dev.agenticwallet.spike.hook;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** One PostgreSQL 18 container for the hook's test JVM; Ryuk removes it when the JVM ends. */
final class HookTestPostgres {

    static final PostgreSQLContainer INSTANCE =
            new PostgreSQLContainer("postgres:18-alpine")
                    .withCommand("postgres", "-c", "transaction_timeout=5s");

    static {
        INSTANCE.start();
    }

    private HookTestPostgres() {}
}

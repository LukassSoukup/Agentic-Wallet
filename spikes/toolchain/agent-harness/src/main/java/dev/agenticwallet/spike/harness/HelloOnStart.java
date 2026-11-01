package dev.agenticwallet.spike.harness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** With the {@code hello} profile the harness says hello to the gateway once at start-up. */
@Component
@Profile("hello")
class HelloOnStart implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HelloOnStart.class);

    private final ChatHello hello;

    HelloOnStart(ChatHello hello) {
        this.hello = hello;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("OpenRouter hello reply length={} ", hello.hello().length());
    }
}

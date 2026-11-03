package dev.agenticwallet.spike.hook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HookResponderApplication {

    public static void main(String[] args) {
        SpringApplication.run(HookResponderApplication.class, args);
    }
}

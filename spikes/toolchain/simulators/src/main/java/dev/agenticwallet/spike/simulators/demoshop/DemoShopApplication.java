package dev.agenticwallet.spike.simulators.demoshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** The simulators in one SYNTHETIC process: demo shop, network credentials and the processor. */
@SpringBootApplication(scanBasePackages = "dev.agenticwallet.spike.simulators")
@ConfigurationPropertiesScan("dev.agenticwallet.spike.simulators")
public class DemoShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoShopApplication.class, args);
    }
}

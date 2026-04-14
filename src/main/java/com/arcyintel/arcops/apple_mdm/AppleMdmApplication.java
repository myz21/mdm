package com.arcyintel.arcops.apple_mdm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan(basePackages = {"com.arcyintel.arcops.apple_mdm.domains"})
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties
public class AppleMdmApplication {

    public static void main(String[] args) {
        // write the current directory to the console
        System.out.println("Current working directory: " + System.getProperty("user.dir"));
        SpringApplication.run(AppleMdmApplication.class, args);
    }

}

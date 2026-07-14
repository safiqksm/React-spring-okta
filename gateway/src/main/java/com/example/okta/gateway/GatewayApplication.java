package com.example.okta.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GatewayApplication {
    public static void main(String[] arguments) {
        SpringApplication.run(GatewayApplication.class, arguments);
    }
}


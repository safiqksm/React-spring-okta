package com.example.okta.gateway;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@EnableScheduling
public class GatewayApplication {
    public static void main(String[] arguments) {
        SpringApplication.run(GatewayApplication.class, arguments);
    }

    /**
     * Pinned to HTTP/1.1 with explicit timeouts: the JDK HttpClient's default HTTP/2
     * negotiation has been observed to hang indefinitely against some reverse-proxy
     * setups (e.g. Render's edge in front of a downstream service), with no exception
     * ever thrown -- just a stalled request until an upstream edge times out and
     * returns its own 502, long after our own logs go silent.
     */
    @Bean
    RestClient restClient(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(25));
        return builder.requestFactory(requestFactory).build();
    }
}


package com.example.okta.servicetwo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ServiceTwoControllerTest {
    private final ServiceTwoController controller = new ServiceTwoController();

    @Test
    void returnsServiceTwoResponse() {
        Map<String, Object> response = controller.ping(testJwt());

        assertThat(response).containsEntry("message", "Service 2 responded");
    }

    private Jwt testJwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("test-user")
                .build();
    }
}

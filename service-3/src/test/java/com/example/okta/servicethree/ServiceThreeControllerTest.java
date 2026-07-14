package com.example.okta.servicethree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ServiceThreeControllerTest {
    private final ServiceThreeController controller = new ServiceThreeController();

    @Test
    void returnsServiceThreeResponse() {
        Map<String, Object> response = controller.ping(testJwt());

        assertThat(response).containsEntry("message", "Service 3 responded");
    }

    private Jwt testJwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("test-user")
                .build();
    }
}

package com.example.okta.serviceone;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;

class ApiControllerTest {
    private final ApiController controller = new ApiController(RestClient.create(), "http://localhost:8082");

    @Test
    void returnsSimulatedAddFactorMessage() {
        Map<String, String> response = controller.addFactor(
                new ApiController.FactorActionRequest("Security key"), testJwt());

        assertThat(response).containsEntry("message", "Factor added (simulated): Security key");
    }

    @Test
    void returnsSimulatedRemoveFactorMessage() {
        Map<String, String> response = controller.removeFactor(
                new ApiController.FactorActionRequest("Phone"), testJwt());

        assertThat(response).containsEntry("message", "Factor removed (simulated): Phone");
    }

    private Jwt testJwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("test-user")
                .build();
    }
}

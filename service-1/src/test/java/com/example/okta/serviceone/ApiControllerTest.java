package com.example.okta.serviceone;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;

class ApiControllerTest {
    private final ApiController controller = new ApiController(
            RestClient.create(),
            new ServiceTwoTokenProvider(RestClient.create(), "", "", "", "", ""),
            new ServiceThreeTokenProvider(RestClient.create(), "", "", "", "", ""),
            "http://localhost:8082",
            "http://localhost:8083");

    private Jwt testJwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("test-user")
                .build();
    }
}

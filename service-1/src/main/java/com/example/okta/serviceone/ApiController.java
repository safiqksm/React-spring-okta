package com.example.okta.serviceone;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api")
public class ApiController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiController.class);
    private final RestClient restClient;
    private final String serviceTwoUrl;

    public ApiController(RestClient restClient, @Value("${service-two.url}") String serviceTwoUrl) {
        this.restClient = restClient;
        this.serviceTwoUrl = serviceTwoUrl;
    }

    @GetMapping("/service-1/hello")
    Map<String, Object> hello(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @AuthenticationPrincipal Jwt jwt) {
        JwtDebugMetadata.logValidation(LOGGER, "service-1", jwt);
        Map<?, ?> serviceTwoResponse = restClient.get()
                .uri(serviceTwoUrl + "/api/service-2/ping")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .body(Map.class);
        return Map.of(
                "message", "Gateway -> Service 1 -> " + serviceTwoResponse.get("message"),
                "jwt", JwtDebugMetadata.from(jwt),
                "service2Jwt", serviceTwoResponse.get("jwt"));
    }

    @PostMapping("/settings/factors/add")
    Map<String, String> addFactor(@RequestBody FactorActionRequest request, @AuthenticationPrincipal Jwt jwt) {
        JwtDebugMetadata.logValidation(LOGGER, "service-1-settings", jwt);
        return simulatedFactorResponse("added", request.factorType());
    }

    @PostMapping("/settings/factors/remove")
    Map<String, String> removeFactor(@RequestBody FactorActionRequest request, @AuthenticationPrincipal Jwt jwt) {
        JwtDebugMetadata.logValidation(LOGGER, "service-1-settings", jwt);
        return simulatedFactorResponse("removed", request.factorType());
    }

    private Map<String, String> simulatedFactorResponse(String action, String factorType) {
        return Map.of("message", "Factor " + action + " (simulated): " + factorType);
    }

    record FactorActionRequest(String factorType) { }
}

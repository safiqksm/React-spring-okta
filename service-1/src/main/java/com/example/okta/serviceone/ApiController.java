package com.example.okta.serviceone;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api")
public class ApiController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiController.class);
    private final RestClient restClient;
    private final String serviceTwoUrl;
    private final String serviceThreeUrl;

    private final ServiceTwoTokenProvider serviceTwoTokenProvider;
    private final ServiceThreeTokenProvider serviceThreeTokenProvider;

    public ApiController(
            RestClient restClient,
            ServiceTwoTokenProvider serviceTwoTokenProvider,
            ServiceThreeTokenProvider serviceThreeTokenProvider,
            @Value("${service-two.url}") String serviceTwoUrl,
            @Value("${service-three.url}") String serviceThreeUrl) {
        this.restClient = restClient;
        this.serviceTwoTokenProvider = serviceTwoTokenProvider;
        this.serviceThreeTokenProvider = serviceThreeTokenProvider;
        this.serviceTwoUrl = serviceTwoUrl;
        this.serviceThreeUrl = serviceThreeUrl;
    }

    @GetMapping("/service-1/hello")
    Map<String, Object> hello(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        JwtDebugMetadata.logValidation(LOGGER, "service-1", jwt);
        TokenResult tokenResult = serviceTwoTokenProvider.accessToken();

        ResponseEntity<Map> serviceTwoEntity = restClient.get()
                .uri(serviceTwoUrl + "/api/service-2/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenResult.accessToken())
                .retrieve()
                .toEntity(Map.class);
        Map<?, ?> serviceTwoResponse = serviceTwoEntity.getBody();

        Map<String, Object> hops = new LinkedHashMap<>();
        hops.put("gatewayToService1", gatewayToServiceOneHop(request));
        hops.put("service1ToOkta", Map.of("request", tokenResult.request(), "response", tokenResult.response()));
        hops.put("service1ToService2", Map.of(
                "request", Map.of(
                        "method", "GET",
                        "url", serviceTwoUrl + "/api/service-2/ping",
                        "headers", Map.of("Authorization", "Bearer " + tokenResult.accessToken())),
                "response", Map.of("status", serviceTwoEntity.getStatusCode().value(), "body", serviceTwoResponse)));

        return Map.of(
                "message", "Gateway -> Service 1 -> " + serviceTwoResponse.get("message"),
                "jwt", JwtDebugMetadata.from(jwt),
                "service2Jwt", serviceTwoResponse.get("jwt"),
                "hops", hops);
    }

    @GetMapping("/service-1/ping")
    Map<String, Object> ping(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        JwtDebugMetadata.logValidation(LOGGER, "service-1", jwt);
        return Map.of(
                "message", "Service 1 responded",
                "jwt", JwtDebugMetadata.from(jwt),
                "hops", Map.of("gatewayToService1", gatewayToServiceOneHop(request)));
    }

    @GetMapping("/service-1/obo-hello")
    Map<String, Object> oboHello(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        JwtDebugMetadata.logValidation(LOGGER, "service-1", jwt);
        TokenResult tokenResult = serviceThreeTokenProvider.exchange(jwt.getTokenValue());

        ResponseEntity<Map> serviceThreeEntity = restClient.get()
                .uri(serviceThreeUrl + "/api/service-3/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenResult.accessToken())
                .retrieve()
                .toEntity(Map.class);
        Map<?, ?> serviceThreeResponse = serviceThreeEntity.getBody();

        Map<String, Object> hops = new LinkedHashMap<>();
        hops.put("gatewayToService1", gatewayToServiceOneHop(request));
        hops.put("service1ToOkta", Map.of("request", tokenResult.request(), "response", tokenResult.response()));
        hops.put("service1ToService3", Map.of(
                "request", Map.of(
                        "method", "GET",
                        "url", serviceThreeUrl + "/api/service-3/ping",
                        "headers", Map.of("Authorization", "Bearer " + tokenResult.accessToken())),
                "response", Map.of("status", serviceThreeEntity.getStatusCode().value(), "body", serviceThreeResponse)));

        return Map.of(
                "message", "Gateway -> Service 1 -> " + serviceThreeResponse.get("message") + " (on-behalf-of)",
                "jwt", JwtDebugMetadata.from(jwt),
                "service3Jwt", serviceThreeResponse.get("jwt"),
                "hops", hops);
    }

    private Map<String, Object> gatewayToServiceOneHop(HttpServletRequest request) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Authorization", request.getHeader("Authorization"));
        if (request.getHeader("DPoP") != null) {
            headers.put("DPoP", request.getHeader("DPoP"));
        }
        Map<String, Object> requestSummary = new LinkedHashMap<>();
        requestSummary.put("method", request.getMethod());
        requestSummary.put("url", request.getRequestURL().toString());
        requestSummary.put("headers", headers);
        return Map.of("request", requestSummary, "response", Map.of("status", 200));
    }
}

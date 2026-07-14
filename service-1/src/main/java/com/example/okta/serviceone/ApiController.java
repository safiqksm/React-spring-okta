package com.example.okta.serviceone;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
    Map<String, Object> hello(@AuthenticationPrincipal Jwt jwt) {
        JwtDebugMetadata.logValidation(LOGGER, "service-1", jwt);
        Map<?, ?> serviceTwoResponse = restClient.get()
                .uri(serviceTwoUrl + "/api/service-2/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTwoTokenProvider.accessToken())
                .retrieve()
                .body(Map.class);
        return Map.of(
                "message", "Gateway -> Service 1 -> " + serviceTwoResponse.get("message"),
                "jwt", JwtDebugMetadata.from(jwt),
                "service2Jwt", serviceTwoResponse.get("jwt"));
    }

    @GetMapping("/service-1/ping")
    Map<String, Object> ping(@AuthenticationPrincipal Jwt jwt) {
        JwtDebugMetadata.logValidation(LOGGER, "service-1", jwt);
        return Map.of("message", "Service 1 responded", "jwt", JwtDebugMetadata.from(jwt));
    }

    @GetMapping("/service-1/obo-hello")
    Map<String, Object> oboHello(@AuthenticationPrincipal Jwt jwt) {
        JwtDebugMetadata.logValidation(LOGGER, "service-1", jwt);
        String exchangedToken = serviceThreeTokenProvider.exchange(jwt.getTokenValue());
        Map<?, ?> serviceThreeResponse = restClient.get()
                .uri(serviceThreeUrl + "/api/service-3/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + exchangedToken)
                .retrieve()
                .body(Map.class);
        return Map.of(
                "message", "Gateway -> Service 1 -> " + serviceThreeResponse.get("message") + " (on-behalf-of)",
                "jwt", JwtDebugMetadata.from(jwt),
                "service3Jwt", serviceThreeResponse.get("jwt"));
    }

}

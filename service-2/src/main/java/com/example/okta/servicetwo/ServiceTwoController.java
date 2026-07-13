package com.example.okta.servicetwo;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service-2")
public class ServiceTwoController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTwoController.class);

    @GetMapping("/ping")
    Map<String, Object> ping(@AuthenticationPrincipal Jwt jwt) {
        JwtDebugMetadata.logValidation(LOGGER, "service-2", jwt);
        return Map.of("message", "Service 2 responded", "jwt", JwtDebugMetadata.from(jwt));
    }
}

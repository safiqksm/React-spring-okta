package com.example.okta.servicethree;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service-3")
public class ServiceThreeController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceThreeController.class);

    @GetMapping("/ping")
    Map<String, Object> ping(@AuthenticationPrincipal Jwt jwt) {
        JwtDebugMetadata.logValidation(LOGGER, "service-3", jwt);
        return Map.of("message", "Service 3 responded", "jwt", JwtDebugMetadata.from(jwt));
    }
}

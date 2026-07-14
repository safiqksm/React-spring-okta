package com.example.okta.servicetwo;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/factors")
public class SettingsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(SettingsController.class);

    @PostMapping("/add")
    Map<String, String> addFactor(@RequestBody FactorActionRequest request, @AuthenticationPrincipal Jwt jwt) {
        JwtDebugMetadata.logValidation(LOGGER, "service-2-settings", jwt);
        return response("added", request.factorType());
    }

    @PostMapping("/remove")
    Map<String, String> removeFactor(@RequestBody FactorActionRequest request, @AuthenticationPrincipal Jwt jwt) {
        JwtDebugMetadata.logValidation(LOGGER, "service-2-settings", jwt);
        return response("removed", request.factorType());
    }

    private Map<String, String> response(String action, String factorType) {
        return Map.of("message", "Factor " + action + " (simulated): " + factorType);
    }

    record FactorActionRequest(String factorType) { }
}

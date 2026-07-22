package com.example.okta.gateway;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {
    private final String extraAllowedOrigin;

    CorsConfig(@Value("${app.cors.extra-allowed-origin:}") String extraAllowedOrigin) {
        this.extraAllowedOrigin = extraAllowedOrigin;
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
        if (!extraAllowedOrigin.isBlank()) {
            allowedOrigins.add(extraAllowedOrigin);
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "DPoP", "X-Correlation-Id"));
        configuration.setExposedHeaders(List.of(
                "WWW-Authenticate", "X-Authentication-Failure", "X-DPoP-Validation", "X-Session-Revoked"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

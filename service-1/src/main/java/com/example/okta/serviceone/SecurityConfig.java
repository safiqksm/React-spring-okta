package com.example.okta.serviceone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Configuration
public class SecurityConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);
    private final BearerTokenAuthenticationEntryPoint authenticationEntryPoint =
            new BearerTokenAuthenticationEntryPoint();

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> {
                    LOGGER.warn("jwt_validation_failure component=service-1 path={} exception={} message={}",
                            request.getRequestURI(), exception.getClass().getSimpleName(), exception.getMessage());
                    authenticationEntryPoint.commence(request, response, exception);
                }))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint((request, response, exception) -> {
                            LOGGER.warn("resource_server_authentication_failure component=service-1 path={} exception={} message={}",
                                    request.getRequestURI(), exception.getClass().getSimpleName(), exception.getMessage());
                            response.setStatus(401);
                            response.setHeader(
                                    "X-Authentication-Failure",
                                    "service-1: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
                        })
                        .jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    OncePerRequestFilter inboundAuthorizationDebugFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain filterChain) throws ServletException, IOException {
                String authorization = request.getHeader("Authorization");
                if (authorization != null && request.getRequestURI().startsWith("/api/")) {
                    String[] parts = authorization.split(" ", 2);
                    String scheme = parts[0];
                    String token = parts.length == 2 ? parts[1] : "";
                    LOGGER.debug("inbound_authorization component=service-1 path={} scheme={} jwtSegments={} tokenFingerprint={}",
                            request.getRequestURI(), scheme, token.split("\\.", -1).length, fingerprint(token));
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    private static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            return "unavailable";
        }
    }
}

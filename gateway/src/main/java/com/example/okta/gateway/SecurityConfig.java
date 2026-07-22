package com.example.okta.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);
    private final BearerTokenAuthenticationEntryPoint authenticationEntryPoint =
            new BearerTokenAuthenticationEntryPoint();

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DpopProofFilter dpopProofFilter,
            RevocationCheckFilter revocationCheckFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.OPTIONS).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/global-token-revocation").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> {
                    LOGGER.debug("jwt_validation_failure component=gateway path={} reason={}",
                            request.getRequestURI(), exception.getClass().getSimpleName());
                    authenticationEntryPoint.commence(request, response, exception);
                }))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setHeader(
                                    "X-Authentication-Failure",
                                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
                        })
                        .jwt(Customizer.withDefaults()))
                .addFilterAfter(dpopProofFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(revocationCheckFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }
}

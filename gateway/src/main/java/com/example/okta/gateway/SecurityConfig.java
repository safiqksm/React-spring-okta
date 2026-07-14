package com.example.okta.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.web.server.BearerTokenServerAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);
    private final BearerTokenServerAuthenticationEntryPoint authenticationEntryPoint =
            new BearerTokenServerAuthenticationEntryPoint();

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, DpopProofWebFilter dpopProofWebFilter) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyExchange().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((exchange, exception) -> {
                    LOGGER.debug("jwt_validation_failure component=gateway path={} reason={}",
                            exchange.getRequest().getPath(), exception.getClass().getSimpleName());
                    return authenticationEntryPoint.commence(exchange, exception);
                }))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenConverter(dpopAwareTokenConverter())
                        .authenticationFailureHandler((webFilterExchange, exception) -> {
                            webFilterExchange.getExchange().getResponse().setStatusCode(
                                    org.springframework.http.HttpStatus.UNAUTHORIZED);
                            webFilterExchange.getExchange().getResponse().getHeaders().set(
                                    "X-Authentication-Failure",
                                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
                            return webFilterExchange.getExchange().getResponse().setComplete();
                        })
                        .authenticationEntryPoint((exchange, exception) -> {
                            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                            exchange.getResponse().getHeaders().set(
                                    "X-Authentication-Failure",
                                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
                            return exchange.getResponse().setComplete();
                        })
                        .jwt(Customizer.withDefaults()))
                .addFilterAfter(dpopProofWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    private ServerAuthenticationConverter dpopAwareTokenConverter() {
        ServerBearerTokenAuthenticationConverter bearerTokenConverter = new ServerBearerTokenAuthenticationConverter();
        return exchange -> {
            String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authorization != null && authorization.startsWith("DPoP ")) {
                return reactor.core.publisher.Mono.just(
                        new BearerTokenAuthenticationToken(authorization.substring("DPoP ".length())));
            }
            return bearerTokenConverter.convert(exchange);
        };
    }
}

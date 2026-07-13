package com.example.okta.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(801)
public class JwtDebugWebFilter implements WebFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtDebugWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .doOnNext(authentication -> logValidatedToken(exchange, authentication.getToken()))
                .then(chain.filter(exchange));
    }

    private void logValidatedToken(ServerWebExchange exchange, Jwt jwt) {
        LOGGER.debug(
                "jwt_validation_success component=gateway path={} subject={} issuer={} expiresAt={} scopes={} tokenFingerprint={}",
                exchange.getRequest().getPath(),
                jwt.getSubject(),
                jwt.getIssuer(),
                jwt.getExpiresAt(),
                scopes(jwt),
                fingerprint(jwt));
    }

    private List<String> scopes(Jwt jwt) {
        List<String> scopedClaims = jwt.getClaimAsStringList("scp");
        if (scopedClaims != null) {
            return scopedClaims;
        }
        String scope = jwt.getClaimAsString("scope");
        return scope == null ? List.of() : List.of(scope.split(" "));
    }

    private String fingerprint(Jwt jwt) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(jwt.getTokenValue().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

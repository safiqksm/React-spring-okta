package com.example.okta.gateway;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Rejects requests whose access token was issued before its subject was last revoked
 * via Global Token Revocation. Runs after normal JWT authentication already passed —
 * this is the extra check that fakes revocation for an otherwise-stateless JWT.
 */
@Component
public class RevocationCheckWebFilter implements WebFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RevocationCheckWebFilter.class);
    private final RevocationDenyList denyList;

    RevocationCheckWebFilter(RevocationDenyList denyList) {
        this.denyList = denyList;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .flatMap(authentication -> checkRevocation(exchange, authentication))
                .defaultIfEmpty(true)
                .flatMap(valid -> valid ? chain.filter(exchange) : reject(exchange));
    }

    private Mono<Boolean> checkRevocation(ServerWebExchange exchange, JwtAuthenticationToken authentication) {
        String subject = authentication.getToken().getSubject();
        Instant issuedAt = authentication.getToken().getIssuedAt();
        if (issuedAt != null && denyList.isRevoked(subject, issuedAt)) {
            LOGGER.debug("revocation_check_failure component=gateway path={} subject={}",
                    exchange.getRequest().getPath(), subject);
            return Mono.just(false);
        }
        return Mono.just(true);
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"session_revoked\"");
        exchange.getResponse().getHeaders().set("X-Session-Revoked", "true");
        return exchange.getResponse().setComplete();
    }
}

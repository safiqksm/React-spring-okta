package com.example.okta.gateway;

import com.nimbusds.jose.jwk.JWK;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.DPoPProofContext;
import org.springframework.security.oauth2.jwt.DPoPProofJwtDecoderFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class DpopProofWebFilter implements WebFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DpopProofWebFilter.class);
    private final DPoPProofJwtDecoderFactory decoderFactory = new DPoPProofJwtDecoderFactory();
    private final boolean required;
    private final boolean debugHeaders;

    DpopProofWebFilter(
            @Value("${app.dpop.required:false}") boolean required,
            @Value("${app.dpop.debug-headers:false}") boolean debugHeaders) {
        this.required = required;
        this.debugHeaders = debugHeaders;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .flatMap(authentication -> validate(exchange, authentication.getToken()))
                .defaultIfEmpty(true)
                .flatMap(valid -> valid ? chain.filter(exchange) : exchange.getResponse().setComplete());
    }

    private Mono<Boolean> validate(ServerWebExchange exchange, Jwt accessToken) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        boolean dpopAuthorization = authorization != null && authorization.startsWith("DPoP ");
        if (!dpopAuthorization) {
            return required ? reject(exchange, "missing_dpop_authorization") : Mono.just(true);
        }

        String proof = exchange.getRequest().getHeaders().getFirst("DPoP");
        if (proof == null || proof.isBlank()) {
            return reject(exchange, "missing_dpop_proof");
        }
        return Mono.fromCallable(() -> validateProof(exchange, accessToken, proof))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(exception -> reject(exchange, exception.getClass().getSimpleName()));
    }

    private boolean validateProof(ServerWebExchange exchange, Jwt accessToken, String proof) throws Exception {
        URI requestUri = exchange.getRequest().getURI();
        String targetUri = UriComponentsBuilder.fromUri(requestUri).replaceQuery(null).build().toUriString();
        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.DPOP,
                accessToken.getTokenValue(),
                Instant.now(),
                accessToken.getExpiresAt());
        DPoPProofContext context = DPoPProofContext.withDPoPProof(proof)
                .method(exchange.getRequest().getMethod().name())
                .targetUri(targetUri)
                .accessToken(token)
                .build();
        Jwt decodedProof = decoderFactory.createDecoder(context).decode(proof);
        Map<String, Object> confirmation = accessToken.getClaimAsMap("cnf");
        String expectedThumbprint = confirmation == null ? null : (String) confirmation.get("jkt");
        Object jwk = decodedProof.getHeaders().get("jwk");
        String actualThumbprint = JWK.parse((Map<String, Object>) jwk).computeThumbprint().toString();
        if (expectedThumbprint == null || !expectedThumbprint.equals(actualThumbprint)) {
            throw new IllegalArgumentException("dpop_jkt_mismatch");
        }
        LOGGER.debug("dpop_validation_success component=gateway path={} proofId={}",
                exchange.getRequest().getPath(), decodedProof.getId());
        return true;
    }

    private Mono<Boolean> reject(ServerWebExchange exchange, String reason) {
        LOGGER.debug("dpop_validation_failure component=gateway path={} reason={}",
                exchange.getRequest().getPath(), reason);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "DPoP error=\"invalid_dpop_proof\"");
        if (debugHeaders) {
            exchange.getResponse().getHeaders().set("X-DPoP-Validation", reason);
        }
        return Mono.just(false);
    }
}

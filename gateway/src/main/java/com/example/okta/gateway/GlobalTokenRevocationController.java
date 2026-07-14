package com.example.okta.gateway;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Okta Global Token Revocation (Universal Logout) endpoint. Okta's cloud POSTs here
 * with a signed JWT (typ=global-token-revocation+jwt, aud=this endpoint's own URL —
 * not the SPA's API audience) proving the call is really from Okta, and a JSON body
 * naming which subject to revoke — never a token value. See PLAN.md Phase 6.
 */
@RestController
public class GlobalTokenRevocationController {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalTokenRevocationController.class);
    private static final String EXPECTED_TYP = "global-token-revocation+jwt";

    private final ReactiveJwtDecoder jwtDecoder;
    private final RevocationDenyList denyList;

    GlobalTokenRevocationController(
            RevocationDenyList denyList,
            @Value("${app.revocation.issuer}") String issuer,
            @Value("${app.revocation.endpoint-url}") String endpointUrl) {
        this.denyList = denyList;
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withIssuerLocation(issuer).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(endpointUrl)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "aud does not match this endpoint", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), audienceValidator));
        this.jwtDecoder = decoder;
    }

    @PostMapping("/global-token-revocation")
    Mono<ResponseEntity<Void>> revoke(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            LOGGER.debug("gtr_rejected component=gateway reason=missing_bearer_scheme");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        String token = authorization.substring("Bearer ".length());
        return jwtDecoder.decode(token)
                .flatMap(jwt -> handleValidatedJwt(jwt, body))
                .onErrorResume(JwtException.class, exception -> {
                    LOGGER.debug("gtr_rejected component=gateway reason={} message={}",
                            exception.getClass().getSimpleName(), exception.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                });
    }

    private Mono<ResponseEntity<Void>> handleValidatedJwt(Jwt jwt, Map<String, Object> body) {
        Object typ = jwt.getHeaders().get("typ");
        if (!EXPECTED_TYP.equals(typ)) {
            LOGGER.debug("gtr_rejected component=gateway reason=unexpected_typ typ={}", typ);
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        String subject = extractSubject(body);
        if (subject == null) {
            LOGGER.debug("gtr_rejected component=gateway reason=malformed_sub_id");
            return Mono.just(ResponseEntity.badRequest().build());
        }
        denyList.revoke(subject);
        LOGGER.debug("gtr_accepted component=gateway subject={}", subject);
        return Mono.just(ResponseEntity.noContent().build());
    }

    @SuppressWarnings("unchecked")
    private static String extractSubject(Map<String, Object> body) {
        Object subIdObject = body.get("sub_id");
        if (!(subIdObject instanceof Map)) {
            return null;
        }
        Object sub = ((Map<String, Object>) subIdObject).get("sub");
        return sub instanceof String ? (String) sub : null;
    }
}

package com.example.okta.gateway;

import com.nimbusds.jose.jwk.JWK;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.DPoPProofContext;
import org.springframework.security.oauth2.jwt.DPoPProofJwtDecoderFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class DpopProofFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DpopProofFilter.class);
    private final DPoPProofJwtDecoderFactory decoderFactory = new DPoPProofJwtDecoderFactory();
    private final boolean required;
    private final boolean debugHeaders;

    DpopProofFilter(
            @Value("${app.dpop.required:false}") boolean required,
            @Value("${app.dpop.debug-headers:false}") boolean debugHeaders) {
        this.required = required;
        this.debugHeaders = debugHeaders;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication
                && !validate(request, response, jwtAuthentication.getToken())) {
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean validate(HttpServletRequest request, HttpServletResponse response, Jwt accessToken)
            throws IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean dpopAuthorization = authorization != null && authorization.startsWith("DPoP ");
        if (!dpopAuthorization) {
            if (required) {
                reject(request, response, "missing_dpop_authorization");
                return false;
            }
            return true;
        }

        String proof = request.getHeader("DPoP");
        if (proof == null || proof.isBlank()) {
            reject(request, response, "missing_dpop_proof");
            return false;
        }
        try {
            return validateProof(request, response, accessToken, proof);
        } catch (Exception exception) {
            reject(request, response, exception.getClass().getSimpleName());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean validateProof(HttpServletRequest request, HttpServletResponse response, Jwt accessToken, String proof)
            throws Exception {
        URI requestUri = URI.create(request.getRequestURL().toString());
        String targetUri = UriComponentsBuilder.fromUri(requestUri).replaceQuery(null).build().toUriString();
        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.DPOP,
                accessToken.getTokenValue(),
                Instant.now(),
                accessToken.getExpiresAt());
        DPoPProofContext context = DPoPProofContext.withDPoPProof(proof)
                .method(request.getMethod())
                .targetUri(targetUri)
                .accessToken(token)
                .build();
        Jwt decodedProof = decoderFactory.createDecoder(context).decode(proof);
        Map<String, Object> confirmation = accessToken.getClaimAsMap("cnf");
        String expectedThumbprint = confirmation == null ? null : (String) confirmation.get("jkt");
        Object jwk = decodedProof.getHeaders().get("jwk");
        String actualThumbprint = JWK.parse((Map<String, Object>) jwk).computeThumbprint().toString();
        if (expectedThumbprint == null || !expectedThumbprint.equals(actualThumbprint)) {
            reject(request, response, "dpop_jkt_mismatch");
            return false;
        }
        LOGGER.debug("dpop_validation_success component=gateway path={} proofId={}",
                request.getRequestURI(), decodedProof.getId());
        return true;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String reason) {
        LOGGER.debug("dpop_validation_failure component=gateway path={} reason={}",
                request.getRequestURI(), reason);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "DPoP error=\"invalid_dpop_proof\"");
        if (debugHeaders) {
            response.setHeader("X-DPoP-Validation", reason);
        }
    }
}

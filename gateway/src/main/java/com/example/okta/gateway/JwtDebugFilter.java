package com.example.okta.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(801)
public class JwtDebugFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtDebugFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            logValidatedToken(request, jwtAuthentication.getToken());
        }
        chain.doFilter(request, response);
    }

    private void logValidatedToken(HttpServletRequest request, Jwt jwt) {
        LOGGER.debug(
                "jwt_validation_success component=gateway path={} subject={} issuer={} expiresAt={} scopes={} tokenFingerprint={}",
                request.getRequestURI(),
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

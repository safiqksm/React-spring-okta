package com.example.okta.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects requests whose access token was issued before its subject was last revoked
 * via Global Token Revocation. Runs after normal JWT authentication already passed —
 * this is the extra check that fakes revocation for an otherwise-stateless JWT.
 */
@Component
public class RevocationCheckFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RevocationCheckFilter.class);
    private final RevocationDenyList denyList;

    RevocationCheckFilter(RevocationDenyList denyList) {
        this.denyList = denyList;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            String subject = jwtAuthentication.getToken().getSubject();
            Instant issuedAt = jwtAuthentication.getToken().getIssuedAt();
            if (issuedAt != null && denyList.isRevoked(subject, issuedAt)) {
                LOGGER.debug("revocation_check_failure component=gateway path={} subject={}",
                        request.getRequestURI(), subject);
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"session_revoked\"");
                response.setHeader("X-Session-Revoked", "true");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}

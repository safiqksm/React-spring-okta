package com.example.okta.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-memory Global Token Revocation deny list, scoped to this single Gateway process.
 * Does not survive a restart and is not shared across replicas — acceptable for this
 * local POC (single instance, Phase 5 containerization deferred); a shared store
 * (Redis or equivalent) becomes a hard requirement the moment there's more than one
 * Gateway instance.
 */
@Component
public class RevocationDenyList {
    private static final Logger LOGGER = LoggerFactory.getLogger(RevocationDenyList.class);
    private final Map<String, Instant> revokedAt = new ConcurrentHashMap<>();
    private final Duration maxTokenLifetime;

    RevocationDenyList(@Value("${app.revocation.max-token-lifetime-seconds:3600}") long maxTokenLifetimeSeconds) {
        this.maxTokenLifetime = Duration.ofSeconds(maxTokenLifetimeSeconds);
    }

    void revoke(String subject) {
        revokedAt.put(subject, Instant.now());
        LOGGER.debug("revocation_recorded component=gateway subject={}", subject);
    }

    boolean isRevoked(String subject, Instant tokenIssuedAt) {
        Instant revokedInstant = revokedAt.get(subject);
        return revokedInstant != null && tokenIssuedAt.isBefore(revokedInstant);
    }

    /**
     * A revocation entry only matters until every token issued before it would have
     * expired on its own anyway — after that it would fail normal expiry validation
     * regardless. This sweep bounds memory growth since a plain map has no native TTL.
     */
    @Scheduled(fixedDelayString = "${app.revocation.sweep-interval-ms:300000}")
    void sweep() {
        Instant cutoff = Instant.now().minus(maxTokenLifetime);
        int before = revokedAt.size();
        revokedAt.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        int removed = before - revokedAt.size();
        if (removed > 0) {
            LOGGER.debug("revocation_sweep component=gateway removed={} remaining={}", removed, revokedAt.size());
        }
    }
}

package com.example.okta.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RevocationDenyListTest {
    private final RevocationDenyList denyList = new RevocationDenyList(3600);

    @Test
    void tokenIssuedBeforeRevocationIsRevoked() {
        Instant tokenIssuedAt = Instant.now();
        denyList.revoke("user-1");

        assertThat(denyList.isRevoked("user-1", tokenIssuedAt)).isTrue();
    }

    @Test
    void tokenIssuedAfterRevocationIsNotRevoked() {
        denyList.revoke("user-1");
        Instant tokenIssuedAt = Instant.now().plusSeconds(1);

        assertThat(denyList.isRevoked("user-1", tokenIssuedAt)).isFalse();
    }

    @Test
    void unknownSubjectIsNotRevoked() {
        assertThat(denyList.isRevoked("never-revoked", Instant.now())).isFalse();
    }

    @Test
    void sweepRemovesEntriesOlderThanMaxTokenLifetime() {
        RevocationDenyList shortLived = new RevocationDenyList(0);
        shortLived.revoke("user-1");

        shortLived.sweep();

        assertThat(shortLived.isRevoked("user-1", Instant.now().minusSeconds(10))).isFalse();
    }
}

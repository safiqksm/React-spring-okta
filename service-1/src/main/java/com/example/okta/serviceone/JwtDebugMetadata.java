package com.example.okta.serviceone;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.security.oauth2.jwt.Jwt;

final class JwtDebugMetadata {
    private JwtDebugMetadata() { }

    static Map<String, Object> from(Jwt jwt) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("validated", true);
        metadata.put("subject", jwt.getSubject());
        metadata.put("issuer", String.valueOf(jwt.getIssuer()));
        metadata.put("expiresAt", String.valueOf(jwt.getExpiresAt()));
        metadata.put("scopes", scopes(jwt));
        metadata.put("tokenFingerprint", fingerprint(jwt));
        // Raw token is intentionally included for this teaching/demo UI, which decodes and
        // displays it client-side to the same user the token already belongs to.
        metadata.put("raw", jwt.getTokenValue());
        return metadata;
    }

    static void logValidation(Logger logger, String component, Jwt jwt) {
        logger.debug("jwt_validation_success component={} subject={} issuer={} expiresAt={} scopes={} tokenFingerprint={}",
                component, jwt.getSubject(), jwt.getIssuer(), jwt.getExpiresAt(), scopes(jwt), fingerprint(jwt));
    }

    private static List<String> scopes(Jwt jwt) {
        List<String> scopedClaims = jwt.getClaimAsStringList("scp");
        if (scopedClaims != null) {
            return scopedClaims;
        }
        String scope = jwt.getClaimAsString("scope");
        return scope == null ? List.of() : List.of(scope.split(" "));
    }

    private static String fingerprint(Jwt jwt) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(jwt.getTokenValue().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

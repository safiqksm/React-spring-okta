package com.example.okta.serviceone;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.JWKSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
class ServiceTwoTokenProvider {
    private final RestClient restClient;
    private final String tokenUri;
    private final String clientId;
    private final String privateKeyPath;
    private final String scope;

    ServiceTwoTokenProvider(
            RestClient restClient,
            @Value("${service-two.client.token-uri}") String tokenUri,
            @Value("${service-two.client.client-id}") String clientId,
            @Value("${service-two.client.private-key-path}") String privateKeyPath,
            @Value("${service-two.client.scope}") String scope) {
        this.restClient = restClient;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.privateKeyPath = privateKeyPath;
        this.scope = scope;
    }

    TokenResult accessToken() {
        if (clientId.isBlank() || privateKeyPath.isBlank()) {
            throw new IllegalStateException("SERVICE_TWO_CLIENT_ID and SERVICE_TWO_CLIENT_PRIVATE_KEY_PATH are required");
        }

        String assertion = clientAssertion();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", scope);
        form.add("client_id", clientId);
        form.add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        form.add("client_assertion", assertion);

        ResponseEntity<Map> entity = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toEntity(Map.class);
        Map<?, ?> response = entity.getBody();
        Object accessToken = response == null ? null : response.get("access_token");
        if (!(accessToken instanceof String token) || token.isBlank()) {
            throw new IllegalStateException("Okta did not return a Service 2 access token");
        }

        Map<String, Object> requestSummary = new LinkedHashMap<>();
        requestSummary.put("method", "POST");
        requestSummary.put("url", tokenUri);
        requestSummary.put("body", Map.of(
                "grant_type", "client_credentials",
                "scope", scope,
                "client_id", clientId,
                "client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                "client_assertion", assertion));

        Map<String, Object> responseSummary = new LinkedHashMap<>();
        responseSummary.put("status", entity.getStatusCode().value());
        responseSummary.put("body", response);

        return new TokenResult(token, requestSummary, responseSummary);
    }

    private String clientAssertion() {
        try {
            JWK parsedKey = RSAKey.parseFromPEMEncodedObjects(Files.readString(Path.of(privateKeyPath)));
            if (!(parsedKey instanceof RSAKey signingKey) || !signingKey.isPrivate()) {
                throw new IllegalArgumentException("Service 2 client key must be an RSA private key");
            }
            JWKSource<SecurityContext> jwkSource = (selector, context) -> selector.select(new JWKSet(signingKey));
            JwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(clientId)
                    .subject(clientId)
                    .audience(java.util.List.of(tokenUri))
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(300))
                    .id(UUID.randomUUID().toString())
                    .build();
            return encoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(), claims)).getTokenValue();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create the Service 2 private-key JWT client assertion", exception);
        }
    }
}

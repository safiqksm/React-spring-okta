package com.example.okta.serviceone;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
class ServiceThreeTokenProvider {
    private final RestClient restClient;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final String audience;

    ServiceThreeTokenProvider(
            RestClient restClient,
            @Value("${service-three.client.token-uri}") String tokenUri,
            @Value("${service-three.client.client-id}") String clientId,
            @Value("${service-three.client.client-secret}") String clientSecret,
            @Value("${service-three.client.scope}") String scope,
            @Value("${service-three.client.audience}") String audience) {
        this.restClient = restClient;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        this.audience = audience;
    }

    String exchange(String subjectToken) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new IllegalStateException("SERVICE_THREE_CLIENT_ID and SERVICE_THREE_CLIENT_SECRET are required");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        form.add("subject_token_type", "urn:ietf:params:oauth:token-type:access_token");
        form.add("subject_token", subjectToken);
        form.add("scope", scope);
        form.add("audience", audience);

        Map<?, ?> response = restClient.post()
                .uri(tokenUri)
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        Object accessToken = response == null ? null : response.get("access_token");
        if (!(accessToken instanceof String token) || token.isBlank()) {
            throw new IllegalStateException("Okta did not return a Service 3 exchanged access token");
        }
        return token;
    }
}

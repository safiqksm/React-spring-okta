package com.example.okta.gateway;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Enumeration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Hand-rolled reverse proxy, replacing Spring Cloud Gateway's reactive RouteLocator.
 * Forwards each request byte-for-byte to the matching downstream service and relays
 * its response back unchanged (status, headers, body) — a generic proxy has no reason
 * to assume the body is JSON.
 *
 * Explicitly sets X-Forwarded-Host/Proto/Port on the outbound call: Spring Cloud
 * Gateway used to add these automatically, and Service 1/2/3 depend on them
 * (server.forward-headers-strategy: framework) to reconstruct the Gateway's public
 * URL for DPoP proof htu validation. Dropping this silently would reintroduce the
 * exact bug documented in CLAUDE.md / EXECUTION_LOG.md ISSUE-003.
 */
@RestController
public class ProxyController {
    private final RestClient restClient;
    private final String serviceOneUrl;
    private final String serviceTwoUrl;

    ProxyController(
            RestClient restClient,
            @Value("${service-one.url:http://localhost:8081}") String serviceOneUrl,
            @Value("${service-two.url:http://localhost:8082}") String serviceTwoUrl) {
        this.restClient = restClient;
        this.serviceOneUrl = serviceOneUrl;
        this.serviceTwoUrl = serviceTwoUrl;
    }

    @RequestMapping("/api/service-1/**")
    ResponseEntity<byte[]> proxyToServiceOne(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
        return forward(serviceOneUrl, request, body);
    }

    @RequestMapping("/api/settings/**")
    ResponseEntity<byte[]> proxyToServiceTwo(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
        return forward(serviceTwoUrl, request, body);
    }

    private ResponseEntity<byte[]> forward(String targetBaseUrl, HttpServletRequest request, byte[] body) {
        String query = request.getQueryString();
        String targetUrl = targetBaseUrl + request.getRequestURI() + (query != null ? "?" + query : "");

        RestClient.RequestBodySpec requestSpec = restClient
                .method(HttpMethod.valueOf(request.getMethod()))
                .uri(targetUrl)
                .headers(headers -> {
                    copyRequestHeaders(request, headers);
                    headers.set("X-Forwarded-Host", forwardedHost(request));
                    headers.set("X-Forwarded-Proto", request.getScheme());
                    headers.set("X-Forwarded-Port", String.valueOf(request.getServerPort()));
                });

        return (body != null && body.length > 0 ? requestSpec.body(body) : requestSpec)
                .exchange((clientRequest, clientResponse) -> {
                    byte[] responseBody = clientResponse.getBody().readAllBytes();
                    var responseHeaders = new org.springframework.http.HttpHeaders();
                    clientResponse.getHeaders().forEach((name, values) -> {
                        if (!"transfer-encoding".equalsIgnoreCase(name) && !"connection".equalsIgnoreCase(name)) {
                            responseHeaders.addAll(name, values);
                        }
                    });
                    return ResponseEntity.status(clientResponse.getStatusCode())
                            .headers(responseHeaders)
                            .body(responseBody);
                });
    }

    private static void copyRequestHeaders(HttpServletRequest request, org.springframework.http.HttpHeaders headers) {
        Enumeration<String> headerNames = request.getHeaderNames();
        for (String name : Collections.list(headerNames)) {
            if ("host".equalsIgnoreCase(name) || "content-length".equalsIgnoreCase(name)) {
                continue;
            }
            for (String value : Collections.list(request.getHeaders(name))) {
                headers.add(name, value);
            }
        }
    }

    private static String forwardedHost(HttpServletRequest request) {
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443);
        return defaultPort ? request.getServerName() : request.getServerName() + ":" + port;
    }
}

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A local-first sample (no containers/deployment yet) demonstrating a React SPA authenticating
with Okta OIE, calling a Spring Cloud Gateway, which routes to two Spring Boot resource
services. It implements, in order of increasing complexity: Authorization Code + PKCE, PAR
(Pushed Authorization Requests), DPoP-bound access tokens at the Gateway boundary, and a
service-to-service `private_key_jwt` client-credentials hop from Service 1 to Service 2. See
`PLAN.md` for the full phased design (Phase 5 containerization/deployment is explicitly
deferred) and `IMPLEMENTATION_STATUS.md` / `EXECUTION_LOG.md` for current known issues and
in-flight debugging state — check these before assuming a flow is fully working end-to-end.

## Commands

Build/test everything:
```bash
./gradlew test              # all Java module tests
cd frontend && npm run build # frontend production build (also type/lint-equivalent check)
```

Run a single Java test class/method:
```bash
./gradlew :service-1:test --tests "com.example.okta.serviceone.ApiControllerTest"
./gradlew :service-2:test --tests "com.example.okta.servicetwo.ServiceTwoControllerTest.someMethod"
```

Run everything locally (one terminal, builds boot jars then launches all four processes):
```bash
./scripts/run-local.sh
```

Or run each piece in its own terminal (order matters — start downstream services first):
```bash
./gradlew :service-2:bootRun   # :8082
./gradlew :service-1:bootRun   # :8081
./gradlew :gateway:bootRun     # :8080
cd frontend && npm install && npm run dev  # :5173
```

If Java 21 was installed via Homebrew, each terminal needs:
```bash
export JAVA_HOME="$(brew --prefix openjdk@21)"
export PATH="$JAVA_HOME/bin:$PATH"
```

Frontend-only: `cd frontend && npm run dev` / `npm run build` / `npm run preview`.

## Architecture

**Request path:** Browser (React SPA, Vite, port 5173) → Spring Cloud Gateway (WebFlux, port
8080, OAuth2 resource server + DPoP validation) → Service 1 (Servlet, port 8081, resource
server) → Service 2 (Servlet, port 8082, resource server). All three backend modules are
independent Gradle subprojects declared in `settings.gradle`; there is no shared Java module —
each duplicates its own `SecurityConfig`, `JwtDebugMetadata`, etc. When changing security or
logging behavior, check whether the same change is needed in gateway, service-1, and service-2.

**Token flow is DPoP end-to-end, not converted at the Gateway:**
1. Browser → Gateway: DPoP-bound access token (`Authorization: DPoP <token>` + `DPoP: <proof>`
   header). `gateway/.../DpopProofWebFilter.java` validates the proof (method, target URI,
   access-token hash via nimbus `DPoPProofJwtDecoderFactory`, and the token's `cnf.jkt`
   thumbprint match) as a `WebFilter` ordered after Spring Security's authentication filter.
2. Gateway → Service 1/2: the Gateway forwards the `DPoP`-scheme Authorization header and the
   `DPoP` proof header unchanged — it does **not** rewrite them to Bearer. Spring Security
   6.5.x auto-registers `DPoPAuthenticationConfigurer` on any resource server that has
   `DPoPProofJwtDecoderFactory` on the classpath (true here via
   `spring-boot-starter-oauth2-resource-server`), so Service 1/2 validate the DPoP proof and
   JWT themselves with zero custom code. **Do not reintroduce a filter that converts
   `DPoP` → `Bearer` before forwarding** — Spring's `BearerTokenAuthenticationFilter` has a
   hardcoded check (`isDPoPBoundAccessToken`) that rejects any JWT with a `cnf.jkt` claim
   presented via the Bearer scheme (`OAuth2AuthenticationException: Invalid bearer token`),
   specifically to prevent this exact downgrade. Because Service 1/2 must independently
   reconstruct the *external* URL the DPoP proof was bound to (the browser signs the proof
   against the Gateway's public URL, e.g. `http://localhost:8080/...`, not each service's own
   internal port), both `service-1/application.yml` and `service-2/application.yml` set
   `server.forward-headers-strategy: framework` so `HttpServletRequest.getRequestURL()` honors
   `X-Forwarded-*` headers. Those headers are **not** added by default: Spring Cloud Gateway's
   `XForwardedHeadersFilter` only registers when `spring.cloud.gateway.server.webflux.trusted-proxies`
   is explicitly set (confirmed by decompiling `spring-cloud-gateway-server-4.3.4.jar`), so
   `gateway/application.yml` sets it to a loopback-matching regex. Without both pieces together,
   DPoP proof `htu` validation fails with a port mismatch.
3. Service 1 → Service 2: an entirely separate OAuth 2.0 client-credentials token, not the
   user's forwarded token. `service-1/.../ServiceTwoTokenProvider.java` reads an RSA private
   key from `SERVICE_TWO_CLIENT_PRIVATE_KEY_PATH` (default `secrets/service-1-private-key.pem`,
   gitignored), signs a `private_key_jwt` client assertion with nimbus, and exchanges it at
   Okta's token endpoint for a `service2.read`-scoped token. Service 2 enforces
   `SCOPE_service2.read` on `/api/service-2/**`.

**Frontend auth (`frontend/src/auth.js`):** wraps `@okta/okta-auth-js`. PAR is on by default
(`VITE_OKTA_USE_PAR`) — `signInWithPar` fetches OIDC discovery metadata, POSTs the prepared
token params to the advertised `pushed_authorization_request_endpoint`, stores the resulting
transaction manually via `oktaAuth.transactionManager.save(...)`, then redirects with
`request_uri` instead of full auth params. DPoP is a separate flag (`VITE_OKTA_USE_DPOP`)
handled by okta-auth-js's built-in DPoP support (`dpopOptions: { allowBearerTokens: false }`).
`frontend/src/api.js` is the only place that attaches tokens to outgoing API calls — it branches
on `oktaAuth.options.dpop` to choose DPoP headers vs. a plain Bearer header, and maps specific
response headers (`X-DPoP-Validation`, `X-Authentication-Failure`, `WWW-Authenticate`) to
user-facing error messages.

**Gateway routing** (`gateway/.../RouteConfig.java`) is a static, hardcoded list of two routes:
`/api/service-1/**` → Service 1, `/api/settings/**` → Service 2 (Service 2's settings endpoints
are exposed under a different path prefix than its own service name — don't assume path prefix
matches service name).

**Config pattern:** every module's `application.yml` reads `OKTA_ISSUER`/`OKTA_AUDIENCE` (same
values expected across all three backend services) and `APP_LOG_LEVEL` via env vars with
inline defaults pointing at the shared dev Okta org (`ntrsoiesys.oktapreview.com`). Debug-level
logging (`com.example.okta: DEBUG`) is intentional in local dev and logs safe JWT metadata only
(subject, issuer, expiry, scopes, a truncated SHA-256 token fingerprint) — never raw tokens.
Preserve this pattern in new code: don't log full JWTs, access tokens, DPoP proofs, or the
service-to-service private key.

## Security constraints (do not relax without explicit instruction)

- The React SPA is a public client — no client secret, ever, in `frontend/`.
- `secrets/` is gitignored and holds only the Service 1 → Service 2 RSA private key; it must
  never be committed, logged, or echoed back in responses.
- Simulated factor add/remove endpoints (`SettingsController` in service-2) must remain
  simulated — they require a bearer token but must never call real Okta management APIs or
  claim to change enrollment state (see `PLAN.md` Phase 1 §3 for the constraint's origin).
- UI must only render approved ID-token claims and DPoP/JWT *metadata* (subject, issuer,
  expiry, scopes, fingerprint, proof method/URI/id) — never raw JWTs or proofs.

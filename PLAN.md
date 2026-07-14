# React, Spring Boot, and Okta Implementation Plan

## Goal

Build a JavaScript React single-page application (SPA) protected by Okta
Identity Engine (OIE). The SPA calls a Spring Cloud Gateway, which validates
access-token JWTs and routes requests to Spring Boot microservices. Service 1
can call Service 2.

## Baseline Technology

| Area | Choice |
| --- | --- |
| Frontend | React SPA with JavaScript |
| Identity provider | Okta OIE |
| Backend | Spring Boot 3.5.13 |
| Java | Java 21 |
| Build | Gradle |
| Gateway | Spring Cloud Gateway |
| Resource security | Spring Security OAuth 2.0 Resource Server |
| Authorization flow | Authorization Code with PKCE |

Use the Spring Cloud release train documented as compatible with Spring Boot
3.5.13 when implementation begins. Use current Okta React and Okta Auth JS SDK
versions that support the selected React version and OIE.

## Target Architecture

```text
Browser
  | Authorization Code + PKCE
  v
Okta OIE
  | Access token (JWT)
  v
React SPA ---- Bearer access token ----> Spring Cloud Gateway
                                              |
                                              | validates JWT
                                              v
                                          Service 1 ----> Service 2
```

## Okta Discovery Configuration

The supplied discovery URL is duplicated. Use this issuer and discovery URL:

```text
Issuer: https://ntrsoiesys.oktapreview.com/oauth2/aus11aowjcqDJAtVk1d8
Discovery: https://ntrsoiesys.oktapreview.com/.well-known/oauth-authorization-server/oauth2/aus11aowjcqDJAtVk1d8
```

Before coding, confirm the SPA client ID, redirect URI, post-logout redirect
URI, allowed origins, custom authorization-server audience, and required scopes.
Do not store client secrets in the SPA.

If a confidential client is introduced for backend token exchange or
service-to-service access, keep its credential in a server-side secret manager.
Use private-key JWT client authentication when that client is migrated from a
secret-based method; never expose either credential to the browser.

## Phase 1: SPA, Gateway, and Services

### 1. Project structure

- Create `frontend/` for the React SPA.
- Create `gateway/` for Spring Cloud Gateway.
- Create `service-1/` and `service-2/` as Spring Boot Gradle services.
- Add shared local-development configuration and a root README.
- Run all applications directly on localhost during this phase; do not add
  Docker, Kubernetes, or deployment configuration.

### 2. React authentication

- Configure Okta React and Okta Auth JS for Authorization Code with PKCE.
- Add public and protected routes with login and logout controls.
- Handle the Okta callback and restore the original SPA route.
- Attach the access token only to Gateway API requests.
- Restrict token storage and use SDK-supported token renewal behavior.

### 3. SPA user experience and account settings

- Use a light, Azure-portal-inspired visual theme: white or light-gray surfaces,
  Azure-blue primary actions, clear typography, responsive navigation, and
  accessible focus, contrast, and error states.
- Add an authenticated home page that displays a **User Profile** panel from
  validated ID-token claims, such as display name, email, subject (`sub`), and
  groups only when that claim is intentionally included.
- Do not display raw JWT strings, access tokens, refresh tokens, or sensitive
  claims in the UI, browser console, logs, or error messages.
- Add a **Settings** button in the primary navigation and a protected Settings
  page.
- In Phase 1, provide **Add factor** and **Remove factor** buttons that send
  the user's bearer access token to token-protected placeholder endpoints via
  the Gateway. The service returns a successful simulated result and the UI
  displays an "Factor added" or "Factor removed" message.
- The Phase 1 placeholder must not change Okta enrollment state or claim that
  a real factor was enrolled or removed. Label this behavior as simulated in
  the UI.
- Use explicit confirmation and accessible success/error messages for each
  simulated request.

Actual factor enrollment and removal are deferred to a later phase. They must
use Okta-supported Identity Engine account-management flows. A React SPA must
not contain an Okta API token or call privileged Okta Management APIs directly.
If real factor management requires privileged calls, expose a narrowly scoped
backend-for-frontend endpoint that validates the user JWT, applies
authorization, and uses server-side Okta credentials stored outside source
control. Confirm the applicable Okta OIE API and enabled authenticator policies
before implementation.

### 4. Gateway security and routing

- Configure the Gateway as an OAuth 2.0 Resource Server using the issuer above.
- Validate JWT signature, expiry, issuer, audience, and scopes.
- Return `401` for invalid or missing tokens and `403` for insufficient scope.
- Configure CORS to permit only SPA local and deployed origins.
- Route `/api/service-1/**` to Service 1 and avoid exposing internal URLs.
- Add health, readiness, request logging, and correlation-ID propagation.

### 5. Service security and communication

- Configure Service 1 and Service 2 as OAuth 2.0 Resource Servers.
- Enforce endpoint-level scope and authority checks in both services.
- Make Service 1 call Service 2 through a configured HTTP client.
- For Phase 1, forward the authenticated user access token only if its audience
  and scopes explicitly permit Service 2; otherwise use a documented
  service-to-service client-credentials token. Confirm this choice before code.
- Propagate correlation IDs and map downstream failures to safe API responses.

### 6. Phase 1 test gate

- Unit test React protected routes and API token attachment.
- Unit test Gateway JWT, scope, CORS, and route rules.
- Integration test Service 1 to Service 2 success and failure paths.
- Test missing, malformed, expired, wrong-issuer, wrong-audience, and
  insufficient-scope tokens.
- Perform a browser end-to-end test: login -> SPA -> Gateway -> Service 1 ->
  Service 2 -> response.
- Verify logout, callback error handling, token renewal, and correlation IDs.
- Verify profile claims render safely, Settings is protected, and simulated
  add/remove requests contain the access token and traverse the Gateway.
- Verify simulated add/remove confirmations, unauthorized access, and
  user-friendly failure handling. Verify that no Okta factor state changes.

Phase 2 must not begin until all Phase 1 tests pass in the local environment.

## Phase 2: DPoP

- Enable DPoP in SPA Okta authorization and token requests.
- Send a DPoP proof with Gateway API requests.
- Validate proof method, URL, issue time, unique ID, access-token hash, and
  confirmation binding at the Gateway.
- Add replay detection with bounded storage and clear expiry behavior.
- Test missing, expired, mismatched, and replayed DPoP proofs.

## Phase 3: Okta On-Behalf-Of (Service 1 → Service 3)

Reference: [Set up token exchange](https://developer.okta.com/docs/guides/set-up-token-exchange/main/).
Service 1 → Service 2 keeps its existing Phase 2 client-credentials/`private_key_jwt`
hop unchanged — that pattern intentionally has no user context and stays as the
"pure service identity" example. Service 3 is the new target that demonstrates the
opposite trust model: Service 1 exchanges the caller's own delegated access token
for a new, narrowly scoped token that still carries the original user's `sub`,
using RFC 8693 token exchange (`grant_type=urn:ietf:params:oauth:grant-type:token-exchange`).

### Okta grant mechanics (from the reference guide above)

The token exchange request Service 1 sends to Okta's `/v1/token` endpoint:

```
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
subject_token_type=urn:ietf:params:oauth:token-type:access_token
subject_token={the caller's own access token, as validated by Service 1}
scope=service3.read
audience=https://ntrsoiesys.oktapreview.com
```

Client authentication is Service 1's own confidential-client credential, sent
however that client is configured (see Decision 8 below). Okta's response is a
new access token whose `sub` claim still matches the original user, but whose
`scope` is narrowed to what was requested and granted by the access policy —
Service 1 never has to see or manufacture the downstream identity itself, Okta
enforces the narrowing.

The reference guide's own walkthrough authenticates the service app with
`client_secret_basic` (base64 client ID/secret in the `Authorization` header)
and does not mention `private_key_jwt` for this specific grant, `actor_token`,
or `requested_token_type` — none of those appear in Okta's documented example,
so this plan doesn't assume them. Confirm during Okta console setup (Decision 8)
whether the org's token endpoint accepts `private_key_jwt` for a token-exchange
grant the same way it already does for Service 1 → Service 2's client-credentials
grant; if not, fall back to `client_secret_basic` with the secret held server-side
only (never in the SPA, matching the existing security posture in this file).

### Okta admin console configuration (per the reference guide's procedure)

1. Under **Security > API**, select the existing custom authorization server
   (`aus11aowjcqDJAtVk1d8`) and open its **Scopes** tab. Add a new scope
   `service3.read`, matching the naming already used for `service2.read`.
2. Decide and create the OAuth client that will perform the exchange
   (Decision 8): either edit the existing Service 1 → Service 2 "API Services"
   app to add the **Token Exchange** grant type (**General Settings > Grant
   type > Advanced**), or create a second, dedicated API Services app scoped
   only to this exchange. This plan recommends a dedicated app so the
   client-credentials trust boundary (Service 1 → Service 2) stays separate
   from the token-exchange trust boundary (Service 1 → Service 3) — one
   compromised credential doesn't grant both capabilities.
3. Under the authorization server's **Access Policies** tab, add a new policy
   assigned to that client app (e.g. name it "Access Service 3"), then add a
   rule under it (e.g. "Service 1 to Service 3") permitting grant type
   **Token Exchange** with **the following scopes**: `service3.read`.
4. If the client uses `private_key_jwt`, register its public JWK the same way
   the existing Service 1 → Service 2 client does; if it uses a client secret,
   generate one and store it the same way the existing PKCS#8 private key is
   stored — outside the repository, referenced only by an environment
   variable at runtime.

### Service 3 (new Gradle module)

- Add `service-3` next to `service-1`/`service-2` in `settings.gradle`.
- `service-3/build.gradle`: `spring-boot-starter-web`,
  `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-actuator`
  — same shape as `service-2/build.gradle`. No nimbus/BouncyCastle dependency
  needed; Service 3 only validates inbound JWTs, it doesn't sign anything.
- `service-3/src/main/resources/application.yml`: `server.port: 8083`
  (next free port after 8080/8081/8082), same
  `OKTA_ISSUER`/`OKTA_AUDIENCE`/`APP_LOG_LEVEL` env-var pattern as the other
  two services.
- `SecurityConfig`: protect `/api/service-3/**` with
  `.hasAuthority("SCOPE_service3.read")`, mirroring Service 2's
  `SecurityConfig` for `/api/service-2/**`.
- A controller (e.g. `ServiceThreeController`) exposing `GET
  /api/service-3/ping`, returning the same
  `{"message": ..., "jwt": {...}}` shape as `ServiceTwoController`/`JwtDebugMetadata`
  (subject, issuer, expiresAt, scopes, tokenFingerprint) — the returned
  `subject` here is the demonstration payoff: it should show the *original
  user's* subject, not a service-account client ID, proving the OBO exchange
  preserved delegated identity while `scopes` shows only `service3.read`.
- Service 3 is **not** given a Gateway route. It's reached only from inside
  Service 1's process, the same way Service 2 is reached for the
  Service 1 → Service 2 hop — no public path to it exists.

### Service 1 changes

- New `ServiceThreeTokenProvider` alongside the existing
  `ServiceTwoTokenProvider`, implementing the token-exchange POST described
  above instead of a client-credentials POST. It takes the inbound caller's
  own `Jwt` (already available via `@AuthenticationPrincipal Jwt jwt` in
  `ApiController`) and returns the exchanged access token string.
- New `ApiController` endpoint, e.g. `GET /api/service-1/obo-hello`, that
  calls `serviceThreeTokenProvider.exchange(jwt)`, then calls Service 3's
  `/api/service-3/ping` with the exchanged token as `Authorization: Bearer`,
  and returns a response combining the caller's own validated JWT metadata
  and Service 3's response — mirroring the existing `/api/service-1/hello`
  shape (`jwt` + `service3Jwt` fields) so the frontend pattern established for
  `service2Jwt` extends naturally.
- Reachable via the Gateway's existing `/api/service-1/**` route — no Gateway
  routing changes needed.

### Frontend (stretch, not required for the OBO demo to work end-to-end)

- Add a third diagnostic button, "Call Service 1 to Service 3 (OBO)", calling
  the new `/api/service-1/obo-hello` endpoint, and a UI card showing
  `service3Jwt` the same way the existing `service2Jwt` card renders — the
  interesting contrast to show side by side: `service2Jwt.subject` is a
  service client ID (client-credentials), `service3Jwt.subject` is the
  signed-in user's own subject (token exchange), even though both hops
  originate from Service 1.

### Phase 3 test gate

- Unit test `ServiceThreeTokenProvider`'s request body construction (grant
  type, subject_token, subject_token_type, scope, audience) without hitting
  a real Okta endpoint.
- Integration test Service 1 → Service 3 success and failure paths: missing
  Token Exchange grant on the client, insufficient scope in the access
  policy, expired/invalid subject token, Service 3 rejecting a token whose
  scope isn't `service3.read`.
- Verify the exchanged token's `sub` claim still equals the original caller's
  `sub`, and its `scope` claim is narrowed to `service3.read` only (not the
  full set of user scopes like `openid profile email`).
- Verify no token value (subject token or exchanged token) appears in logs;
  reuse the same subject/issuer/expiry/scope/fingerprint-only logging pattern
  already used everywhere else in this repo.

Phase 3 must not begin implementation until Decisions 7–10 below are resolved.

## Phase 4: Pushed Authorization Requests (PAR)

- Configure the Okta authorization server and SPA client for PAR.
- Push authorization parameters to the PAR endpoint before browser redirect.
- Keep Authorization Code with PKCE for callback and token exchange.
- Test valid requests, expired request URIs, rejected parameters, and browser
  callback failures.

## Phase 5: Optional Containerization and Deployment

Begin this phase only after Phases 1 through 4 are complete and tested locally.

- Decide the target deployment environment and service-discovery approach.
- Add Dockerfiles and local container orchestration only if required.
- Add Kubernetes manifests or other platform configuration only after the
  containerized application is validated.
- Run deployment-specific integration, security, and operational tests.

## Phase 6: Universal Logout (Global Token Revocation)

Reference: [Okta Global Token Revocation (Universal Logout) developer guide](https://developer.okta.com/docs/guides/universal-logout/).
Universal Logout (UL) is Okta's back-channel, server-to-server kill switch: when
Okta detects a risk change (Identity Threat Protection) or an admin clicks
**Clear user sessions**, Okta's cloud calls an app-specific endpoint over HTTPS
and tells it "terminate everything for this subject." No browser is involved —
unlike SAML SLO or front-channel OIDC logout, which this is not and should not
be called in any doc or diagram for this repo. Okta's own request contract:

```
POST /global-token-revocation HTTP/1.1
Content-Type: application/json
Authorization: Bearer <signed JWT, typ=global-token-revocation+jwt, aud=this exact URL, 5 min exp>

{"sub_id": {"format": "iss_sub", "iss": "https://yourOktaOrg.okta.com", "sub": "00u1a2b3c4d5e6f7g8h9"}}
```

The body carries **who** to revoke, never a token value — this app is
responsible for finding and invalidating whatever it issued to that subject.
That's the core problem this phase solves: this Gateway validates JWTs
statelessly (signature + claims, no per-request lookup), so there is no
session row anywhere to delete. The only way to "revoke" a token that's
already been handed to the browser is to fake it with a deny list: every
authenticated request gets one extra check, after normal JWT validation
already passed — *is this subject's token older than the last time Okta told
us to revoke them?*

### Scope for this phase: Gateway only, in-memory deny list

Enforcement lives entirely in the Gateway — the sole entry point from the
browser. Service 1/2/3 are not touched: Service 1 → Service 2 uses a
client-credentials token (no user subject to revoke), and Service 1 → Service
3's OBO-exchanged token is separately scoped and short-lived, so stopping the
original browser → Gateway hop is what actually matters here. If a revoked
user's already-forwarded token could still reach Service 1 within its natural
lifetime, that's an accepted gap for this phase (see Decision 15).

Per your own ask, the deny list is **in-memory**, not Redis — a
`ConcurrentHashMap<String, Instant>` keyed by the JWT `sub` claim, value =
when that subject was last revoked. This is a deliberate, documented
trade-off, not an oversight:

- It only protects the single Gateway process it lives in. If this project is
  ever run as multiple replicas (Phase 5 containerization), a revocation
  handled by one replica is invisible to the others — an in-memory map has no
  way to see across processes. Redis (or an equivalent shared, low-latency
  store) becomes a hard requirement the moment Phase 5 introduces more than
  one Gateway instance. Since Phase 5 is explicitly deferred and this project
  runs as one Gateway process locally, that limitation doesn't apply yet.
- It does not survive a Gateway restart — every revocation is forgotten on
  restart. Acceptable for a local POC; would need to be called out prominently
  if this code is ever the basis for something that runs in production.
- It needs its own cleanup, since a plain map has no TTL. A revocation entry
  only matters until every token issued before it would have expired anyway
  (after that, the token was going to fail signature/expiry validation
  regardless) — so a scheduled sweep removes entries older than the
  configured access-token lifetime (Decision 16).

### Gateway changes

- **`RevocationDenyList`** (`gateway/.../RevocationDenyList.java`), a
  `@Component` wrapping a `ConcurrentHashMap<String, Instant>`:
  - `void revoke(String subject)` — `put(subject, Instant.now())`.
  - `boolean isRevoked(String subject, Instant tokenIssuedAt)` — `true` when
    the map has an entry for `subject` and `tokenIssuedAt` is before it.
  - A `@Scheduled` sweep (fixed delay, e.g. every 5 minutes) removes entries
    older than `app.revocation.max-token-lifetime` (Decision 16).
- **`GlobalTokenRevocationController`** (`gateway/.../GlobalTokenRevocationController.java`),
  a plain `@RestController` (Gateway can host its own controllers alongside
  `RouteConfig`'s proxied routes) exposing `POST /global-token-revocation`:
  1. Validate the `Authorization: Bearer <jwt>` header with a **dedicated**
     `ReactiveJwtDecoder` — this is *not* the same decoder the
     `oauth2ResourceServer()` chain in `SecurityConfig` uses for the SPA's own
     access tokens, because the audience is completely different (this
     endpoint's own URL, not the API audience). Validate: signature against
     Okta's JWKS, `aud` equals this endpoint's exact configured URL, `typ`
     header equals `global-token-revocation+jwt`, not expired.
  2. Parse `sub_id` from the JSON body per the configured subject format
     (Decision 14) and resolve it to the same `sub` value this Gateway's own
     JWT validation already sees in access tokens.
  3. Call `revocationDenyList.revoke(subject)`.
  4. Return `204`; `400` if the body doesn't parse; log subject/issuer/outcome
     only, in keeping with this repo's existing metadata-only logging
     convention — never log the incoming JWT or request body verbatim.
  - Okta reads only the HTTP status code, not the response body — don't build
    a structured error payload expecting Okta to consume it.
- **`SecurityConfig.java`**: add `/global-token-revocation` to the
  `permitAll()` matchers alongside `/actuator/health` — this endpoint has its
  own bespoke authentication (step 1 above), not the SPA's OAuth2 resource
  server chain.
- **`RevocationCheckWebFilter`** (`gateway/.../RevocationCheckWebFilter.java`),
  a new `WebFilter` mirroring `DpopProofWebFilter`'s shape — registered via
  `addFilterAfter(..., SecurityWebFiltersOrder.AUTHENTICATION)`, so it runs
  after normal JWT validation already succeeded. Reads the authenticated
  `JwtAuthenticationToken`'s `sub` and `iat` claims; if
  `revocationDenyList.isRevoked(sub, iat)`, completes the response with `401`
  and a distinguishing header (e.g. `X-Session-Revoked: true`) instead of
  calling `chain.filter(exchange)` — mirrors `DpopProofWebFilter.reject(...)`.
- **Frontend** (stretch, not required for the Gateway-side mechanism to work):
  `frontend/src/api.js`'s existing response-header-to-error-message mapping
  gains a case for `X-Session-Revoked`, distinct from the existing
  `X-Authentication-Failure`/`X-DPoP-Validation` cases, so the SPA can react
  by clearing its local tokens and redirecting to sign-in — but this is purely
  reactive on the SPA's *next* request; nothing in this design pushes the
  revocation to an idle browser tab (that would be a separate WebSocket/SSE
  feature, out of scope here).

### Okta admin console configuration

1. **Applications → Applications → [this app] → Sign On tab → Logout
   section → Edit.**
2. Under **Global Token Revocation**, select **Okta system or admin initiates
   logout**.
3. **Logout endpoint URL**: must be HTTPS and reachable from Okta's cloud —
   `http://localhost:8080/...` is not reachable from Okta, so local testing
   needs a public tunnel (Decision 12).
4. **Endpoint authentication type**: `Signed JWT` — the only option for a
   generic OIDC app; there is no shared-secret alternative.
5. **Subject format**: `Issuer and Subject Identifier` (`iss_sub`) is
   recommended over `Email Identifier` — it's the immutable Okta user ID, not
   an email that can change or need case-insensitive matching (Decision 14).
6. Save, then configure the actual triggers separately: ITP entity risk
   policies (requires the ITP SKU — Decision 13) for automatic firing, or the
   manual **Clear user sessions** action on a user's profile for testing.

### Phase 6 test gate

- Unit test `RevocationDenyList`: revoke then check a token issued before
  (revoked) and after (not revoked) the revocation instant; verify the sweep
  removes entries older than the configured lifetime.
- Unit test the GTR endpoint's JWT validation: reject wrong `aud`, wrong
  `typ`, expired, and bad-signature cases without touching the deny list.
- Integration test: POST a validly-shaped signed JWT + `iss_sub` body, assert
  `204` and that the subject is now revoked; malformed body → `400`.
- Integration test `RevocationCheckWebFilter`: a request with a token whose
  `sub`/`iat` is on the deny list is rejected with `401` and
  `X-Session-Revoked`; an unrevoked or post-revocation-issued token passes.
- Live test, in this order (cleanest signal first): (1) manual **Clear user
  sessions** from the user's Okta profile — confirm the Gateway logs the POST,
  responds `204`, and the deny list contains the subject; (2) a subsequent SPA
  call gets rejected; (3) only then graduate to policy-driven (ITP) triggers.

## Decisions Required Before Implementation

1. Okta SPA client ID, allowed callback/logout URLs, scopes, and API audience.
2. Local SPA and API ports/origins for the initial implementation.
3. Phase 1 Service 1 -> Service 2 authentication: forwarded user token or
   client credentials.
4. Deployment and service-discovery model, deferred until Phase 5.
5. API endpoint contract and authorization policy for each service.
6. Okta authenticator enrollment policies and the approved server-side approach
   for real user factor management, deferred to a later phase.
7. Whether Service 1's token-exchange client is a new, dedicated Okta Service
   App (recommended, keeps the client-credentials and token-exchange trust
   boundaries separate) or the existing Service 1 → Service 2 app with the
   Token Exchange grant type added.
8. Client authentication method for that token-exchange client:
   `private_key_jwt` (consistent with Service 1 → Service 2, but unconfirmed
   as supported for this specific grant — verify in the Okta admin console)
   or `client_secret_basic` (what Okta's own reference guide demonstrates).
9. Confirm the new custom scope name `service3.read` and that the existing
   shared `audience` (`https://ntrsoiesys.oktapreview.com`) is reused rather
   than introducing a second audience.
10. Confirm Service 3 stays internal-only (no Gateway route) versus adding a
    public route for it later.
11. Confirm this app's **Sign On tab** actually shows a **Logout** section —
    it only appears once the app is fully configured and saved, and only if
    the org has the Universal Logout feature.
12. **Local testing reachability** for Phase 6: Okta's cloud must reach the
    revocation endpoint over public HTTPS. Decide the tunnel approach for
    local dev (ngrok, Cloudflare Tunnel, or similar) since `localhost` cannot
    be configured directly in Okta.
13. Confirm the org has the **ITP SKU** on Identity Engine if automatic,
    risk-triggered revocation is wanted (Phase 6); manual **Clear user
    sessions** works without it. Also confirm **Federation Broker Mode** is
    not enabled for this app — Universal Logout doesn't work with custom
    SAML/OIDC apps under FBM.
14. Confirm the exact `iss` value Okta sends in `iss_sub`-format Global Token
    Revocation bodies matches (or can be mapped to) the `iss`/`sub` claims
    this Gateway already validates on ordinary access tokens — this app's
    tokens come from a *custom* authorization server
    (`.../oauth2/aus11aowjcqDJAtVk1d8`), and the GTR body's `iss` may
    reference the org-level issuer instead; don't assume they match without
    checking a real payload.
15. Confirm Gateway-only Universal Logout enforcement (not extended to
    Service 1/2/3) is acceptable for Phase 6, given the accepted gap that an
    already-forwarded token could still reach Service 1 until it naturally
    expires.
16. Confirm the actual configured access-token lifetime for this app (commonly
    a 1-hour default) so Phase 6's `app.revocation.max-token-lifetime` and the
    deny-list sweep interval are set correctly — too short and a
    revoked-but-still-valid token could outlive its deny-list entry.

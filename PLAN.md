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

## Phase 3: Okta On-Behalf-Of

- Configure Okta-supported token exchange/on-behalf-of settings and scopes.
- Have Service 1 exchange the incoming delegated user context for a
  Service-2-specific, least-privilege token.
- Validate exchanged tokens and audience at Service 2.
- Test scope narrowing, authorization failures, exchange failures, and audit
  logging without exposing tokens.

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

## Decisions Required Before Implementation

1. Okta SPA client ID, allowed callback/logout URLs, scopes, and API audience.
2. Local SPA and API ports/origins for the initial implementation.
3. Phase 1 Service 1 -> Service 2 authentication: forwarded user token or
   client credentials.
4. Deployment and service-discovery model, deferred until Phase 5.
5. API endpoint contract and authorization policy for each service.
6. Okta authenticator enrollment policies and the approved server-side approach
   for real user factor management, deferred to a later phase.

# Execution Log: Steps, Problems, and Fixes

Use this file to record implementation activity, blockers, and resolutions.
Add an entry for every meaningful change or issue.

## Status Legend

- `Planned`: not started.
- `In Progress`: currently being implemented.
- `Blocked`: needs a decision, credential, or external change.
- `Complete`: implemented and validated.

## Work Items

| ID | Phase | Step | Status | Validation | Notes |
| --- | --- | --- | --- | --- | --- |
| P1-01 | 1 | Create local React, Gateway, Service 1, and Service 2 projects | Complete | Wrapper build and health checks pass | No containers |
| P1-02 | 1 | Configure Okta SPA login and protected routes | In Progress | Browser login succeeds | SPA client ID configured locally; needs manual Okta sign-in |
| P1-02A | 1 | Build Azure-style profile and Settings pages | Complete | Production SPA build passes | Display only approved ID-token claims |
| P1-02B | 1 | Implement simulated factor add/remove service calls | Complete | Unit tests and SPA build pass | No Okta factor state changes |
| P1-03 | 1 | Configure Gateway JWT validation and routes | Planned | Token security tests pass | |
| P1-04 | 1 | Secure services and Service 1 -> Service 2 call | Planned | Integration tests pass | |
| P1-05 | 1 | Run end-to-end authentication and API tests | Planned | E2E test passes | |
| P2-01 | 2 | Add DPoP issuance and validation | Planned | DPoP tests pass | |
| P3-01 | 3 | Add Okta on-behalf-of token exchange | In Progress | Delegation tests pass | New `service-3` module and Service 1 `ServiceThreeTokenProvider` scaffolded on `feature/service3-okta-obo`; needs a dedicated Okta Service App (Token Exchange grant, `service3.read` scope, access policy) before it can be exercised live |
| P4-01 | 4 | Add pushed authorization requests | Planned | PAR tests pass | |
| P5-01 | 5 | Evaluate containerization and deployment | Planned | Deployment plan approved | Deferred until local phases pass |
| P5-02 | 5 | Implement real Okta factor management | Planned | Okta enroll/remove tests pass | Never expose Okta API tokens to SPA |
| P6-01 | 6 | Implement Global Token Revocation (Universal Logout) in the Gateway | Blocked | Live Okta-triggered `204` + deny-list entry | Code complete on `feature/global-token-revocation` (commit `baceb52`); blocked on Okta not dispatching the outbound call — see ISSUE-004 |

## Problems and Fixes

| ID | Date | Problem | Impact | Investigation | Fix | Status | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ISSUE-001 | 2026-07-13 | Workspace is empty; no application code exists. | Implementation has not started. | Confirmed directory contains no project files. | Create structure after Okta and deployment decisions are provided. | Open | Project skeleton builds. |
| ISSUE-002 | 2026-07-13 | A confidential-client secret was shared outside secure configuration. | Secret must not be used in the React SPA or committed to source control. | SPA uses Authorization Code with PKCE and is a public client. | Do not store the secret; rotate it in Okta. Use a secret manager or private-key JWT for a future confidential backend client. | Open | Rotated secret and no secret found in repository. |
| ISSUE-003 | 2026-07-13 | Gateway -> Service 1/2 calls failed with `OAuth2AuthenticationException: Invalid bearer token` after successful DPoP login. | Both UI diagnostic calls and simulated Settings actions failed for any authenticated user with DPoP enabled. | Decompiled `spring-security-oauth2-resource-server-6.5.9.jar`: `BearerTokenAuthenticationFilter` hardcodes a check that rejects any JWT with a `cnf.jkt` claim presented via the `Bearer` scheme (RFC 9449 anti-downgrade safeguard). The Gateway's `InternalAuthorizationHeaderFilter` was rewriting `DPoP` to `Bearer` before proxying, tripping this on every DPoP-bound token. | Removed `InternalAuthorizationHeaderFilter`; Gateway now forwards the `DPoP` scheme and proof header unchanged. Spring Security auto-registers `DPoPAuthenticationConfigurer` on Service 1/2 (no new code needed). Added `server.forward-headers-strategy: framework` to both services so the DPoP proof's `htu` claim (bound to the Gateway's public URL) matches the reconstructed request URL. | Complete (build/tests); browser E2E still pending | `./gradlew clean compileJava test` passes for all three modules. |
| ISSUE-004 | 2026-07-14 | Okta never dispatches the Global Token Revocation outbound call to the Gateway's `/global-token-revocation` endpoint, despite `Universal Logout: SUCCESS` in Okta's own System Log. | Phase 6 (Universal Logout) cannot be live-tested; the deny-list/enforcement code is unverified against a real Okta call. | Ruled out, in order: (1) tunnel unreachable — no, confirmed working via a phone on cellular (this Mac's own network independently blocks `*.ngrok-free.dev`, unrelated); (2) wrong endpoint URL — no, the value saved in Okta's Logout section matches `REVOCATION_ENDPOINT_URL` exactly; (3) test user not assigned to the SPA app — no, confirmed assigned; (4) wrong manual trigger — no, confirmed using the documented **Directory → People → [user] → More Actions → Clear user sessions**, not the unrelated "End session" action; (5) Front-channel SLO enabled as a possible fix — no effect, and it's an unrelated (browser-based) feature from GTR (back-channel); (6) ITP/AMFA licensing gate — ruled out, ITP is confirmed enabled on this tenant. Triggered three separate times across these attempts; ngrok's own request inspector shows zero inbound connections each time. Okta's public docs (help.okta.com Universal Logout / Universal Logout revocations pages) don't document dispatch timing, retry behavior, or failure conditions at the level needed to diagnose further — this needs Okta's server-side delivery logs, which aren't customer-visible. | None yet — needs an Okta Support case with the exact trigger timestamps from the System Log, asking specifically why no outbound call was dispatched for this app. Gateway-side code (`RevocationDenyList`, `GlobalTokenRevocationController`, `RevocationCheckWebFilter`) is complete, unit-tested, and ready to correctly process a real call once Okta actually sends one. | Open — escalated to Okta Support | N/A until a live call is received; local `curl` tests against the Gateway directly (bypassing Okta) confirm correct `401` handling for missing/invalid auth. |

## Decision Log

| ID | Date | Decision Needed | Owner | Status | Resolution |
| --- | --- | --- | --- | --- | --- |
| DEC-001 | 2026-07-13 | Provide Okta SPA client configuration and API audience/scopes. | Project owner | Open | |
| DEC-002 | 2026-07-13 | Choose Service 1 -> Service 2 authentication for Phase 1. | Project owner | Open | |
| DEC-003 | 2026-07-13 | Choose local SPA, Gateway, and service ports/origins. | Project owner | Open | |
| DEC-004 | 2026-07-13 | Choose deployment and service-discovery model. | Project owner | Deferred | Phase 5 only |
| DEC-005 | 2026-07-13 | Confirm Okta OIE authenticator policies and real factor-management API flow. | Project owner | Deferred | Implement after initial phases |

## Change Entries

### 2026-07-13 - Planning documentation created

- Created `PLAN.md` with the four-phase implementation plan.
- Created this log to track execution steps, problems, fixes, and decisions.
- No application code or infrastructure has been created yet.

### 2026-07-13 - Phase 1 local scaffold started

- Created the React SPA, Spring Cloud Gateway, Service 1, and Service 2.
- Configured the SPA with the supplied public client ID in ignored local config.
- Added simulated factor add/remove API calls through the Gateway.
- Verified the React production build and all Spring module compilation.
- Verified local health endpoints for Gateway (`8080`), Service 1 (`8081`), and
  Service 2 (`8082`).
- Added unit tests for Service 2 and simulated factor responses; `./gradlew test`
  and `npm run build` pass.

### 2026-07-14 - Phase 3 Service 3 / OBO scaffold started

- Created branch `feature/service3-okta-obo` off the DPoP/PAR fix branch.
- Updated `PLAN.md` Phase 3 with a concrete plan grounded in Okta's token
  exchange guide (developer.okta.com/docs/guides/set-up-token-exchange).
- Added the `service-3` Gradle module (port `8083`, `SCOPE_service3.read` on
  `/api/service-3/**`, no Gateway route — reachable only from Service 1).
- Added `ServiceThreeTokenProvider` in Service 1, performing RFC 8693 token
  exchange (`grant_type=urn:ietf:params:oauth:grant-type:token-exchange`)
  authenticated with `client_secret_basic` against a dedicated Okta Service
  App (Decision 7/8 resolved: dedicated app, client secret to start).
- Added `GET /api/service-1/obo-hello` in `ApiController`, and a third SPA
  diagnostic button plus a `service3Jwt` card in `App.jsx`, mirroring the
  existing `service2Jwt` pattern.
- `./gradlew clean compileJava test` and `npm run build` pass. Live
  verification still needs the Okta admin console setup described in
  `PLAN.md` Phase 3 (dedicated Service App, `service3.read` scope, access
  policy) — `SERVICE_THREE_CLIENT_ID`/`SERVICE_THREE_CLIENT_SECRET` are
  unset by default, so `ServiceThreeTokenProvider` will fail fast with a
  clear error until they're configured.

### 2026-07-14 - Phase 6 Universal Logout implemented, live test blocked

- Created branch `feature/global-token-revocation` off `feature/service3-okta-obo`.
- Updated `PLAN.md` with a concrete Phase 6 plan (Okta's Universal Logout /
  Global Token Revocation guide): request/response contract, why an
  in-memory deny list instead of Redis is the right call for this
  single-process local POC, and a test gate.
- Installed and authenticated `ngrok`; started a tunnel (static free domain
  `frenzied-coyness-subscript.ngrok-free.dev` → `localhost:8080`) so Okta's
  cloud can reach the Gateway. Confirmed the tunnel works from a phone on
  cellular — this Mac's own network independently blocks `*.ngrok-free.dev`
  domains (likely corporate EDR/proxy), unrelated to the tunnel itself.
- Implemented `RevocationDenyList` (in-memory, scheduled sweep),
  `GlobalTokenRevocationController` (`POST /global-token-revocation`, its
  own dedicated JWT validation distinct from the SPA's resource-server
  chain), and `RevocationCheckWebFilter` (rejects requests whose token
  predates its subject's revocation). `./gradlew clean compileJava test`
  passes; local `curl` tests confirm correct `401` handling.
- Configured Okta: Global Token Revocation on the SPA's own app (not the
  service-to-service apps, which have no end-user session to revoke),
  Signed JWT auth, `iss_sub` subject format, tunnel URL as the Logout
  endpoint.
- Live-tested three times (`Clear user sessions` manual trigger); Okta's
  System Log shows `Universal Logout: SUCCESS` each time, but zero calls
  ever reached the Gateway (confirmed via ngrok's own request inspector).
  See ISSUE-004 — escalated to Okta Support; Gateway-side implementation
  is complete and ready pending a real dispatched call.

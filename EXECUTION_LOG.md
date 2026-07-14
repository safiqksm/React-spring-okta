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

## Problems and Fixes

| ID | Date | Problem | Impact | Investigation | Fix | Status | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ISSUE-001 | 2026-07-13 | Workspace is empty; no application code exists. | Implementation has not started. | Confirmed directory contains no project files. | Create structure after Okta and deployment decisions are provided. | Open | Project skeleton builds. |
| ISSUE-002 | 2026-07-13 | A confidential-client secret was shared outside secure configuration. | Secret must not be used in the React SPA or committed to source control. | SPA uses Authorization Code with PKCE and is a public client. | Do not store the secret; rotate it in Okta. Use a secret manager or private-key JWT for a future confidential backend client. | Open | Rotated secret and no secret found in repository. |
| ISSUE-003 | 2026-07-13 | Gateway -> Service 1/2 calls failed with `OAuth2AuthenticationException: Invalid bearer token` after successful DPoP login. | Both UI diagnostic calls and simulated Settings actions failed for any authenticated user with DPoP enabled. | Decompiled `spring-security-oauth2-resource-server-6.5.9.jar`: `BearerTokenAuthenticationFilter` hardcodes a check that rejects any JWT with a `cnf.jkt` claim presented via the `Bearer` scheme (RFC 9449 anti-downgrade safeguard). The Gateway's `InternalAuthorizationHeaderFilter` was rewriting `DPoP` to `Bearer` before proxying, tripping this on every DPoP-bound token. | Removed `InternalAuthorizationHeaderFilter`; Gateway now forwards the `DPoP` scheme and proof header unchanged. Spring Security auto-registers `DPoPAuthenticationConfigurer` on Service 1/2 (no new code needed). Added `server.forward-headers-strategy: framework` to both services so the DPoP proof's `htu` claim (bound to the Gateway's public URL) matches the reconstructed request URL. | Complete (build/tests); browser E2E still pending | `./gradlew clean compileJava test` passes for all three modules. |

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

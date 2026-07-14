# Okta Integration Status

## Objective

The application is a React SPA behind a Spring Cloud Gateway with two Spring services:

- Gateway: `http://localhost:8080`
- Service 1: `http://localhost:8081`
- Service 2: `http://localhost:8082`
- React UI: `http://localhost:5173`

The intended security model is:

1. The SPA signs in with Okta using Authorization Code with PKCE and PAR.
2. The SPA sends DPoP-bound access tokens to the Gateway.
3. The Gateway validates DPoP and JWT claims at the external boundary.
4. Service 1 receives the routed request and calls Service 2 using an OAuth client-credentials token authenticated with `private_key_jwt`.
5. Service 2 protects its internal endpoint with `service2.read`.

## Okta Configuration Provided

- Custom authorization server issuer:
  `https://ntrsoiesys.oktapreview.com/oauth2/aus11aowjcqDJAtVk1d8`
- Authorization-server audience:
  `https://ntrsoiesys.oktapreview.com`
- PAR endpoint:
  `https://ntrsoiesys.oktapreview.com/oauth2/aus11aowjcqDJAtVk1d8/v1/par`
- Service client ID for Service 1 to Service 2:
  `0oa11b0i0rqMoVAxU1d8`
- Internal scope created:
  `service2.read`
- A client private key PEM was placed locally at:
  `secrets/service-1-private-key.pem`

The `secrets/` directory is ignored by Git. The private key must not be committed or logged. A private key previously pasted in chat should be rotated in Okta.

## Implemented Work

### SPA

- Added PAR support in `frontend/src/auth.js`.
- Added DPoP support controlled by `VITE_OKTA_USE_DPOP=true`.
- Added safe browser console logging for PAR, API requests, token audience metadata, and DPoP proof metadata.
- The UI displays DPoP proof metadata only: HTTP method, target URI, issued time, and proof ID. It does not display the raw proof or access token.
- Added two diagnostic actions:
  - **Call Service 1**: `GET /api/service-1/ping`
  - **Call Service 1 to Service 2**: `GET /api/service-1/hello`

### Gateway

- Configured the corrected audience in `gateway/src/main/resources/application.yml`.
- Added a DPoP validation filter in `gateway/src/main/java/com/example/okta/gateway/DpopProofWebFilter.java`.
- Added a routing filter that converts the inbound DPoP authorization scheme to an internal Bearer scheme before routing: `InternalAuthorizationHeaderFilter.java`.
- Added CORS support for DPoP and exposed security diagnostic headers to the SPA.
- Routes now send:
  - `/api/service-1/**` to Service 1
  - `/api/settings/**` to Service 2

### Service 1

- Configured the corrected issuer and audience in `service-1/src/main/resources/application.yml`.
- Added `ServiceTwoTokenProvider.java`, which:
  - reads the local RSA private key;
  - signs a `private_key_jwt` client assertion;
  - obtains an Okta client-credentials token with `service2.read`;
  - calls Service 2 with that token.
- Added safe JWT metadata responses/logs: subject, issuer, expiry, scopes, and a shortened SHA-256 token fingerprint.
- Added inbound authorization diagnostics: scheme, JWT segment count, and fingerprint only.
- Added resource-server failure diagnostics and `X-Authentication-Failure` response header.

### Service 2

- Protected `/api/service-2/**` with `SCOPE_service2.read`.
- Added `SettingsController.java` and moved the Settings API to Service 2.

## Verification Completed

- PAR request was tested directly against Okta and returned `201`.
- SPA login succeeds.
- Gradle tests and the relevant service boot-jar builds completed successfully during implementation.
- Gateway, Service 1, Service 2, and frontend health checks were observed as available after restarts.
- Service 1 was rebuilt and restarted after adding diagnostics; `GET /actuator/health` returned `200`.

## Current Failure

Both UI diagnostic calls fail after successful login:

```text
Gateway token validation failed: service-1: OAuth2AuthenticationException: Invalid bearer token
```

The earlier audience error was corrected by changing `api://default` to:

```text
https://ntrsoiesys.oktapreview.com
```

The remaining failure occurs at Service 1 resource-server authentication, before either controller executes. Consequently, the Service 1 to Service 2 private-key JWT call has not yet been reached in this test path.

## Remaining Investigation Plan

1. Read the new Service 1 runtime diagnostics immediately after a failing request and determine whether Service 1 receives `Bearer` or `DPoP`, whether the token is a three-segment JWT, and the decoder validation cause.
2. Compare the token issuer, audience, key ID, expiry, and DPoP confirmation (`cnf.jkt`) claim against the configured Okta authorization server without exposing the token value.
3. Verify the Gateway authorization-header conversion runs after DPoP validation and before proxying to Service 1.
4. If the token is valid at the Gateway but rejected by Service 1 because it is DPoP-bound, establish an explicit internal trust mechanism instead of forwarding the browser token as a generic Bearer token. Options include a separate gateway-to-service client token or a signed internal identity token.
5. After Service 1 accepts the external request, test `/api/service-1/hello` and then diagnose the Service 1 to Service 2 `private_key_jwt` token request independently.

## Important Files

- `frontend/src/auth.js`
- `frontend/src/api.js`
- `gateway/src/main/java/com/example/okta/gateway/DpopProofWebFilter.java`
- `gateway/src/main/java/com/example/okta/gateway/InternalAuthorizationHeaderFilter.java`
- `gateway/src/main/resources/application.yml`
- `service-1/src/main/java/com/example/okta/serviceone/SecurityConfig.java`
- `service-1/src/main/java/com/example/okta/serviceone/ServiceTwoTokenProvider.java`
- `service-1/src/main/resources/application.yml`
- `service-2/src/main/java/com/example/okta/servicetwo/SettingsController.java`

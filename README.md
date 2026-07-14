# React, Spring Gateway, and Okta

Local-first Phase 1 sample: a React SPA authenticates with Okta, calls a Spring
Cloud Gateway, and reaches Service 1, which calls Service 2. Container and
deployment configuration are intentionally deferred.

## Prerequisites

- Java 21
- Gradle 8.14 or later (a wrapper can be generated after Gradle is installed)
- Node.js 20 or later and npm
- An Okta OIE SPA integration using Authorization Code with PKCE

If Java 21 was installed using Homebrew, configure it in each terminal:

```bash
export JAVA_HOME="$(brew --prefix openjdk@21)"
export PATH="$JAVA_HOME/bin:$PATH"
```

## Okta setup

Configure the SPA integration with these local URLs:

- Sign-in redirect URI: `http://localhost:5173/login/callback`
- Sign-out redirect URI: `http://localhost:5173/`
- Trusted origin: `http://localhost:5173`

Set an access policy that issues access tokens for the API scopes you require.
The default issuer is
`https://ntrsoiesys.oktapreview.com/oauth2/aus11aowjcqDJAtVk1d8`.

### Required SPA configuration

- Application type: **Single-Page Application**.
- Grant type: **Authorization Code** with **PKCE**; do not configure a client
  secret for the SPA.
- OIDC scopes: `openid profile email`.
- Assign the test user (or a group containing the test user) to the SPA app.
- Ensure the custom authorization server access policy permits that user to
  receive the requested scopes.

The `profile` scope is required for the UI to display standard profile claims.
The UI safely falls back to `Not provided` when an individual claim is absent.

## Configure the SPA

```bash
cd frontend
cp .env.example .env
```

Set `VITE_OKTA_CLIENT_ID` in `frontend/.env`. Optionally set
`VITE_OKTA_SCOPES` and `VITE_API_BASE_URL`.

### Pushed Authorization Requests

PAR is enabled by default (`VITE_OKTA_USE_PAR=true`). Before starting the
login, the SPA retrieves the authorization-server metadata, sends the PKCE
authorization request to the advertised `pushed_authorization_request_endpoint`,
and redirects the browser with the short-lived `request_uri` returned by Okta.
The PKCE verifier and state are kept in the Okta Auth JS transaction store for
the callback and token exchange.

The SPA origin must be configured as an Okta Trusted Origin. Do not add a
client secret: this remains a public Authorization Code with PKCE client. Set
`VITE_OKTA_USE_PAR=false` only when temporarily testing the standard redirect
flow. `VITE_OKTA_PAR_ENDPOINT` is available for an explicit endpoint override.

### DPoP and Service-to-Service Authentication

After enabling DPoP on the Okta SPA integration, set the following values in
`frontend/.env` and the Gateway run configuration:

```text
VITE_OKTA_USE_DPOP=true
DPOP_REQUIRED=true
```

The SPA sends `Authorization: DPoP` and a DPoP proof to the Gateway. The
Gateway validates the proof method, target URI, access-token hash, proof key,
and the access token `cnf.jkt` binding. It logs only proof metadata, never the
proof or access token. Service 1 and Service 2 are internal services; the
Gateway converts the validated external request to its internal bearer hop.

Service 1 calls Service 2 with a distinct OAuth 2.0 client-credentials token.
Configure a separate Okta service application for Service 1 with
`private_key_jwt` client authentication, register its public RSA key in Okta,
and grant it the `service2.read` scope. Store the matching PKCS#8 RSA private
key outside this repository, then set these Service 1 run-configuration
variables:

```text
SERVICE_TWO_CLIENT_ID=your-service-1-client-id
SERVICE_TWO_CLIENT_PRIVATE_KEY_PATH=/secure/path/service-1-private-key.pem
SERVICE_TWO_CLIENT_SCOPE=service2.read
SERVICE_TWO_TOKEN_URI=https://your-okta-domain/oauth2/your-authorization-server-id/v1/token
```

Service 2 requires `SCOPE_service2.read` for `/api/service-2/**`. A browser
access token cannot be used for that internal hop. The UI displays only the
DPoP proof method, target URI, issued-at time, and proof ID after a successful
service-chain call; it never renders the raw proof.

## Run locally

To start everything in one terminal:

```bash
./scripts/run-local.sh
```

Or open four terminals from the repository root:

```bash
./gradlew :service-2:bootRun
./gradlew :service-1:bootRun
./gradlew :gateway:bootRun
cd frontend && npm install && npm run dev
```

Services run on `8082`, `8081`, and `8080`; Vite runs on `5173`.

## Import into Spring Tool Suite (STS)

1. Start STS with a Java 21 runtime configured in **Preferences > Java >
   Installed JREs**.
2. Select **File > Import > Gradle > Existing Gradle Project**.
3. Choose this repository root: `React-spring-okta`.
4. Select the Gradle wrapper when STS asks which Gradle distribution to use.
5. Finish the import. STS creates the `gateway`, `service-1`, and `service-2`
   projects.
6. In the **Boot Dashboard**, start in this order: `service-2`, `service-1`,
   then `gateway`.

For each Spring Boot run configuration, open **Run > Run Configurations >
Spring Boot App > [service] > Environment** and set these variables. Use the
same values for all three backend services:

```text
OKTA_ISSUER=https://your-okta-domain/oauth2/your-authorization-server-id
OKTA_AUDIENCE=api://your-api-audience
APP_LOG_LEVEL=DEBUG
```

`OKTA_AUDIENCE` must exactly match the access token's `aud` claim. The default
for this project is `https://ntrsoiesys.oktapreview.com`. Leave `APP_LOG_LEVEL=DEBUG` enabled while
verifying authentication; use `INFO` outside local troubleshooting. Do not add
a client secret to any STS run configuration for the React SPA.

### Equivalent terminal commands

```bash
export JAVA_HOME="$(brew --prefix openjdk@21)"
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew :service-2:bootRun
./gradlew :service-1:bootRun
./gradlew :gateway:bootRun
cd frontend && npm run dev
```

## Verify Phase 1

1. Open `http://localhost:5173` and sign in through Okta.
2. Confirm the Profile panel shows approved ID-token claims only.
3. Use **Call service chain** and confirm the SPA -> Gateway -> Service 1 ->
   Service 2 response.
4. Open **Settings** and use the simulated add/remove factor controls.
5. Confirm success messages appear and no real Okta factor is changed.

## Security notes

- The SPA is a public client and has no client secret.
- Gateway and both services validate incoming JWTs against the configured
  issuer and audience, including signature and expiry. `OKTA_AUDIENCE` must
  match the access token `aud` claim.
- The simulated factor endpoints require a bearer token, but do not contact
  Okta or change enrollment state.
- At local `DEBUG` level, Gateway and both services log
  `jwt_validation_success` after a token is authenticated. A rejected or
  missing bearer token produces `jwt_validation_failure` with the request path
  and failure type. Success logs include subject, issuer, expiry, scopes, and
  a short SHA-256 token fingerprint. Raw JWTs are never logged or returned.
  Set `APP_LOG_LEVEL=INFO` to disable these debug entries.

## Build and test

```bash
./gradlew test
cd frontend && npm run build
```


Access token rejected: Bearer error="invalid_token", error_description="The aud claim is not valid", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1"

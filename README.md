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
for this project is `api://default`. Leave `APP_LOG_LEVEL=DEBUG` enabled while
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

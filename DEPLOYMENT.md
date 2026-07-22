# Deployment: Render (backend) + Vercel (frontend)

This deploys the 4 Spring Boot modules to Render and the React SPA to Vercel,
both via GitHub — pushing to the connected branch triggers a redeploy on
each platform. See `render.yaml` for the Render Blueprint definition.

**Why**: primarily so the Gateway has a stable, real public HTTPS URL to
give Okta for Global Token Revocation (Phase 6 / `EXECUTION_LOG.md`
ISSUE-004) — no longer dependent on this machine staying on with a live
ngrok tunnel, and it also rules out "Okta might filter tunnel-service
domains" as an explanation for the dispatch failures we saw.

## Architecture

All 4 services are `type: web` (public `onrender.com` URLs), so everything
stays on Render's **free** plan. This isn't the ideal architecture: nothing
outside this backend ever calls `service-1/2/3` directly, so they'd
naturally be Render **private services** (`type: pserv`) — but Render's free
plan explicitly isn't available for private services, only for `type: web`.
They're still protected by their own existing OAuth2/JWT resource-server
validation regardless of network reachability; they just aren't
network-isolated. If that isolation matters enough to pay for later, switch
their `type` to `pserv` and `plan` to `starter` (~$7/month each) in
`render.yaml`.

```
Browser / Vercel SPA ──HTTPS──▶ react-spring-okta-gateway (public, type: web)
                                        │  SERVICE_ONE_URL (Render internal networking)
                                        ▼
                                 react-spring-okta-service-1 (public, type: web)
                                   │ SERVICE_TWO_URL          │ SERVICE_THREE_URL
                                   ▼                          ▼
                          react-spring-okta-service-2   react-spring-okta-service-3
                              (public, type: web)           (public, type: web)

Okta Cloud ──HTTPS POST──▶ react-spring-okta-gateway /global-token-revocation
```

## Render setup

1. **Dashboard → New → Blueprint**, connect this GitHub repo, select this
   branch. Render reads `render.yaml` and proposes all 4 services at once.
2. Before the first deploy completes, fill in the `sync: false` values it
   prompts for:
   - `react-spring-okta-service-1`:
     - `SERVICE_TWO_CLIENT_PRIVATE_KEY` — paste the full contents of
       `secrets/service-1-private-key.pem` (this repo's gitignored local
       copy). Real newlines or literal `\n` both work.
     - `SERVICE_THREE_CLIENT_ID`, `SERVICE_THREE_CLIENT_SECRET` — from the
       dedicated Okta Service App created for Phase 3's OBO flow (same
       values as your local `secrets/service-1.env`).
   - `react-spring-okta-gateway`:
     - `REVOCATION_ENDPOINT_URL` — leave blank for the very first deploy
       (see step 4).
3. Deploy. `service-1/2/3` need no further input — `SERVICE_TWO_URL` and
   `SERVICE_THREE_URL` on `service-1`, and `SERVICE_ONE_URL` on `gateway`,
   are wired automatically via Render's `fromService` references to each
   service's internal `host:port` (Render's internal networking works for
   `type: web` services too, not just `pserv`).
4. **Two-step step for `REVOCATION_ENDPOINT_URL`** (a real chicken-and-egg:
   the Gateway needs to know its own public URL, which Render only assigns
   once the service exists): after the first deploy, copy
   `react-spring-okta-gateway`'s `*.onrender.com` URL from the Render
   dashboard, set `REVOCATION_ENDPOINT_URL` to
   `https://<that-host>.onrender.com/global-token-revocation`, and redeploy
   the Gateway service. This is the same final URL you'll paste into Okta.

### Free tier vs. always-on

`render.yaml` defaults every service to the **free** plan — no cost, but
free services spin down after inactivity and take tens of seconds to cold
-start on the next request. For the Gateway specifically, that matters:
Okta's Universal Logout dispatch may have a request timeout, and a cold
Gateway could cause a delivery to silently fail or be reported as an error
on Okta's side, muddying exactly the signal we're trying to get. If you're
actively testing GTR, consider upgrading `react-spring-okta-gateway` to a
paid **Starter** plan (always-on) — `service-1/2/3` can stay on free tier,
since a slow first hit to them just adds latency, not dropped webhooks. All
4 services share the same Render account's 750 free instance-hours/month
(shared with any other free services already in that account) while idle
time doesn't count against that budget.

## Vercel setup (frontend)

Since Vercel's already connected to this GitHub account:

1. **Add New → Project**, select this repo.
2. **Root Directory**: `frontend` (this is a monorepo — the SPA isn't at
   the repo root).
3. Framework preset: Vite (auto-detected). Build command `npm run build`,
   output directory `dist` (Vercel should auto-fill these).
4. Environment variables (from `frontend/.env.example`):
   - `VITE_OKTA_CLIENT_ID` — your SPA's Okta client ID
   - `VITE_API_BASE_URL` — `https://<gateway-host>.onrender.com` (the
     Gateway's Render URL, no trailing slash, no path)
   - `VITE_OKTA_ISSUER`, `VITE_OKTA_SCOPES`, `VITE_OKTA_USE_PAR`,
     `VITE_OKTA_USE_DPOP` — same values you use locally, if overridden
5. Deploy.

## Okta admin console follow-ups (required, not optional)

Once both are deployed:

1. **Trusted Origins**: add the Vercel deployment's URL (e.g.
   `https://your-app.vercel.app`) as a Trusted Origin, same as
   `http://localhost:5173` already is.
2. **SPA app's Sign-in/Sign-out redirect URIs**: add the Vercel URL's
   `/login/callback` and root path alongside the existing localhost ones.
3. **Global Token Revocation Logout endpoint URL**: replace the ngrok URL
   with `https://<gateway-host>.onrender.com/global-token-revocation` —
   this is the actual point of this deployment.

Separately (not Okta-side): set `ALLOWED_ORIGIN` on
`react-spring-okta-gateway` to your Vercel deployment's origin (e.g.
`https://your-app.vercel.app`, no trailing slash) so CORS allows it
alongside `http://localhost:5173` — no code change needed, just the env
var and a redeploy.

## Verifying

- `https://<gateway-host>.onrender.com/actuator/health` → `{"status":"UP"}`
- Sign in at the Vercel URL, exercise all three tabs — same behavior as
  local, just talking to Render instead of `localhost`.
- Trigger **Clear user sessions** in Okta (same steps as
  `EXECUTION_LOG.md` ISSUE-004) and check `react-spring-okta-gateway`'s
  Render logs for `gtr_accepted`/`gtr_rejected` — this is the real test
  Phase 6 has been blocked on.

// Builds the end-to-end hop-by-hop flow shown in the UI, including the raw
// JWTs and the actual request/response of each hop, so the UI can decode and
// display everything per hop — this app's teaching/demo purpose, for the
// user's own tokens.

import { decodeJwt } from './decodeJwt.js';

function claimsFromJwt(raw) {
  const decoded = raw ? decodeJwt(raw) : null;
  if (!decoded) return null;
  const { payload } = decoded;
  return {
    subject: payload.sub,
    issuer: payload.iss,
    expiresAt: payload.exp ? new Date(payload.exp * 1000).toISOString() : undefined,
    scopes: typeof payload.scp === 'string' ? payload.scp.split(' ') : payload.scp || [],
    raw,
  };
}

function browserToOkta(idToken, accessToken, loginTrace) {
  return {
    id: '0',
    from: 'Browser',
    to: 'Okta',
    badge: 'PAR + Authorization Code + PKCE',
    ok: Boolean(idToken || accessToken),
    claims: claimsFromJwt(idToken),
    tokens: [
      idToken ? { label: 'ID token (JWT)', raw: idToken } : null,
      accessToken ? { label: 'Access token (JWT)', raw: accessToken } : null,
    ].filter(Boolean),
    http: loginTrace?.par,
    assertion: {
      summary: 'Sign-in pushed the authorization request (PKCE code_challenge, scope, redirect_uri) to Okta’s PAR endpoint, then redirected the browser to /authorize with the returned request_uri. After you authenticated, Okta redirected back with an authorization code, which okta-auth-js exchanged for tokens — that final code exchange happens inside the SDK and isn’t separately captured here, but the PAR request/response is, and the tokens it produced are shown below.',
      details: loginTrace?.authorizeUrl ? [['Authorize redirect', loginTrace.authorizeUrl]] : [],
    },
  };
}

function browserToGateway(validatedJwt, dpopProof, http) {
  return {
    id: 'A',
    from: 'Browser',
    to: 'Gateway',
    badge: 'DPoP',
    ok: Boolean(dpopProof),
    claims: validatedJwt,
    http,
    assertion: {
      summary: 'DPoP-bound access token (Authorization: DPoP) plus a DPoP proof header, bound to this exact method and URL.',
      details: dpopProof
        ? [
            ['Method', dpopProof.method],
            ['Target URI', dpopProof.targetUri],
            ['Issued at', dpopProof.issuedAt],
            ['Proof ID', dpopProof.proofId],
          ]
        : [],
      proof: dpopProof ? { label: 'DPoP proof (JWT)', raw: dpopProof.raw } : null,
    },
  };
}

function gatewayToServiceOne(validatedJwt, http) {
  return {
    id: 'B',
    from: 'Gateway',
    to: 'Service 1',
    badge: 'DPoP (validated)',
    ok: Boolean(validatedJwt),
    claims: validatedJwt,
    http,
    assertion: {
      summary: 'The Gateway forwards the same DPoP scheme and proof unchanged; Service 1 independently validates the DPoP proof and the JWT (issuer, audience, signature, expiry).',
      details: [],
    },
  };
}

export function serviceOneFlow({ validatedJwt, dpopProof, httpTrace, idToken, accessToken, loginTrace }) {
  return [
    browserToOkta(idToken, accessToken, loginTrace),
    browserToGateway(validatedJwt, dpopProof, httpTrace?.browserToGateway),
    gatewayToServiceOne(validatedJwt, httpTrace?.hops?.gatewayToService1),
  ];
}

export function serviceTwoFlow({ validatedJwt, service2Jwt, dpopProof, httpTrace, idToken, accessToken, loginTrace }) {
  return [
    browserToOkta(idToken, accessToken, loginTrace),
    browserToGateway(validatedJwt, dpopProof, httpTrace?.browserToGateway),
    gatewayToServiceOne(validatedJwt, httpTrace?.hops?.gatewayToService1),
    {
      id: 'C',
      from: 'Service 1',
      to: 'Okta',
      badge: 'client_credentials + private_key_jwt',
      ok: Boolean(service2Jwt),
      claims: service2Jwt,
      http: httpTrace?.hops?.service1ToOkta,
      assertion: {
        summary: 'Service 1 POSTs to Okta’s /v1/token endpoint, authenticating as its own client with a signed private_key_jwt assertion (RS256) instead of a shared secret. Okta returns a new access token scoped to service2.read.',
        details: [
          ['Grant type', 'client_credentials'],
          ['Client assertion type', 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer'],
          ['Scope requested', 'service2.read'],
        ],
      },
    },
    {
      id: 'D',
      from: 'Service 1',
      to: 'Service 2',
      badge: 'Bearer',
      ok: Boolean(service2Jwt),
      claims: service2Jwt,
      http: httpTrace?.hops?.service1ToService2,
      assertion: {
        summary: 'Service 1 presents the client-credentials token from Okta to Service 2 as Authorization: Bearer. Service 2 validates it and requires SCOPE_service2.read.',
        details: [],
      },
      verify:
        service2Jwt && validatedJwt
          ? [{ pass: service2Jwt.subject !== validatedJwt.subject, label: 'Distinct service identity — not your own subject' }]
          : [],
    },
  ];
}

export function serviceThreeFlow({ validatedJwt, service3Jwt, dpopProof, httpTrace, idToken, accessToken, loginTrace }) {
  return [
    browserToOkta(idToken, accessToken, loginTrace),
    browserToGateway(validatedJwt, dpopProof, httpTrace?.browserToGateway),
    gatewayToServiceOne(validatedJwt, httpTrace?.hops?.gatewayToService1),
    {
      id: 'C',
      from: 'Service 1',
      to: 'Okta',
      badge: 'token-exchange + client_secret_basic',
      ok: Boolean(service3Jwt),
      claims: service3Jwt,
      http: httpTrace?.hops?.service1ToOkta,
      assertion: {
        summary: 'Service 1 POSTs to Okta’s /v1/token endpoint with the RFC 8693 token-exchange grant, presenting your own access token (from hop B) as subject_token, and authenticates itself with client_secret_basic. Okta returns a new access token that still carries your subject, narrowed to service3.read.',
        details: [
          ['Grant type', 'urn:ietf:params:oauth:grant-type:token-exchange'],
          ['Subject token type', 'urn:ietf:params:oauth:token-type:access_token'],
          ['Scope requested', 'service3.read'],
        ],
      },
    },
    {
      id: 'D',
      from: 'Service 1',
      to: 'Service 3',
      badge: 'Bearer',
      ok: Boolean(service3Jwt),
      claims: service3Jwt,
      http: httpTrace?.hops?.service1ToService3,
      assertion: {
        summary: 'Service 1 presents the exchanged token to Service 3 as Authorization: Bearer. Service 3 validates it and requires SCOPE_service3.read.',
        details: [],
      },
      verify:
        service3Jwt && validatedJwt
          ? [
              { pass: service3Jwt.subject === validatedJwt.subject, label: 'Same user identity preserved (On-Behalf-Of)' },
              {
                pass: service3Jwt.scopes?.length === 1 && service3Jwt.scopes[0] === 'service3.read',
                label: 'Scope narrowed to service3.read only',
              },
            ]
          : [],
    },
  ];
}

import { OktaAuth } from '@okta/okta-auth-js';

const issuer = import.meta.env.VITE_OKTA_ISSUER
  || 'https://ntrsoiesys.oktapreview.com/oauth2/aus11aowjcqDJAtVk1d8';
const usePar = import.meta.env.VITE_OKTA_USE_PAR !== 'false';
const useDpop = import.meta.env.VITE_OKTA_USE_DPOP === 'true';

export const isConfigured = Boolean(import.meta.env.VITE_OKTA_CLIENT_ID)
  && import.meta.env.VITE_OKTA_CLIENT_ID !== 'REPLACE_WITH_OKTA_SPA_CLIENT_ID';

export const oktaAuth = new OktaAuth({
  issuer,
  clientId: import.meta.env.VITE_OKTA_CLIENT_ID || 'REPLACE_WITH_OKTA_SPA_CLIENT_ID',
  redirectUri: `${window.location.origin}/login/callback`,
  scopes: (import.meta.env.VITE_OKTA_SCOPES || 'openid profile email').split(' '),
  pkce: true,
  dpop: useDpop,
  dpopOptions: { allowBearerTokens: false }
});

function valueOrUndefined(value) {
  return value === undefined || value === null || value === '' ? undefined : value;
}

function createTransactionMetadata(tokenParams, metadata) {
  return {
    issuer: oktaAuth.options.issuer,
    urls: {
      issuer: oktaAuth.options.issuer,
      authorizeUrl: metadata.authorization_endpoint,
      userinfoUrl: metadata.userinfo_endpoint,
      tokenUrl: metadata.token_endpoint,
      revokeUrl: metadata.revocation_endpoint,
      logoutUrl: metadata.end_session_endpoint
    },
    clientId: tokenParams.clientId,
    redirectUri: tokenParams.redirectUri,
    responseType: tokenParams.responseType,
    responseMode: tokenParams.responseMode,
    scopes: tokenParams.scopes,
    state: tokenParams.state,
    nonce: tokenParams.nonce,
    ignoreSignature: tokenParams.ignoreSignature,
    acrValues: tokenParams.acrValues,
    extraParams: tokenParams.extraParams,
    codeVerifier: tokenParams.codeVerifier,
    codeChallengeMethod: tokenParams.codeChallengeMethod,
    codeChallenge: tokenParams.codeChallenge
  };
}

function parRequestBody(tokenParams) {
  return Object.fromEntries(Object.entries({
    client_id: tokenParams.clientId,
    code_challenge: tokenParams.codeChallenge,
    code_challenge_method: tokenParams.codeChallengeMethod,
    display: tokenParams.display,
    idp: tokenParams.idp,
    idp_scope: Array.isArray(tokenParams.idpScope) ? tokenParams.idpScope.join(' ') : tokenParams.idpScope,
    login_hint: tokenParams.loginHint,
    max_age: tokenParams.maxAge,
    nonce: tokenParams.nonce,
    prompt: tokenParams.prompt,
    redirect_uri: tokenParams.redirectUri,
    response_mode: tokenParams.responseMode,
    response_type: Array.isArray(tokenParams.responseType)
      ? tokenParams.responseType.join(' ')
      : tokenParams.responseType,
    scope: tokenParams.scopes?.join(' '),
    state: tokenParams.state,
    ...tokenParams.extraParams
  }).filter(([, value]) => valueOrUndefined(value) !== undefined));
}

async function getOpenIdConfiguration() {
  const response = await fetch(`${issuer}/.well-known/openid-configuration`);
  if (!response.ok) {
    throw new Error(`Unable to retrieve Okta discovery metadata (${response.status}).`);
  }
  return response.json();
}

export async function signInWithPar(originalUri) {
  if (!usePar) {
    console.debug('auth.redirect.start', { flow: 'authorization_code_pkce', par: false });
    return oktaAuth.signInWithRedirect({ originalUri });
  }

  console.debug('auth.par.start', { issuer, flow: 'authorization_code_pkce' });
  const tokenParams = await oktaAuth.token.prepareTokenParams();
  const metadata = await getOpenIdConfiguration();
  const parEndpoint = import.meta.env.VITE_OKTA_PAR_ENDPOINT
    || metadata.pushed_authorization_request_endpoint;

  if (!parEndpoint) {
    console.debug('auth.par.unavailable', { issuer });
    throw new Error('This Okta authorization server does not advertise a PAR endpoint.');
  }

  console.debug('auth.par.submit', { endpoint: parEndpoint });
  const response = await fetch(parEndpoint, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/x-www-form-urlencoded'
    },
    body: new URLSearchParams(parRequestBody(tokenParams))
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || !payload.request_uri) {
    console.debug('auth.par.rejected', { status: response.status, error: payload.error });
    throw new Error(payload.error_description || `Okta PAR request failed (${response.status}).`);
  }

  console.debug('auth.par.accepted', { expiresInSeconds: payload.expires_in });
  oktaAuth.setOriginalUri(originalUri);
  oktaAuth.transactionManager.save(createTransactionMetadata(tokenParams, metadata));

  const authorizeUrl = new URL(metadata.authorization_endpoint);
  authorizeUrl.searchParams.set('client_id', tokenParams.clientId);
  authorizeUrl.searchParams.set('request_uri', payload.request_uri);
  console.debug('auth.authorize.redirect', {
    endpoint: authorizeUrl.origin + authorizeUrl.pathname,
    usesRequestUri: true
  });
  window.location.assign(authorizeUrl.toString());
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

async function request(oktaAuth, path, options = {}) {
  const accessToken = await oktaAuth.getAccessToken();
  if (!accessToken) {
    throw new Error('Your session has no access token. Please sign in again.');
  }

  const url = `${apiBaseUrl}${path}`;
  const method = options.method || 'GET';
  const accessTokenMetadata = await oktaAuth.tokenManager.get('accessToken');
  console.debug('api.access_token_metadata', { audience: accessTokenMetadata?.claims?.aud });
  const dpopHeaders = oktaAuth.options.dpop
    ? await oktaAuth.getDPoPAuthorizationHeaders({ url, method })
    : { Authorization: `Bearer ${accessToken}` };
  const requestHeaders = { ...dpopHeaders, 'Content-Type': 'application/json', ...options.headers };

  console.debug('api.request', { method, path, dpop: oktaAuth.options.dpop === true });
  const response = await fetch(url, { ...options, headers: requestHeaders });

  if (!response.ok) {
    const dpopFailure = response.headers.get('X-DPoP-Validation');
    if (dpopFailure) {
      throw new Error(`DPoP validation failed: ${dpopFailure}.`);
    }
    const authenticationFailure = response.headers.get('WWW-Authenticate');
    const gatewayFailure = response.headers.get('X-Authentication-Failure');
    if (gatewayFailure) {
      throw new Error(`Gateway token validation failed: ${gatewayFailure}.`);
    }
    if (authenticationFailure) {
      throw new Error(`Access token rejected: ${authenticationFailure}.`);
    }
    throw new Error(`Request failed with status ${response.status}.`);
  }
  const result = await response.json();
  const responseHeaders = {};
  response.headers.forEach((value, key) => { responseHeaders[key] = value; });

  return {
    ...result,
    dpopProof: dpopHeaders.DPoP ? dpopProofDetails(dpopHeaders.DPoP) : null,
    browserToGateway: {
      request: { method, url, headers: requestHeaders, body: options.body || null },
      response: { status: response.status, headers: responseHeaders, body: result }
    }
  };
}

function dpopProofDetails(proof) {
  const encodedPayload = proof.split('.')[1];
  const payload = JSON.parse(atob(encodedPayload.replace(/-/g, '+').replace(/_/g, '/')));
  return {
    method: payload.htm,
    targetUri: payload.htu,
    issuedAt: payload.iat ? new Date(payload.iat * 1000).toISOString() : undefined,
    proofId: payload.jti,
    raw: proof
  };
}

export function getServiceChain(oktaAuth) {
  return request(oktaAuth, '/api/service-1/hello');
}

export function getServiceThreeObo(oktaAuth) {
  return request(oktaAuth, '/api/service-1/obo-hello');
}

export function getServiceOne(oktaAuth) {
  return request(oktaAuth, '/api/service-1/ping');
}

export function changeFactor(oktaAuth, action, factorType) {
  return request(oktaAuth, `/api/settings/factors/${action}`, {
    method: 'POST',
    body: JSON.stringify({ factorType })
  });
}

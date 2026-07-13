const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

async function request(oktaAuth, path, options = {}) {
  const accessToken = await oktaAuth.getAccessToken();
  if (!accessToken) {
    throw new Error('Your session has no access token. Please sign in again.');
  }

  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
      ...options.headers
    }
  });

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}.`);
  }
  return response.json();
}

export function getServiceChain(oktaAuth) {
  return request(oktaAuth, '/api/service-1/hello');
}

export function changeFactor(oktaAuth, action, factorType) {
  return request(oktaAuth, `/api/settings/factors/${action}`, {
    method: 'POST',
    body: JSON.stringify({ factorType })
  });
}


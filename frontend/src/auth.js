import { OktaAuth } from '@okta/okta-auth-js';

const issuer = import.meta.env.VITE_OKTA_ISSUER
  || 'https://ntrsoiesys.oktapreview.com/oauth2/aus11aowjcqDJAtVk1d8';

export const isConfigured = Boolean(import.meta.env.VITE_OKTA_CLIENT_ID)
  && import.meta.env.VITE_OKTA_CLIENT_ID !== 'REPLACE_WITH_OKTA_SPA_CLIENT_ID';

export const oktaAuth = new OktaAuth({
  issuer,
  clientId: import.meta.env.VITE_OKTA_CLIENT_ID || 'REPLACE_WITH_OKTA_SPA_CLIENT_ID',
  redirectUri: `${window.location.origin}/login/callback`,
  scopes: (import.meta.env.VITE_OKTA_SCOPES || 'openid profile email').split(' '),
  pkce: true
});


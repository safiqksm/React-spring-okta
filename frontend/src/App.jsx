import React from 'react';
import { LoginCallback, useOktaAuth } from '@okta/okta-react';
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { isConfigured } from './auth';
import { changeFactor, getServiceChain } from './api';

function Layout({ children }) {
  const { oktaAuth, authState } = useOktaAuth();
  const location = useLocation();
  const signedIn = authState?.isAuthenticated;

  const signIn = () => oktaAuth.signInWithRedirect({ originalUri: location.pathname });

  return (
    <div className="shell">
      <header className="topbar">
        <Link className="brand" to="/">Identity Portal</Link>
        <nav>
          {signedIn && <Link to="/settings">Settings</Link>}
          {signedIn
            ? <button className="link-button" onClick={() => oktaAuth.signOut()}>Sign out</button>
            : <button className="primary-button" onClick={signIn}>Sign in</button>}
        </nav>
      </header>
      <main className="content">{children}</main>
    </div>
  );
}

function ProtectedPage({ children }) {
  const { authState } = useOktaAuth();
  if (!authState) return <p>Checking your session…</p>;
  return authState.isAuthenticated ? children : <Navigate to="/" replace />;
}

function Home() {
  const { oktaAuth, authState } = useOktaAuth();
  const [message, setMessage] = React.useState('');
  const [validatedJwt, setValidatedJwt] = React.useState(null);
  const claims = authState?.idToken?.claims;

  async function callServices() {
    try {
      const result = await getServiceChain(oktaAuth);
      setMessage(result.message);
      setValidatedJwt(result.jwt);
    } catch (error) {
      setMessage(error.message);
      setValidatedJwt(null);
    }
  }

  return <Layout>
    <section className="hero"><p className="eyebrow">LOCAL DEVELOPMENT</p><h1>Secure service access</h1><p>Sign in with Okta to access protected APIs through the Gateway.</p></section>
    {!isConfigured && <div className="notice">Set `VITE_OKTA_CLIENT_ID` in `frontend/.env` before signing in.</div>}
    {authState?.isAuthenticated && <div className="grid">
      <section className="card"><h2>User Profile</h2><dl>
        <dt>Name</dt><dd>{claims?.name || 'Not provided'}</dd>
        <dt>Email</dt><dd>{claims?.email || 'Not provided'}</dd>
        <dt>Subject</dt><dd className="wrap">{claims?.sub}</dd>
      </dl></section>
      <section className="card"><h2>Service chain</h2><p>Call Service 1 through the Gateway. Service 1 then calls Service 2.</p><button className="primary-button" onClick={callServices}>Call service chain</button>{message && <p className="result" role="status">{message}</p>}</section>
      {validatedJwt && <section className="card"><h2>Validated access token</h2><dl>
        <dt>Subject</dt><dd className="wrap">{validatedJwt.subject}</dd>
        <dt>Issuer</dt><dd className="wrap">{validatedJwt.issuer}</dd>
        <dt>Expires</dt><dd>{validatedJwt.expiresAt}</dd>
        <dt>Scopes</dt><dd>{validatedJwt.scopes?.join(', ') || 'None'}</dd>
        <dt>Fingerprint</dt><dd className="token-fingerprint">{validatedJwt.tokenFingerprint}</dd>
      </dl></section>}
    </div>}
  </Layout>;
}

function Settings() {
  const { oktaAuth } = useOktaAuth();
  const [factorType, setFactorType] = React.useState('Authenticator app');
  const [message, setMessage] = React.useState('');

  async function submit(action) {
    try {
      const result = await changeFactor(oktaAuth, action, factorType);
      setMessage(result.message);
    } catch (error) {
      setMessage(error.message);
    }
  }

  return <Layout><section className="page-heading"><p className="eyebrow">ACCOUNT</p><h1>Settings</h1><p>Factor changes are simulated in Phase 1 and do not update your Okta account.</p></section>
    <section className="card settings-card"><label htmlFor="factorType">Factor type</label><select id="factorType" value={factorType} onChange={(event) => setFactorType(event.target.value)}><option>Authenticator app</option><option>Phone</option><option>Security key</option></select><div className="actions"><button className="primary-button" onClick={() => submit('add')}>Add factor</button><button className="secondary-button" onClick={() => submit('remove')}>Remove factor</button></div>{message && <p className="result" role="status">{message}</p>}</section>
  </Layout>;
}

export default function App() {
  return <Routes>
    <Route path="/" element={<Home />} />
    <Route path="/login/callback" element={<LoginCallback />} />
    <Route path="/settings" element={<ProtectedPage><Settings /></ProtectedPage>} />
  </Routes>;
}

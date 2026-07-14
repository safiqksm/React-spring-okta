import React from 'react';
import { LoginCallback, useOktaAuth } from '@okta/okta-react';
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { isConfigured, signInWithPar } from './auth';
import { changeFactor, getServiceChain, getServiceOne } from './api';

function Layout({ children }) {
  const { oktaAuth, authState } = useOktaAuth();
  const location = useLocation();
  const signedIn = authState?.isAuthenticated;

  const signIn = () => signInWithPar(location.pathname).catch((error) => {
    console.error('Okta sign-in could not start.', error);
  });

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
  const [dpopProof, setDpopProof] = React.useState(null);
  const [serviceResult, setServiceResult] = React.useState('');
  const claims = authState?.idToken?.claims;

  async function callServices() {
    try {
      const result = await getServiceChain(oktaAuth);
      setMessage(result.message);
      setValidatedJwt(result.jwt);
      setDpopProof(result.dpopProof);
    } catch (error) {
      setMessage(error.message);
      setValidatedJwt(null);
      setDpopProof(null);
    }
  }

  async function callServiceOne() {
    try {
      const result = await getServiceOne(oktaAuth);
      setMessage('Service 1 call succeeded.');
      setServiceResult(result.message);
      setValidatedJwt(result.jwt);
      setDpopProof(result.dpopProof);
    } catch (error) {
      setMessage(error.message);
      setServiceResult('');
      setValidatedJwt(null);
      setDpopProof(null);
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
      <section className="card"><h2>Service checks</h2><p>Call Service 1 first, then test the Service 1 to Service 2 private-key JWT hop.</p><div className="actions"><button className="primary-button" onClick={callServiceOne}>Call Service 1</button><button className="secondary-button" onClick={callServices}>Call Service 1 to Service 2</button></div>{message && <p className="result" role="status">{message}{serviceResult && ` ${serviceResult}`}</p>}</section>
      {validatedJwt && <section className="card"><h2>Validated access token</h2><dl>
        <dt>Subject</dt><dd className="wrap">{validatedJwt.subject}</dd>
        <dt>Issuer</dt><dd className="wrap">{validatedJwt.issuer}</dd>
        <dt>Expires</dt><dd>{validatedJwt.expiresAt}</dd>
        <dt>Scopes</dt><dd>{validatedJwt.scopes?.join(', ') || 'None'}</dd>
        <dt>Fingerprint</dt><dd className="token-fingerprint">{validatedJwt.tokenFingerprint}</dd>
      </dl></section>}
      {dpopProof && <section className="card"><h2>DPoP proof</h2><dl>
        <dt>Method</dt><dd>{dpopProof.method}</dd>
        <dt>Target URI</dt><dd className="wrap">{dpopProof.targetUri}</dd>
        <dt>Issued at</dt><dd>{dpopProof.issuedAt}</dd>
        <dt>Proof ID</dt><dd className="wrap token-fingerprint">{dpopProof.proofId}</dd>
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

import React from 'react';
import { LoginCallback, useOktaAuth } from '@okta/okta-react';
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { getLoginTrace, isConfigured, signInWithPar } from './auth';
import { changeFactor, getServiceChain, getServiceOne, getServiceThreeObo } from './api';
import { serviceOneFlow, serviceTwoFlow, serviceThreeFlow } from './flow';
import FlowView from './components/FlowView.jsx';

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

const TABS = [
  { id: 'service1', label: 'Service 1' },
  { id: 'service2', label: 'Service 1 → Service 2' },
  { id: 'service3', label: 'Service 1 → Service 3 (OBO)' }
];

function Home() {
  const { oktaAuth, authState } = useOktaAuth();
  const [activeTab, setActiveTab] = React.useState('service1');
  const [message, setMessage] = React.useState('');
  const [validatedJwt, setValidatedJwt] = React.useState(null);
  const [service2Jwt, setService2Jwt] = React.useState(null);
  const [service3Jwt, setService3Jwt] = React.useState(null);
  const [dpopProof, setDpopProof] = React.useState(null);
  const [httpTrace, setHttpTrace] = React.useState(null);
  const [serviceResult, setServiceResult] = React.useState('');
  const claims = authState?.idToken?.claims;
  const idToken = authState?.idToken?.idToken;
  const accessToken = authState?.accessToken?.accessToken;
  const loginTrace = getLoginTrace();

  function selectTab(tabId) {
    setActiveTab(tabId);
    setMessage('');
    setServiceResult('');
  }

  async function callServices() {
    try {
      const result = await getServiceChain(oktaAuth);
      setMessage(result.message);
      setValidatedJwt(result.jwt);
      setService2Jwt(result.service2Jwt);
      setService3Jwt(null);
      setDpopProof(result.dpopProof);
      setHttpTrace({ browserToGateway: result.browserToGateway, hops: result.hops });
    } catch (error) {
      setMessage(error.message);
      setValidatedJwt(null);
      setService2Jwt(null);
      setService3Jwt(null);
      setDpopProof(null);
      setHttpTrace(null);
    }
  }

  async function callServiceOne() {
    try {
      const result = await getServiceOne(oktaAuth);
      setMessage('Service 1 call succeeded.');
      setServiceResult(result.message);
      setValidatedJwt(result.jwt);
      setService2Jwt(null);
      setService3Jwt(null);
      setDpopProof(result.dpopProof);
      setHttpTrace({ browserToGateway: result.browserToGateway, hops: result.hops });
    } catch (error) {
      setMessage(error.message);
      setServiceResult('');
      setValidatedJwt(null);
      setService2Jwt(null);
      setService3Jwt(null);
      setDpopProof(null);
      setHttpTrace(null);
    }
  }

  async function callServiceThreeObo() {
    try {
      const result = await getServiceThreeObo(oktaAuth);
      setMessage(result.message);
      setValidatedJwt(result.jwt);
      setService2Jwt(null);
      setService3Jwt(result.service3Jwt);
      setDpopProof(result.dpopProof);
      setHttpTrace({ browserToGateway: result.browserToGateway, hops: result.hops });
    } catch (error) {
      setMessage(error.message);
      setValidatedJwt(null);
      setService2Jwt(null);
      setService3Jwt(null);
      setDpopProof(null);
      setHttpTrace(null);
    }
  }

  const loginContext = { idToken, accessToken, loginTrace };
  const flowSteps = activeTab === 'service1'
    ? serviceOneFlow({ validatedJwt, dpopProof, httpTrace, ...loginContext })
    : activeTab === 'service2'
      ? serviceTwoFlow({ validatedJwt, service2Jwt, dpopProof, httpTrace, ...loginContext })
      : serviceThreeFlow({ validatedJwt, service3Jwt, dpopProof, httpTrace, ...loginContext });

  return <Layout>
    <section className="hero"><p className="eyebrow">LOCAL DEVELOPMENT</p><h1>Secure service access</h1><p>Sign in with Okta to access protected APIs through the Gateway.</p></section>
    {!isConfigured && <div className="notice">Set `VITE_OKTA_CLIENT_ID` in `frontend/.env` before signing in.</div>}
    {authState?.isAuthenticated && <>
      <section className="card"><h2>User Profile</h2><dl>
        <dt>Name</dt><dd>{claims?.name || 'Not provided'}</dd>
        <dt>Email</dt><dd>{claims?.email || 'Not provided'}</dd>
        <dt>Subject</dt><dd className="wrap">{claims?.sub}</dd>
      </dl></section>

      <div className="tabs" role="tablist">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            role="tab"
            aria-selected={activeTab === tab.id}
            className={activeTab === tab.id ? 'tab active' : 'tab'}
            onClick={() => selectTab(tab.id)}
          >{tab.label}</button>
        ))}
      </div>

      {activeTab === 'service1' && <section className="card">
        <h2>Call Service 1</h2>
        <p>Sends a DPoP-bound request straight to Service 1 through the Gateway.</p>
        <div className="actions"><button className="primary-button" onClick={callServiceOne}>Call Service 1</button></div>
        {message && <p className="result" role="status">{message}{serviceResult && ` ${serviceResult}`}</p>}
      </section>}

      {activeTab === 'service2' && <section className="card">
        <h2>Call Service 1 → Service 2</h2>
        <p>Service 1 calls Service 2 using a separate private-key JWT client-credentials token — a distinct service identity, not yours.</p>
        <div className="actions"><button className="primary-button" onClick={callServices}>Call Service 1 to Service 2</button></div>
        {message && <p className="result" role="status">{message}</p>}
      </section>}

      {activeTab === 'service3' && <section className="card">
        <h2>Call Service 1 → Service 3 (On-Behalf-Of)</h2>
        <p>Service 1 exchanges your own access token with Okta (RFC 8693 token exchange) for a narrowly scoped token that still carries your identity.</p>
        <div className="actions"><button className="primary-button" onClick={callServiceThreeObo}>Call Service 1 to Service 3 (OBO)</button></div>
        {message && <p className="result" role="status">{message}</p>}
      </section>}

      {message && <section className="card"><h2>Token flow: end to end</h2><p>Every hop this call took, with the claims, assertion type, and scope carried at each step.</p>
        <FlowView steps={flowSteps} />
      </section>}
    </>}
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

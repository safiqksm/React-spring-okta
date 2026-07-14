import React from 'react';
import { decodeJwt } from '../decodeJwt.js';

function JsonBlock({ value }) {
  return <pre className="jsonview"><code>{JSON.stringify(value, null, 2)}</code></pre>;
}

function RawToken({ raw }) {
  const [show, setShow] = React.useState(false);
  const [copied, setCopied] = React.useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(raw);
      setCopied(true);
      setTimeout(() => setCopied(false), 1200);
    } catch {
      /* clipboard unavailable */
    }
  }

  return (
    <div className="rawtoken">
      <button className="link-button rawtoken-toggle" onClick={() => setShow((s) => !s)}>
        {show ? 'Hide raw JWT' : 'Show raw JWT'}
      </button>
      {show && (
        <div className="codeblock">
          <div className="codeblock-bar">
            <span className="codeblock-label">JWT</span>
            <button className="copy-btn" onClick={copy}>{copied ? '✓ Copied' : '⧉ Copy'}</button>
          </div>
          <pre><code className="wrap">{raw}</code></pre>
        </div>
      )}
    </div>
  );
}

function TokenBlock({ raw, label }) {
  if (!raw) return null;
  const decoded = decodeJwt(raw);
  return (
    <div className="token-block">
      <h4>{label}</h4>
      {decoded ? (
        <>
          <div className="token-section"><h5>Header</h5><JsonBlock value={decoded.header} /></div>
          <div className="token-section"><h5>Payload</h5><JsonBlock value={decoded.payload} /></div>
        </>
      ) : (
        <p className="flow-empty">Opaque token — not a decodable JWT.</p>
      )}
      <RawToken raw={raw} />
    </div>
  );
}

function HttpBlock({ http }) {
  if (!http) return <p className="flow-empty">No captured request/response for this hop.</p>;
  const { request: req, response: res } = http;
  return (
    <>
      {req && (
        <div className="http-section">
          <h4>Request</h4>
          <p className="http-line"><span className="method-pill">{req.method}</span><span className="wrap">{req.url}</span></p>
          {req.headers && <><h5>Headers</h5><JsonBlock value={req.headers} /></>}
          {req.body && <><h5>Body</h5><JsonBlock value={req.body} /></>}
        </div>
      )}
      {res && (
        <div className="http-section">
          <h4>Response</h4>
          <p className="http-line">Status <strong>{res.status}</strong></p>
          {res.headers && Object.keys(res.headers).length > 0 && <><h5>Headers</h5><JsonBlock value={res.headers} /></>}
          {res.body && <><h5>Body</h5><JsonBlock value={res.body} /></>}
        </div>
      )}
    </>
  );
}

function ClaimsBlock({ claims }) {
  if (!claims) return <p className="flow-empty">No validated claims for this hop yet.</p>;
  return (
    <dl>
      <dt>Subject</dt><dd className="wrap">{claims.subject}</dd>
      <dt>Issuer</dt><dd className="wrap">{claims.issuer}</dd>
      <dt>Expires</dt><dd>{claims.expiresAt}</dd>
      <dt>Scopes</dt><dd>{claims.scopes?.join(', ') || 'None'}</dd>
      <dt>Fingerprint</dt><dd className="token-fingerprint">{claims.tokenFingerprint}</dd>
    </dl>
  );
}

function Verify({ pass, children }) {
  return (
    <p className={pass ? 'verify verify-pass' : 'verify verify-fail'} role="status">
      {pass ? '✓ ' : '✗ '}{children}
    </p>
  );
}

function FlowStep({ step, expanded, onSelect }) {
  return (
    <div className={`flow-card ${expanded ? 'flow-card-open' : ''}`}>
      <button className="flow-card-header" onClick={onSelect}>
        <span className={`flow-check ${step.ok ? 'ok' : 'fail'}`}>{step.ok ? '✓' : '…'}</span>
        <span className="flow-route">{step.from} <span className="flow-arrow">→</span> {step.to}</span>
        <span className="flow-badge">{step.badge}</span>
      </button>
      {expanded && (
        <div className="flow-card-body">
          <HttpBlock http={step.http} />
          <h4>Claims</h4>
          <ClaimsBlock claims={step.claims} />
          <h4>Assertion</h4>
          <p>{step.assertion.summary}</p>
          {step.assertion.details?.length > 0 && (
            <dl>
              {step.assertion.details.map(([key, value]) => (
                <React.Fragment key={key}>
                  <dt>{key}</dt><dd className="wrap">{value}</dd>
                </React.Fragment>
              ))}
            </dl>
          )}
          {step.tokens
            ? step.tokens.map((t) => <TokenBlock key={t.label} raw={t.raw} label={t.label} />)
            : <TokenBlock raw={step.claims?.raw} label="Access token (JWT)" />}
          {step.assertion.proof && <TokenBlock raw={step.assertion.proof.raw} label={step.assertion.proof.label} />}
          {step.verify?.map((v) => (
            <Verify key={v.label} pass={v.pass}>{v.label}</Verify>
          ))}
        </div>
      )}
    </div>
  );
}

export default function FlowView({ steps }) {
  const [active, setActive] = React.useState(steps[steps.length - 1]?.id);

  React.useEffect(() => {
    if (steps.length && !steps.find((s) => s.id === active)) {
      setActive(steps[steps.length - 1].id);
    }
  }, [steps]);

  if (!steps.length) {
    return <p className="flow-empty">Call the endpoint above to see the token flow end to end.</p>;
  }

  return (
    <div className="flow">
      {steps.map((step) => (
        <FlowStep key={step.id} step={step} expanded={active === step.id} onSelect={() => setActive(step.id)} />
      ))}
    </div>
  );
}

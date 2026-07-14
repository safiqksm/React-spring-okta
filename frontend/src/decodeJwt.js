// Browser-side, display-only JWT decode (no signature verification).
function b64urlDecode(str) {
  const pad = str.length % 4 === 0 ? '' : '='.repeat(4 - (str.length % 4));
  const b64 = str.replace(/-/g, '+').replace(/_/g, '/') + pad;
  return decodeURIComponent(
    atob(b64)
      .split('')
      .map((c) => `%${`00${c.charCodeAt(0).toString(16)}`.slice(-2)}`)
      .join('')
  );
}

const JWT_RE = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;

export function looksLikeJwt(value) {
  return typeof value === 'string' && JWT_RE.test(value);
}

export function decodeJwt(token) {
  if (!looksLikeJwt(token)) return null;
  const [header, payload] = token.split('.');
  try {
    return { header: JSON.parse(b64urlDecode(header)), payload: JSON.parse(b64urlDecode(payload)) };
  } catch {
    return null;
  }
}

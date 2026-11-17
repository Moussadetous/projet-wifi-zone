'use strict';
/* Serveur MPro local pour les tests de l'application (faux Google, faux Chariow, base en mémoire).
 * Usage : node mpro-test-server.js /chemin/vers/le/serveur/ad
 * Affiche « READY <port> » puis sert la boutique sur http://127.0.0.1:<port>.
 * Commandes de test : GET /control/google-token?sub=&email=&name=  ·  POST /control/pay?sale=sal_x */
const http = require('http');
const crypto = require('crypto');
const path = require('path');
const AD = process.argv[2];
const { build } = require(path.join(AD, 'construire.js'));
const { startLocal } = require(path.join(AD, 'src/local.js'));
const CLIENT_ID = '678554027312-l6v8h8di0lm3g3a19ooaemu8u81ohlv7.apps.googleusercontent.com';
const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwk = { ...publicKey.export({ format: 'jwk' }), kid: 'k1', alg: 'RS256', use: 'sig' };
const b64u = b => Buffer.from(b).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const PRODUCTS = { prd_5ts0f371: 1000, prd_7fgizh33: 3000, prd_hitb2r6h: 5000, prd_jk392enu: 10000, prd_lymeg1fy: 20000, prd_gm5vwczu: 40000 };
const sales = new Map();
let n = 0;

function googleToken(q) {
  const head = b64u(JSON.stringify({ alg: 'RS256', kid: 'k1', typ: 'JWT' }));
  const now = Math.floor(Date.now() / 1000);
  const body = b64u(JSON.stringify({ iss: 'https://accounts.google.com', aud: CLIENT_ID, iat: now, exp: now + 600, email_verified: true,
    sub: q.get('sub') || 'g-1', email: q.get('email') || 'awa@gmail.com', name: q.get('name') || 'Awa Traoré' }));
  return `${head}.${body}.${b64u(crypto.sign('RSA-SHA256', Buffer.from(head + '.' + body), privateKey))}`;
}
function sale(id, s) {
  const paid = s.status === 'paid';
  return { id, status: paid ? 'completed' : 'pending', completed_at: paid ? new Date().toISOString() : null, payment: { status: paid ? 'success' : 'pending' },
    amount: { value: s.amount, currency: 'XOF' }, product: { id: s.product }, customer: { id: 'c1', email: s.email } };
}

(async () => {
  const fake = http.createServer((req, res) => {
    const chunks = []; req.on('data', c => chunks.push(c)); req.on('end', () => {
      const send = (c, o) => { res.writeHead(c, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(o)); };
      const u = new URL(req.url, 'http://x');
      if (u.pathname === '/certs') return send(200, { keys: [jwk] });
      if (u.pathname === '/v1/checkout' && req.method === 'POST') {
        const b = JSON.parse(Buffer.concat(chunks).toString() || '{}');
        const id = 'sal_t' + (++n);
        sales.set(id, { amount: PRODUCTS[b.product_id], product: b.product_id, status: 'pending', email: b.email });
        return send(200, { data: { step: 'payment', purchase: { id }, payment: { checkout_url: `https://pay.example/${id}` } } });
      }
      const m = u.pathname.match(/^\/v1\/sales\/(sal_\w+)$/);
      if (m) return sales.has(m[1]) ? send(200, { data: sale(m[1], sales.get(m[1])) }) : send(404, {});
      if (u.pathname === '/v1/sales') return send(200, { data: [...sales].map(([id, s]) => sale(id, s)) });
      send(404, {});
    });
  });
  await new Promise(r => fake.listen(0, '127.0.0.1', r));
  const fb = `http://127.0.0.1:${fake.address().port}`;
  build({ quiet: true });
  const app = await startLocal({ file: ':memory:', alarms: true, env: { ADMIN_PASSWORD: 'test-admin', MPRO_CHARIOW_KEY: 'k', MPRO_CHARIOW_API: fb + '/v1', MPRO_GOOGLE_CERTS: fb + '/certs' }, workerPath: path.join(AD, 'worker.js') });
  const srv = http.createServer(async (req, res) => {
    const u = new URL(req.url, 'http://x');
    if (u.pathname === '/control/google-token') { res.writeHead(200, { 'Content-Type': 'text/plain' }); return res.end(googleToken(u.searchParams)); }
    if (u.pathname === '/control/pay') { const s = sales.get(u.searchParams.get('sale')); if (s) s.status = 'paid'; res.writeHead(s ? 200 : 404); return res.end(); }
    const chunks = []; for await (const c of req) chunks.push(c);
    const headers = new Headers();
    for (const [k, v] of Object.entries(req.headers)) if (v !== undefined) headers.set(k, Array.isArray(v) ? v.join(', ') : v);
    const body = ['GET', 'HEAD'].includes(req.method) ? undefined : Buffer.concat(chunks);
    const r = await app.fetch(new Request(`http://127.0.0.1:${srv.address().port}${req.url}`, { method: req.method, headers, body, redirect: 'manual' }));
    const h = {}; r.headers.forEach((v, k) => { if (k !== 'set-cookie') h[k] = v; });
    const sc = r.headers.getSetCookie ? r.headers.getSetCookie().map(c => c.replace('; Secure', '')) : [];
    if (sc.length) h['set-cookie'] = sc;
    res.writeHead(r.status, h); res.end(Buffer.from(await r.arrayBuffer()));
  });
  srv.listen(0, '127.0.0.1', () => console.log('READY ' + srv.address().port));
})().catch(e => { console.error(e); process.exit(1); });

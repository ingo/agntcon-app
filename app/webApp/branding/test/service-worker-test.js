const fs = require('fs'), assert = require('assert');
const handlers = {};
const store = new Map(); let online = true; const calls = [];
class FakeResponse { constructor(body, o = {}) { this.body = body; this.status = o.status ?? 200; this.type = o.type ?? 'basic'; } clone() { return new FakeResponse(this.body, this); } static error() { const r = new FakeResponse('error', {status: 0}); r.isError = true; return r; } }
global.Response = FakeResponse;
global.self = { location: { origin: 'https://site.test' }, addEventListener: (n, f) => handlers[n] = f, skipWaiting: () => calls.push('skip'), clients: { claim: async () => calls.push('claim') } };
global.caches = { open: async () => ({ put: async (req, res) => store.set(req.url, res), match: async (u) => store.get(u.url || u), add: async (u) => { if (!online) throw new Error('offline'); store.set(u, new FakeResponse('body of ' + u)); } }), keys: async () => ['conference-app-v2', 'old'], delete: async n => calls.push('delete ' + n), match: async (req) => store.get(req.url) };
global.fetch = async (req) => { if (!online) throw new Error('offline'); return new FakeResponse('body of ' + req.url); };
eval(fs.readFileSync(require('path').join(__dirname, '..', 'agntcon-mcpcon-na-2026', 'service-worker.js'), 'utf8'));
const run = async (req) => { let p; handlers.fetch({ request: req, respondWith: (x) => p = x }); return p === undefined ? 'passthrough' : await p; };
(async () => {
  handlers.install({}); assert(calls.includes('skip'));
  let act; handlers.activate({ waitUntil: (p) => act = p }); await act; assert(calls.includes('delete old') && !calls.includes('delete conference-app-v2') && calls.includes('claim'));
  const page = { method: 'GET', url: 'https://site.test/app/index.html' };
  assert.strictEqual((await run(page)).body, 'body of https://site.test/app/index.html'); // online: network
  await new Promise(r => setTimeout(r, 10)); assert(store.has(page.url), 'cached when online');
  online = false;
  assert.strictEqual((await run(page)).body, 'body of https://site.test/app/index.html'); // offline: cache
  assert((await run({ method: 'GET', url: 'https://site.test/app/never-seen.js' })).isError, 'unknown offline file is an error, not a hang');
  assert.strictEqual(await run({ method: 'GET', url: 'https://sessionize.com/api/v2/x/view/All' }), 'passthrough'); // cross-origin untouched
  assert.strictEqual(await run({ method: 'POST', url: 'https://site.test/app/x' }), 'passthrough');
  // Hashed bundles: cache first, no network once cached
  online = true; let fetched = 0; const realFetch = global.fetch; global.fetch = async (r) => { fetched++; return realFetch(r); };
  const wasm = { method: 'GET', url: 'https://site.test/app/da391f3279fd168ddefb.wasm' };
  await run(wasm); await new Promise(r => setTimeout(r, 10)); const after1 = fetched;
  await run(wasm); assert.strictEqual(fetched, after1, 'a hashed file is not fetched again once cached');
  // Resources: answered from the cache, refreshed in the background
  const font = { method: 'GET', url: 'https://site.test/app/composeResources/x/font/a.ttf' };
  await run(font); await new Promise(r => setTimeout(r, 10)); const before = fetched;
  assert.strictEqual((await run(font)).body, 'body of ' + font.url); assert.strictEqual(fetched, before + 1, 'refreshed in the background');
  // The page reports what it loaded; same-origin files are cached, others ignored
  let pre; handlers.message({ data: { type: 'precache', urls: ['https://site.test/app/a.bin', 'https://elsewhere.test/b.bin'] }, waitUntil: (p) => pre = p }); await pre;
  assert(store.has('https://site.test/app/a.bin') && !store.has('https://elsewhere.test/b.bin'), 'precache keeps to this origin');
  console.log('service worker logic ok');
})().catch(e => { console.error(e); process.exit(1); });

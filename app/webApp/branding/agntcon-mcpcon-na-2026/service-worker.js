// Keeps the app working without a connection and makes later visits start fast.
//  - Files named by a content hash (the .wasm and .js bundles) never change: answered from the cache.
//  - composeResources (fonts, strings, images): answered from the cache, refreshed in the background.
//  - Everything else of this site: network first, so a new build is picked up as soon as there is a
//    connection; the cache answers when there is not.
// Only this site's own files are cached. Schedule data is kept by the app itself, so the Sessionize
// feed is not cached here.
const CACHE = 'conference-app-v2';
const HASHED = /\/[0-9a-f]{16,}\.(wasm|js|mjs)$/;
const RESOURCE = /\/composeResources\//;

self.addEventListener('install', () => self.skipWaiting());

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((names) => Promise.all(names.filter((n) => n !== CACHE).map((n) => caches.delete(n))))
      .then(() => self.clients.claim())
  );
});

const store = (request, response) => {
  if (response && response.status === 200 && response.type === 'basic') {
    const copy = response.clone();
    caches.open(CACHE).then((cache) => cache.put(request, copy));
  }
  return response;
};

const networkFirst = (request) =>
  fetch(request)
    .then((response) => store(request, response))
    .catch(() => caches.match(request, { ignoreSearch: true }).then((cached) => cached || Response.error()));

const cacheFirst = (request) =>
  caches.match(request).then((cached) => cached || fetch(request).then((response) => store(request, response)));

const staleWhileRevalidate = (request) =>
  caches.match(request).then((cached) => {
    const fresh = fetch(request).then((response) => store(request, response));
    if (cached) { fresh.catch(() => {}); return cached; }
    return fresh;
  });

self.addEventListener('fetch', (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin) return;

  if (HASHED.test(url.pathname)) event.respondWith(cacheFirst(request));
  else if (RESOURCE.test(url.pathname)) event.respondWith(staleWhileRevalidate(request));
  else event.respondWith(networkFirst(request));
});

// The page lists what it loaded once it is running, so the first visit is cached too (the worker
// takes control only after that first load).
self.addEventListener('message', (event) => {
  const data = event.data || {};
  if (data.type !== 'precache' || !Array.isArray(data.urls)) return;
  event.waitUntil(
    caches.open(CACHE).then((cache) =>
      Promise.all(data.urls
        .filter((u) => new URL(u, self.location.href).origin === self.location.origin)
        .map((u) => cache.match(u).then((hit) => hit || cache.add(u).catch(() => {}))))
    )
  );
});

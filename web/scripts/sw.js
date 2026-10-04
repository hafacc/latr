// scripts/build-sw.ts prepends self.__VERSION and self.__FILES when it copies this to out/sw.js.
const CACHE = `latr-${self.__VERSION}`;
const SCOPE = self.registration.scope;

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      .then((cache) =>
        cache.addAll(
          self.__FILES.map(
            (file) => new Request(new URL(file, SCOPE), { cache: "reload" }),
          ),
        ),
      ),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((names) =>
        Promise.all(
          names
            .filter((name) => name.startsWith("latr-") && name !== CACHE)
            .map((name) => caches.delete(name)),
        ),
      )
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("message", (event) => {
  if (event.data === "skipWaiting") self.skipWaiting();
});

// A saved page at that path (with or without its trailing slash), else the app.
async function savedPage(url) {
  const path = new URL(url);
  path.search = "";
  path.hash = "";
  const cache = await caches.open(CACHE);
  const withSlash = path.href.endsWith("/") ? path.href : `${path.href}/`;
  return (
    (await cache.match(path.href)) ??
    (await cache.match(withSlash)) ??
    (await cache.match(SCOPE)) ??
    fetch(url)
  );
}

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET" || !request.url.startsWith(SCOPE)) return;
  event.respondWith(
    request.mode === "navigate"
      ? savedPage(request.url)
      : caches
          .match(request, { cacheName: CACHE })
          .then((cached) => cached ?? fetch(request)),
  );
});

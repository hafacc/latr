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

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET" || !request.url.startsWith(SCOPE)) return;
  const key = request.mode === "navigate" ? SCOPE : request;
  event.respondWith(
    caches
      .match(key, { cacheName: CACHE })
      .then((cached) => cached ?? fetch(request)),
  );
});

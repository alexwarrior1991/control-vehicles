// Guarda la app en el móvil para que abra sin cobertura. Las llamadas a la oficina (Google) no pasan por aquí.
const CACHE = 'cv-mobile-v1';
const FILES = ['./', 'index.html', 'styles.css', 'i18n.js', 'app.js', 'manifest.webmanifest', 'icons/icon-180.png',
    'icons/icon-192.png', 'icons/icon-512.png'];

self.addEventListener('install', event => {
    event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(FILES)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', event => {
    event.waitUntil(caches.keys()
        .then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key))))
        .then(() => self.clients.claim()));
});

self.addEventListener('fetch', event => {
    const url = new URL(event.request.url);
    if (event.request.method !== 'GET' || url.origin !== self.location.origin) {
        return;
    }
    // Red primero, para que una versión nueva llegue en cuanto hay conexión; sin red, lo guardado.
    event.respondWith(fetch(event.request)
        .then(response => {
            const copy = response.clone();
            caches.open(CACHE).then(cache => cache.put(event.request, copy));
            return response;
        })
        .catch(() => caches.match(event.request, {ignoreSearch: true})));
});

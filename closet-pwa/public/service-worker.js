const CACHE_NAME = 'closet-pwa-cache-v2'; // Increment this for each version
const ASSETS = [
    '/',
    '/index.html',
    '/manifest.webmanifest',
    '/icon-192x192.png',
    '/icon-512x512.png',
];

// Install event: Cache static assets
self.addEventListener('install', (event) => {
    console.log('Service Worker: Installing...');
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => {
            console.log('Service Worker: Caching static files');
            return cache.addAll(ASSETS);
        })
    );
});

// Activate event: Clean up old caches
self.addEventListener('activate', (event) => {
    console.log('Service Worker: Activating...');
    event.waitUntil(
        caches.keys().then((cacheNames) => {
            return Promise.all(
                cacheNames.map((cache) => {
                    if (cache !== CACHE_NAME) {
                        console.log('Service Worker: Deleting old cache', cache);
                        return caches.delete(cache);
                    }
                })
            );
        })
    );
});

// Fetch event: Serve cached files and dynamically cache new ones
self.addEventListener('fetch', (event) => {
    const requestUrl = new URL(event.request.url);
    const normalizedUrl = requestUrl.pathname === '/' ? '/index.html' : requestUrl.pathname;

    event.respondWith(
        caches.match(normalizedUrl).then((cachedResponse) => {
            if (cachedResponse) {
                console.log('Cache hit for:', normalizedUrl);
                return cachedResponse;
            }

            console.log('Cache miss, fetching from network:', normalizedUrl);
            return fetch(event.request).then((response) => {
                if (!response || response.status !== 200 || response.type !== 'basic') {
                    return response;
                }

                const responseClone = response.clone();
                caches.open(CACHE_NAME).then((cache) => {
                    console.log('Caching new resource:', normalizedUrl);
                    cache.put(normalizedUrl, responseClone);
                });

                return response;
            });
        })
    );
});

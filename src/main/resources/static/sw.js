/*
 * Minimal Service Worker for Open-Pickle
 * - Enables Web Push notifications
 * - Avoids aggressive caching to keep the web app fresh
 */

self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('push', (event) => {
  // Payload is expected to be JSON: { title, body, url }
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch (e) {
    try {
      data = event.data ? JSON.parse(event.data.text()) : {};
    } catch (ignored) {
      data = {};
    }
  }

  const title = data.title || 'Open-Pickle';
  const body = data.body || 'You have an update.';
  const url = data.url || '/play-plans';

  const options = {
    body,
    icon: '/android-chrome-192x192.png',
    badge: '/favicon-192x192.png',
    data: { url }
  };

  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = (event.notification && event.notification.data && event.notification.data.url) || '/play-plans';

  event.waitUntil((async () => {
    const allClients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    for (const client of allClients) {
      if (client && 'focus' in client) {
        try {
          await client.focus();
          if ('navigate' in client) {
            await client.navigate(url);
          }
          return;
        } catch (e) {
          // fall through
        }
      }
    }
    if (self.clients.openWindow) {
      await self.clients.openWindow(url);
    }
  })());
});

package com.w3llspring.fhpb.web.controller.site;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.w3llspring.fhpb.web.config.BrandingProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SiteMetadataController {

  private final BrandingProperties brandingProperties;
  private final ObjectMapper objectMapper;

  public SiteMetadataController(
      BrandingProperties brandingProperties, ObjectMapper objectMapper) {
    this.brandingProperties = brandingProperties;
    this.objectMapper = objectMapper;
  }

  @GetMapping(value = "/site.webmanifest", produces = "application/manifest+json")
  public Map<String, Object> siteManifest() {
    Map<String, Object> manifest = new LinkedHashMap<>();
    manifest.put("id", "/");
    manifest.put("name", brandingProperties.getManifestName());
    manifest.put("short_name", brandingProperties.getManifestShortName());
    manifest.put("start_url", brandingProperties.getManifestStartUrl());
    manifest.put("scope", brandingProperties.getManifestScope());
    manifest.put(
        "icons",
        List.of(
            iconEntry(brandingProperties.getManifestIcon192Path(), "192x192"),
            iconEntry(brandingProperties.getManifestIcon512Path(), "512x512")));
    manifest.put("theme_color", brandingProperties.getManifestThemeColor());
    manifest.put("background_color", brandingProperties.getManifestBackgroundColor());
    manifest.put("display", brandingProperties.getManifestDisplay());
    return manifest;
  }

  @GetMapping(value = "/sw.js", produces = "application/javascript")
  public ResponseEntity<String> serviceWorker() {
    String script =
        """
/*
 * Minimal Service Worker
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

  const title = data.title || %s;
  const body = data.body || 'You have an update.';
  const url = data.url || '/play-plans';

  const options = {
    body,
    icon: %s,
    badge: %s,
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
"""
            .formatted(
                asJsString(brandingProperties.getAppName()),
                asJsString(brandingProperties.getNotificationIconPath()),
                asJsString(brandingProperties.getNotificationBadgePath()));

    return ResponseEntity.ok().contentType(MediaType.valueOf("application/javascript")).body(script);
  }

  private Map<String, Object> iconEntry(String src, String sizes) {
    Map<String, Object> icon = new LinkedHashMap<>();
    icon.put("src", src);
    icon.put("sizes", sizes);
    icon.put("type", "image/png");
    return icon;
  }

  private String asJsString(String value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      return "\"\"";
    }
  }
}
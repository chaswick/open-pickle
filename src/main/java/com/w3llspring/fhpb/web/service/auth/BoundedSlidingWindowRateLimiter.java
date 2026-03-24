package com.w3llspring.fhpb.web.service.auth;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class BoundedSlidingWindowRateLimiter {

  private final long windowMs;
  private final int maxTrackedKeys;
  private final LinkedHashMap<String, SlidingWindow> windows = new LinkedHashMap<>(16, 0.75f, true);

  BoundedSlidingWindowRateLimiter(Duration window, int maxTrackedKeys) {
    this.windowMs = Math.max(1L, window.toMillis());
    this.maxTrackedKeys = Math.max(1, maxTrackedKeys);
  }

  synchronized boolean allow(String key, long nowMs, int limit) {
    pruneExpired(nowMs);

    SlidingWindow window = windows.get(key);
    if (window == null) {
      evictIfNeeded();
      window = new SlidingWindow(windowMs);
      windows.put(key, window);
    }

    return window.allow(nowMs, limit);
  }

  synchronized int trackedKeyCount() {
    pruneExpired(System.currentTimeMillis());
    return windows.size();
  }

  private void evictIfNeeded() {
    while (windows.size() >= maxTrackedKeys) {
      Iterator<Map.Entry<String, SlidingWindow>> iterator = windows.entrySet().iterator();
      if (!iterator.hasNext()) {
        return;
      }
      iterator.next();
      iterator.remove();
    }
  }

  private void pruneExpired(long nowMs) {
    Iterator<Map.Entry<String, SlidingWindow>> iterator = windows.entrySet().iterator();
    while (iterator.hasNext()) {
      SlidingWindow window = iterator.next().getValue();
      window.trim(nowMs);
      if (window.isEmpty()) {
        iterator.remove();
      }
    }
  }

  private static final class SlidingWindow {
    private final long windowMs;
    private final Deque<Long> hits = new ArrayDeque<>();

    private SlidingWindow(long windowMs) {
      this.windowMs = windowMs;
    }

    private boolean allow(long nowMs, int limit) {
      trim(nowMs);
      if (hits.size() >= limit) {
        return false;
      }
      hits.addLast(nowMs);
      return true;
    }

    private void trim(long nowMs) {
      long cutoff = nowMs - windowMs;
      while (!hits.isEmpty() && hits.peekFirst() < cutoff) {
        hits.removeFirst();
      }
    }

    private boolean isEmpty() {
      return hits.isEmpty();
    }
  }
}

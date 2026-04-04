(function (window, document) {
  'use strict';

  window.FHPB = window.FHPB || {};

  FHPB.SessionRecentTicker = {
    pixelsPerSecond: 64,
    reducedMotionPixelsPerSecond: 28,
    refreshMs: 60000,
    minLoopWidthPadding: 96,
    maxRepeatCount: 6,
    maxBuildRetries: 6,

    init: function () {
      document.addEventListener('DOMContentLoaded', function () {
        FHPB.SessionRecentTicker.mountAll(document);
      });
    },

    mountAll: function (scope) {
      var roots = [];
      var context = scope && typeof scope.querySelectorAll === 'function' ? scope : document;
      if (scope
          && scope.getAttribute
          && scope.getAttribute('data-session-recent-ticker') === 'true') {
        roots.push(scope);
      }
      Array.prototype.push.apply(
        roots,
        Array.prototype.slice.call(context.querySelectorAll('[data-session-recent-ticker]')));
      roots.forEach(function (ticker) {
        FHPB.SessionRecentTicker.mount(ticker);
      });
    },

    unmount: function (root) {
      if (!root || typeof root._sessionRecentTickerCleanup !== 'function') {
        return;
      }
      root._sessionRecentTickerCleanup();
    },

    mount: function (root) {
      if (!root) {
        return;
      }

      FHPB.SessionRecentTicker.unmount(root);

      var viewport = root.querySelector('.session-recent-ticker-viewport');
      var source = root.querySelector('[data-session-recent-source]');
      var marqueeElement = root.querySelector('[data-session-recent-marquee]');
      if (!viewport || !source || !marqueeElement) {
        return;
      }

      var baseMarkup = source.innerHTML;
      if (!baseMarkup || !baseMarkup.trim()) {
        return;
      }

      var prefersReducedMotionQuery =
        window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;
      var resizeHandle = null;
      var buildRetryHandle = null;
      var track = null;
      var sourceSegment = null;
      var cloneSegment = null;
      var segmentWidth = 0;
      var buildAttempts = 0;
      var resizeObserver = null;
      var refreshInterval = null;
      var loadListener = null;

      function refreshTimes(scope) {
        FHPB.SessionRecentTicker.refreshTimes(scope);
      }

      function buildTrack() {
        marqueeElement.innerHTML = '';

        track = document.createElement('span');
        track.className = 'session-recent-ticker-track';

        sourceSegment = document.createElement('span');
        sourceSegment.className = 'session-recent-ticker-segment';
        sourceSegment.innerHTML = baseMarkup;

        var minSequenceWidth = Math.max(viewport.clientWidth + FHPB.SessionRecentTicker.minLoopWidthPadding, 320);
        var repeatCount = 1;
        while (sourceSegment.scrollWidth < minSequenceWidth
            && repeatCount < FHPB.SessionRecentTicker.maxRepeatCount) {
          sourceSegment.insertAdjacentHTML('beforeend', baseMarkup);
          repeatCount += 1;
        }

        cloneSegment = sourceSegment.cloneNode(true);
        cloneSegment.setAttribute('aria-hidden', 'true');

        track.appendChild(sourceSegment);
        track.appendChild(cloneSegment);
        marqueeElement.appendChild(track);

        refreshTimes(sourceSegment);
        refreshTimes(cloneSegment);
        segmentWidth = sourceSegment.offsetWidth;
        track.style.setProperty('--session-ticker-loop-width', segmentWidth + 'px');
        track.style.setProperty(
          '--session-ticker-duration',
          FHPB.SessionRecentTicker.buildDuration(segmentWidth, prefersReducedMotionQuery));
        track.style.transform = 'translate3d(0,0,0)';
        buildAttempts += 1;
      }

      function scheduleBuildRetry() {
        if (buildRetryHandle !== null || buildAttempts >= FHPB.SessionRecentTicker.maxBuildRetries) {
          return;
        }
        buildRetryHandle = window.setTimeout(function () {
          buildRetryHandle = null;
          rebuild();
        }, 180);
      }

      function useAnimatedTicker() {
        source.setAttribute('hidden', 'hidden');
        marqueeElement.classList.remove('d-none');
        buildTrack();
        if (!segmentWidth) {
          source.removeAttribute('hidden');
          marqueeElement.classList.add('d-none');
          scheduleBuildRetry();
          return;
        }
        buildAttempts = 0;
      }

      function rebuild() {
        if (buildRetryHandle !== null) {
          window.clearTimeout(buildRetryHandle);
          buildRetryHandle = null;
        }
        useAnimatedTicker();
      }

      function scheduleRebuild() {
        if (resizeHandle !== null) {
          window.clearTimeout(resizeHandle);
        }
        resizeHandle = window.setTimeout(function () {
          resizeHandle = null;
          rebuild();
        }, 120);
      }

      if (window.ResizeObserver) {
        resizeObserver = new window.ResizeObserver(scheduleRebuild);
        resizeObserver.observe(viewport);
      } else {
        window.addEventListener('resize', scheduleRebuild);
      }

      if (prefersReducedMotionQuery) {
        if (typeof prefersReducedMotionQuery.addEventListener === 'function') {
          prefersReducedMotionQuery.addEventListener('change', rebuild);
        } else if (typeof prefersReducedMotionQuery.addListener === 'function') {
          prefersReducedMotionQuery.addListener(rebuild);
        }
      }

      rebuild();
      loadListener = function () {
        scheduleRebuild();
      };
      window.addEventListener('load', loadListener, { once: true });
      refreshInterval = window.setInterval(function () {
        refreshTimes(source);
        refreshTimes(marqueeElement);
      }, FHPB.SessionRecentTicker.refreshMs);

      root._sessionRecentTickerCleanup = function () {
        if (resizeHandle !== null) {
          window.clearTimeout(resizeHandle);
          resizeHandle = null;
        }
        if (buildRetryHandle !== null) {
          window.clearTimeout(buildRetryHandle);
          buildRetryHandle = null;
        }
        if (refreshInterval !== null) {
          window.clearInterval(refreshInterval);
          refreshInterval = null;
        }
        if (resizeObserver) {
          resizeObserver.disconnect();
          resizeObserver = null;
        } else {
          window.removeEventListener('resize', scheduleRebuild);
        }
        if (prefersReducedMotionQuery) {
          if (typeof prefersReducedMotionQuery.removeEventListener === 'function') {
            prefersReducedMotionQuery.removeEventListener('change', rebuild);
          } else if (typeof prefersReducedMotionQuery.removeListener === 'function') {
            prefersReducedMotionQuery.removeListener(rebuild);
          }
        }
        if (loadListener) {
          window.removeEventListener('load', loadListener);
          loadListener = null;
        }
        marqueeElement.innerHTML = '';
        marqueeElement.classList.add('d-none');
        source.removeAttribute('hidden');
        delete root._sessionRecentTickerCleanup;
      };
    },

    currentPixelsPerSecond: function (prefersReducedMotionQuery) {
      return prefersReducedMotionQuery && prefersReducedMotionQuery.matches
        ? FHPB.SessionRecentTicker.reducedMotionPixelsPerSecond
        : FHPB.SessionRecentTicker.pixelsPerSecond;
    },

    buildDuration: function (segmentWidth, prefersReducedMotionQuery) {
      var rate = FHPB.SessionRecentTicker.currentPixelsPerSecond(prefersReducedMotionQuery);
      var seconds = segmentWidth > 0 && rate > 0
        ? (segmentWidth / rate)
        : 22;
      return Math.max(10, seconds).toFixed(2) + 's';
    },

    refreshTimes: function (scope) {
      if (!scope) {
        return;
      }

      var ageNodes = scope.querySelectorAll('[data-session-recent-age][data-utc-time]');
      ageNodes.forEach(function (node) {
        var utcTime = node.getAttribute('data-utc-time');
        if (!utcTime
            || !window.FHPB
            || !FHPB.DateTime
            || typeof FHPB.DateTime.formatRelativeChangeTime !== 'function') {
          return;
        }
        node.textContent = FHPB.DateTime.formatRelativeChangeTime(utcTime);
      });
    }
  };

  FHPB.SessionRecentTicker.init();
})(window, document);

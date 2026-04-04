(function (window, document) {
  'use strict';

  window.FHPB = window.FHPB || {};

  FHPB.SessionStandings = {
    idleRefreshMs: 7000,
    pendingRefreshMs: 1000,
    replayMoveMs: 5000,
    replayInitialDelayMs: 200,
    replayStaticLiftPx: 12,
    replayStaticScale: 0.992,
    replayMoveScale: 0.985,
    replayBounceScale: 1.022,
    replayStaggerMs: 500,
    refreshTimer: null,
    refreshInFlight: false,
    refreshQueued: false,
    refreshQueuedDelayMs: null,
    activityWatchUntil: 0,
    overlayLifecycleBound: false,

    init: function () {
      document.addEventListener('DOMContentLoaded', function () {
        FHPB.SessionStandings.bindConfirmationOverlayLifecycle();
        FHPB.SessionStandings.mountCurrentRoot();
        document.addEventListener('visibilitychange', function () {
          FHPB.SessionStandings.handleVisibilityChange();
        });
      });
    },

    mountCurrentRoot: function () {
      var root = FHPB.SessionStandings.getRoot();
      if (!root) {
        return;
      }

      FHPB.SessionStandings.mount(root);
      FHPB.SessionStandings.scheduleNextRefresh(root);
    },

    getRoot: function (scope) {
      var context = scope && typeof scope.querySelector === 'function' ? scope : document;
      return context.querySelector('[data-session-standings-root="true"]');
    },

    getTickerAnchor: function (scope) {
      var context = scope && typeof scope.querySelector === 'function' ? scope : document;
      return context.querySelector('[data-session-recent-ticker-anchor="true"]');
    },

    getTickerSignature: function (anchor) {
      if (!anchor) {
        return '';
      }

      return Array.prototype.slice.call(anchor.querySelectorAll('[data-session-recent-item]'))
        .map(function (item) {
          var age = item.querySelector('[data-session-recent-age]');
          var copy = item.querySelector('.session-recent-ticker-copy');
          var utcTime = age ? (age.getAttribute('data-utc-time') || '') : '';
          var summary = copy ? copy.textContent.trim() : item.textContent.trim();
          return utcTime + '|' + summary;
        })
        .join('||');
    },

    syncRecentTicker: function (doc) {
      var currentAnchor = FHPB.SessionStandings.getTickerAnchor();
      var freshAnchor = FHPB.SessionStandings.getTickerAnchor(doc);
      if (!currentAnchor || !freshAnchor) {
        return;
      }

      var currentSignature = FHPB.SessionStandings.getTickerSignature(currentAnchor);
      var freshSignature = FHPB.SessionStandings.getTickerSignature(freshAnchor);
      if (currentSignature === freshSignature) {
        return;
      }

      var freshMarkup = freshAnchor.innerHTML || '';

      var currentTicker = currentAnchor.querySelector('[data-session-recent-ticker]');
      if (currentTicker
          && window.FHPB
          && FHPB.SessionRecentTicker
          && typeof FHPB.SessionRecentTicker.unmount === 'function') {
        FHPB.SessionRecentTicker.unmount(currentTicker);
      }

      currentAnchor.innerHTML = freshMarkup;
      if (window.FHPB
          && FHPB.SessionRecentTicker
          && typeof FHPB.SessionRecentTicker.mountAll === 'function') {
        FHPB.SessionRecentTicker.mountAll(currentAnchor);
      }
    },

    hasActiveRoot: function () {
      return !!FHPB.SessionStandings.getRoot();
    },

    bindConfirmationOverlayLifecycle: function () {
      if (FHPB.SessionStandings.overlayLifecycleBound) {
        return;
      }

      document.addEventListener('shown.bs.modal', function (event) {
        if (!FHPB.SessionStandings.isConfirmationOverlayEvent(event)) {
          return;
        }

        FHPB.SessionStandings.clearRefreshTimer();
        var root = FHPB.SessionStandings.getRoot();
        if (!root) {
          return;
        }

        FHPB.SessionStandings.stopReplayLoop(root);
        FHPB.SessionStandings.syncReplayState(root);
      });

      document.addEventListener('hidden.bs.modal', function (event) {
        if (!FHPB.SessionStandings.isConfirmationOverlayEvent(event)) {
          return;
        }

        var root = FHPB.SessionStandings.getRoot();
        if (!root) {
          return;
        }

        FHPB.SessionStandings.mount(root);
        FHPB.SessionStandings.refreshAfterActivity(120);
      });

      FHPB.SessionStandings.overlayLifecycleBound = true;
    },

    isConfirmationOverlayEvent: function (event) {
      var target = event && event.target;
      return !!(target
        && target.getAttribute
        && target.getAttribute('data-session-confirmation-modal'));
    },

    isConfirmationOverlayOpen: function () {
      return !!document.querySelector('[data-session-confirmation-modal].show');
    },

    mount: function (root) {
      if (!root) {
        return;
      }

      FHPB.SessionStandings.initializeSortControls(root);
      if (root._sessionReplayOverride) {
        FHPB.SessionStandings.applyReplayState(root, root._sessionReplayOverride);
        root._sessionReplayOverride = null;
      } else {
        FHPB.SessionStandings.syncReplayState(root);
      }
      FHPB.SessionStandings.initializeReplayControl(root);
      if (root._sessionReplay && typeof root._sessionStandingsUpdateStat === 'function') {
        FHPB.SessionStandings.prepareReplayDisplay(root);
      }
      FHPB.SessionStandings.startReplayLoop(root);
    },

    handleVisibilityChange: function () {
      var root = FHPB.SessionStandings.getRoot();
      if (!root) {
        return;
      }

      if (document.visibilityState === 'hidden') {
        FHPB.SessionStandings.clearRefreshTimer();
        FHPB.SessionStandings.stopReplayLoop(root);
        return;
      }

      FHPB.SessionStandings.mount(root);
      FHPB.SessionStandings.refreshAfterActivity(250);
    },

    initializeSortControls: function (root) {
      if (!root) {
        return;
      }

      if (typeof root._sessionStandingsUpdateStat === 'function') {
        root._sessionStandingsUpdateStat(FHPB.SessionStandings.getDisplayStatKey(root), { persist: false });
        return;
      }

      var list = root.querySelector('[data-session-standings-list="true"]');
      var statButtons = Array.prototype.slice.call(root.querySelectorAll('[data-session-stat-toggle]'));
      if (!list || !statButtons.length) {
        return;
      }

      var rows = Array.prototype.slice.call(list.querySelectorAll('[data-session-row]'));
      if (!rows.length) {
        return;
      }

      function updateStatControls(statKey) {
        statButtons.forEach(function (button) {
          var isActive = button.getAttribute('data-session-stat-toggle') === statKey;
          button.classList.toggle('is-active', isActive);
          button.setAttribute('aria-pressed', isActive ? 'true' : 'false');
        });
      }

      function sortRows(statKey) {
        var sorted = rows.slice().sort(function (a, b) {
          var aValue = Number(a.getAttribute('data-session-sort-' + statKey) || '0');
          var bValue = Number(b.getAttribute('data-session-sort-' + statKey) || '0');
          if (bValue !== aValue) {
            return bValue - aValue;
          }

          var aRank = Number(a.getAttribute('data-session-original-rank') || '0');
          var bRank = Number(b.getAttribute('data-session-original-rank') || '0');
          return aRank - bRank;
        });

        sorted.forEach(function (row) {
          list.appendChild(row);
        });
      }

      function renumberRows() {
        Array.prototype.slice.call(list.querySelectorAll('[data-session-row]')).forEach(function (row, index) {
          var rankCell = row.querySelector('[data-session-row-rank]');
          if (rankCell) {
            rankCell.textContent = String(index + 1);
          }
        });
      }

      function setHiddenState(element, hidden) {
        if (!element) {
          return;
        }
        if (hidden) {
          element.setAttribute('hidden', 'hidden');
          return;
        }
        element.removeAttribute('hidden');
      }

      function updateStatColumn(statKey, options) {
        var persist = !options || options.persist !== false;
        updateStatControls(statKey);
        rows.forEach(function (row) {
          var ratingTrigger = row.querySelector('[data-session-rating-trigger]');
          var ratingTriggerText = row.querySelector('[data-session-rating-trigger-text]');
          var statText = row.querySelector('[data-session-stat-text]');
          var nextValue = row.getAttribute('data-session-sort-' + statKey) || '';
          var ratingLabel = row.getAttribute('data-session-rating-label') || nextValue;
          var showingClickableRating = statKey === 'rating' && !!ratingTrigger;

          if (statText) {
            statText.textContent = statKey === 'rating' ? ratingLabel : nextValue;
          }
          if (ratingTrigger) {
            setHiddenState(ratingTrigger, !showingClickableRating);
            if (ratingTriggerText) {
              ratingTriggerText.textContent = ratingLabel;
            }
          }
          if (statText) {
            setHiddenState(statText, showingClickableRating);
          }
        });

        sortRows(statKey);
        renumberRows();
        root._sessionStandingsCurrentStat = statKey;
        if (persist) {
          FHPB.SessionStandings.writePreferredStat(root, statKey);
        }
      }

      statButtons.forEach(function (button) {
        button.addEventListener('click', function () {
          updateStatColumn(button.getAttribute('data-session-stat-toggle') || 'rating');
        });
      });

      root._sessionStandingsUpdateStat = updateStatColumn;
      updateStatColumn(FHPB.SessionStandings.readPreferredStat(root) || 'rating');
    },

    refreshAfterActivity: function (delayMs) {
      var root = FHPB.SessionStandings.getRoot();
      if (!root) {
        return;
      }

      if (FHPB.SessionStandings.isConfirmationOverlayOpen()) {
        FHPB.SessionStandings.clearRefreshTimer();
        return;
      }

      if (FHPB.SessionStandings.isReplayActive(root)) {
        FHPB.SessionStandings.queueRefresh(delayMs === undefined || delayMs === null ? 0 : delayMs);
        FHPB.SessionStandings.clearRefreshTimer();
        return;
      }

      if (FHPB.SessionStandings.refreshInFlight) {
        FHPB.SessionStandings.queueRefresh(delayMs);
        return;
      }

      FHPB.SessionStandings.clearRefreshTimer();
      FHPB.SessionStandings.refreshTimer = window.setTimeout(function () {
        FHPB.SessionStandings.refreshNow('activity');
      }, Math.max(0, Number(delayMs) || 0));
    },

    refreshNow: function (reason) {
      var currentRoot = FHPB.SessionStandings.getRoot();
      if (!currentRoot) {
        return;
      }

      if (FHPB.SessionStandings.isConfirmationOverlayOpen()) {
        FHPB.SessionStandings.clearRefreshTimer();
        return;
      }

      if (document.visibilityState === 'hidden' && reason !== 'activity') {
        FHPB.SessionStandings.scheduleNextRefresh(currentRoot);
        return;
      }

      if (FHPB.SessionStandings.isReplayActive(currentRoot)) {
        FHPB.SessionStandings.queueRefresh(0);
        FHPB.SessionStandings.clearRefreshTimer();
        return;
      }

      if (FHPB.SessionStandings.refreshInFlight) {
        FHPB.SessionStandings.queueRefresh();
        return;
      }

      FHPB.SessionStandings.refreshInFlight = true;

      var previousMetrics = FHPB.SessionStandings.captureCurrentUserMetrics(currentRoot);
      var currentStatKey = FHPB.SessionStandings.getDisplayStatKey(currentRoot);
      var currentSnapshot = FHPB.SessionStandings.buildSnapshot(currentRoot);
      var currentState = {
        pending: FHPB.SessionStandings.isPending(currentRoot),
        signature: currentSnapshot.signature,
        statusSignature: currentSnapshot.statusSignature
      };

      fetch(window.location.href, {
        cache: 'no-store',
        credentials: 'same-origin',
        headers: {
          'X-Requested-With': 'XMLHttpRequest'
        }
      })
        .then(function (response) {
          if (!response.ok) {
            throw new Error('Failed to refresh session standings');
          }
          return response.text();
        })
        .then(function (html) {
          var parser = new DOMParser();
          var doc = parser.parseFromString(html, 'text/html');
          FHPB.SessionStandings.syncRecentTicker(doc);
          var freshRoot = FHPB.SessionStandings.getRoot(doc);
          if (!freshRoot || !currentRoot.parentNode) {
            return;
          }

          FHPB.SessionStandings.syncStatDisplay(freshRoot, currentStatKey);
          FHPB.SessionStandings.carryForwardMomentumDisplay(currentRoot, freshRoot);
          var freshSnapshot = FHPB.SessionStandings.buildSnapshot(freshRoot);
          var freshState = {
            pending: FHPB.SessionStandings.isPending(freshRoot),
            signature: freshSnapshot.signature,
            statusSignature: freshSnapshot.statusSignature
          };
          if (freshState.pending) {
            FHPB.SessionStandings.refreshQueued = true;
            FHPB.SessionStandings.refreshQueuedDelayMs = FHPB.SessionStandings.pendingRefreshMs;
            return;
          }
          if (!FHPB.SessionStandings.shouldReplace(currentState, freshState)) {
            return;
          }

          var replayOverride = null;
          var shouldAnimate = currentState.signature !== freshState.signature;
          if (currentState.signature !== freshState.signature) {
            if (shouldAnimate) {
              replayOverride = FHPB.SessionStandings.buildReplay(
                currentSnapshot,
                freshSnapshot,
                currentRoot,
                currentStatKey);
            }
          }

          if (FHPB.SessionStandings.isConfirmationOverlayOpen()) {
            return;
          }

          if (FHPB.SessionStandings.isReplayActive(currentRoot)) {
            FHPB.SessionStandings.queueRefresh(0);
            return;
          }

          var standingsCollapse = currentRoot.querySelector('.collapse[id]');
          if (standingsCollapse && window.FHPB && FHPB.CollapseState) {
            FHPB.CollapseState.captureCurrentState(standingsCollapse.id, currentRoot);
          }

          FHPB.SessionStandings.stopReplayLoop(currentRoot);
          currentRoot.parentNode.replaceChild(freshRoot, currentRoot);
          if (window.FHPB && FHPB.CardCollapse) {
            FHPB.CardCollapse.upgradeAll(freshRoot);
          }
          if (window.FHPB && FHPB.CollapseState) {
            FHPB.CollapseState.restoreAll(freshRoot);
          }
          if (currentRoot._sessionLastReplay) {
            freshRoot._sessionLastReplay = FHPB.SessionStandings.cloneReplay(currentRoot._sessionLastReplay);
          }
          if (replayOverride) {
            freshRoot._sessionReplayOverride = replayOverride;
          }
          FHPB.SessionStandings.mount(freshRoot);

          if (shouldAnimate && currentState.signature !== freshState.signature && !freshRoot._sessionReplay) {
            FHPB.SessionStandings.animateCurrentUserTransition(previousMetrics, freshRoot);
          }
        })
        .catch(function (error) {
          console.error('Failed to refresh session standings:', error);
        })
        .finally(function () {
          FHPB.SessionStandings.refreshInFlight = false;
          FHPB.SessionStandings.resumeRefreshLoop(FHPB.SessionStandings.getRoot());
        });
    },

    shouldReplace: function (currentState, freshState) {
      if (!currentState || !freshState) {
        return true;
      }

      return currentState.pending !== freshState.pending
        || currentState.signature !== freshState.signature
        || currentState.statusSignature !== freshState.statusSignature;
    },

    scheduleNextRefresh: function (root) {
      FHPB.SessionStandings.clearRefreshTimer();
      if (!root
          || document.visibilityState === 'hidden'
          || FHPB.SessionStandings.isConfirmationOverlayOpen()
          || FHPB.SessionStandings.isReplayActive(root)) {
        return;
      }

      var delay = FHPB.SessionStandings.isPending(root)
        ? FHPB.SessionStandings.pendingRefreshMs
        : FHPB.SessionStandings.idleRefreshMs;
      if (FHPB.SessionStandings.hasRecentConfirmationWatch()) {
        delay = Math.min(delay, FHPB.SessionStandings.pendingRefreshMs);
      }

      FHPB.SessionStandings.refreshTimer = window.setTimeout(function () {
        FHPB.SessionStandings.refreshNow('poll');
      }, delay);
    },

    clearRefreshTimer: function () {
      if (FHPB.SessionStandings.refreshTimer !== null) {
        window.clearTimeout(FHPB.SessionStandings.refreshTimer);
        FHPB.SessionStandings.refreshTimer = null;
      }
    },

    extractDisplayState: function (root) {
      var snapshot = FHPB.SessionStandings.buildSnapshot(root);
      return {
        pending: FHPB.SessionStandings.isPending(root),
        signature: snapshot.signature,
        statusSignature: snapshot.statusSignature
      };
    },

    captureCurrentUserMetrics: function (root) {
      if (!root) {
        return null;
      }

      var row = root.querySelector('[data-session-row].current-user-row');
      if (!row) {
        return null;
      }

      var rect = row.getBoundingClientRect();
      return {
        top: rect.top
      };
    },

    animateCurrentUserTransition: function (previousMetrics, root) {
      if (!previousMetrics || !root) {
        return;
      }

      var row = root.querySelector('[data-session-row].current-user-row');
      if (!row) {
        return;
      }

      if (row._sessionMoveResetTimer) {
        window.clearTimeout(row._sessionMoveResetTimer);
        row._sessionMoveResetTimer = null;
      }

      var deltaY = previousMetrics.top - row.getBoundingClientRect().top;
      if (Math.abs(deltaY) < 1) {
        return;
      }

      row.style.transition = 'none';
      row.style.transform = 'translate3d(0,' + deltaY.toFixed(2) + 'px,0)';
      row.getBoundingClientRect();

      var durationMs = FHPB.SessionStandings.prefersReducedMotion() ? 780 : 1100;
      window.requestAnimationFrame(function () {
        row.style.transition = 'transform ' + durationMs + 'ms cubic-bezier(0.2, 0.8, 0.2, 1)';
        row.style.transform = 'translate3d(0,0,0)';
      });

      row._sessionMoveResetTimer = window.setTimeout(function () {
        row.style.transition = '';
        row.style.transform = '';
        row._sessionMoveResetTimer = null;
      }, durationMs + 100);
    },

    syncReplayState: function (root) {
      if (!root) {
        return;
      }
      root._sessionReplay = null;
      root._sessionReplayHasPlayed = false;
      root._sessionReplayRestoreStat = null;
      FHPB.SessionStandings.applyReplayBanner(root, null);
      FHPB.SessionStandings.updateReplayControl(root);
    },

    applyReplayState: function (root, replay) {
      if (!root) {
        return;
      }

      root._sessionReplay = replay || null;
      root._sessionReplayHasPlayed = false;
      root._sessionReplayRestoreStat = null;
      if (replay) {
        root._sessionLastReplay = FHPB.SessionStandings.cloneReplay(replay);
      }
      FHPB.SessionStandings.applyReplayBanner(root, replay || null);
      FHPB.SessionStandings.updateReplayControl(root);
    },

    initializeReplayControl: function (root) {
      if (!root) {
        return;
      }

      var button = root.querySelector('[data-session-replay-trigger]');
      if (!button) {
        return;
      }

      if (!button._sessionReplayBound) {
        button.addEventListener('click', function () {
          FHPB.SessionStandings.replayLast(root);
        });
        button._sessionReplayBound = true;
      }

      FHPB.SessionStandings.updateReplayControl(root);
    },

    isReplayActive: function (root) {
      return !!(root && (root._sessionReplay
        || root._sessionReplayTimer
        || root._sessionReplayCleanupTimer));
    },

    queueRefresh: function (delayMs) {
      FHPB.SessionStandings.refreshQueued = true;
      if (delayMs === undefined || delayMs === null) {
        return;
      }

      var requestedDelay = Math.max(0, Number(delayMs) || 0);
      if (FHPB.SessionStandings.refreshQueuedDelayMs === null
          || requestedDelay < FHPB.SessionStandings.refreshQueuedDelayMs) {
        FHPB.SessionStandings.refreshQueuedDelayMs = requestedDelay;
      }
    },

    resumeRefreshLoop: function (root) {
      if (FHPB.SessionStandings.isConfirmationOverlayOpen()) {
        FHPB.SessionStandings.refreshQueued = false;
        FHPB.SessionStandings.refreshQueuedDelayMs = null;
        FHPB.SessionStandings.clearRefreshTimer();
        return;
      }

      if (FHPB.SessionStandings.refreshQueued) {
        var queuedDelay = FHPB.SessionStandings.refreshQueuedDelayMs;
        FHPB.SessionStandings.refreshQueued = false;
        FHPB.SessionStandings.refreshQueuedDelayMs = null;
        FHPB.SessionStandings.refreshAfterActivity(
          queuedDelay === null || queuedDelay === undefined ? 200 : queuedDelay);
        return;
      }

      FHPB.SessionStandings.scheduleNextRefresh(root || FHPB.SessionStandings.getRoot());
    },

    updateReplayControl: function (root) {
      if (!root) {
        return;
      }

      var button = root.querySelector('[data-session-replay-trigger]');
      if (!button) {
        return;
      }

      var hasReplay = !!(root._sessionLastReplay
        && root._sessionLastReplay.affectedUserIds
        && root._sessionLastReplay.affectedUserIds.length);
      var isBusy = FHPB.SessionStandings.isReplayActive(root);

      button.disabled = !hasReplay || isBusy;
      button.setAttribute('aria-disabled', (!hasReplay || isBusy) ? 'true' : 'false');
      button.hidden = !hasReplay;
      button.classList.toggle('d-none', !hasReplay);
    },

    replayLast: function (root) {
      if (!root || FHPB.SessionStandings.isConfirmationOverlayOpen()) {
        return;
      }

      if (!root._sessionLastReplay
          || !root._sessionLastReplay.affectedUserIds
          || !root._sessionLastReplay.affectedUserIds.length) {
        return;
      }

      if (FHPB.SessionStandings.isReplayActive(root)) {
        return;
      }

      FHPB.SessionStandings.applyReplayState(
        root,
        FHPB.SessionStandings.cloneReplay(root._sessionLastReplay));
      if (root._sessionReplay && typeof root._sessionStandingsUpdateStat === 'function') {
        FHPB.SessionStandings.prepareReplayDisplay(root);
      }
      FHPB.SessionStandings.startReplayLoop(root);
    },

    cloneReplay: function (replay) {
      if (!replay) {
        return null;
      }

      try {
        return JSON.parse(JSON.stringify(replay));
      } catch (error) {
        return null;
      }
    },

    watchForRecentConfirmation: function (ttlMs) {
      var now = Date.now();
      var ttl = Math.max(1000, Number(ttlMs) || 8000);
      FHPB.SessionStandings.activityWatchUntil = Math.max(
        FHPB.SessionStandings.activityWatchUntil || 0,
        now + ttl);
    },

    hasRecentConfirmationWatch: function () {
      if (!FHPB.SessionStandings.activityWatchUntil) {
        return false;
      }

      if (FHPB.SessionStandings.activityWatchUntil < Date.now()) {
        FHPB.SessionStandings.activityWatchUntil = 0;
        return false;
      }

      return true;
    },

    prepareReplayDisplay: function (root) {
      if (!root || !root._sessionReplay || typeof root._sessionStandingsUpdateStat !== 'function') {
        return;
      }

      if (!root._sessionReplayRestoreStat) {
        root._sessionReplayRestoreStat = FHPB.SessionStandings.getDisplayStatKey(root);
      }

      var replayStat = root._sessionReplay.displayStat || root._sessionReplayRestoreStat;
      if (replayStat && root._sessionStandingsCurrentStat !== replayStat) {
        root._sessionStandingsUpdateStat(replayStat, { persist: false });
      }
    },

    restoreReplayDisplay: function (root) {
      if (!root || typeof root._sessionStandingsUpdateStat !== 'function') {
        return;
      }

      var restoreStat = root._sessionReplayRestoreStat;
      if (!restoreStat) {
        return;
      }

      root._sessionStandingsUpdateStat(restoreStat, { persist: false });
    },

    primeReplayScene: function (root) {
      if (!root || !root._sessionReplay || root._sessionReplayHasPlayed) {
        return null;
      }

      if (root._sessionReplayScene && root._sessionReplayScene.items && root._sessionReplayScene.items.length) {
        return root._sessionReplayScene;
      }

      FHPB.SessionStandings.clearReplayArtifacts(root);
      var scene = FHPB.SessionStandings.buildReplayScene(root, root._sessionReplay);
      if (!scene || !scene.items.length) {
        root._sessionReplayScene = null;
        return null;
      }

      FHPB.SessionStandings.hideSourceRows(scene);
      root._sessionReplayScene = scene;
      return scene;
    },

    buildReplay: function (previousSnapshot, currentSnapshot, previousRoot, displayStat) {
      if (!previousSnapshot || !currentSnapshot) {
        return null;
      }

      var previousRanksByUserId = {};
      var currentRanksByUserId = {};
      var affectedUserIds = [];
      var movedUserIds = [];
      var previousRowMarkupByUserId = previousRoot
        ? FHPB.SessionStandings.captureRowMarkupByUserId(previousRoot)
        : {};
      var currentUserId = currentSnapshot.currentUserId !== null
        ? String(currentSnapshot.currentUserId)
        : null;

      var allUserIds = Object.keys(previousSnapshot.ranksByUserId);
      Object.keys(currentSnapshot.ranksByUserId).forEach(function (userId) {
        if (allUserIds.indexOf(userId) === -1) {
          allUserIds.push(userId);
        }
      });

      allUserIds.forEach(function (userId) {
        var prev = previousSnapshot.ranksByUserId[userId];
        var curr = currentSnapshot.ranksByUserId[userId];
        var isMoved = !!prev && !!curr && prev !== curr;

        if (!isMoved) {
          return;
        }

        if (!prev || !curr) {
          return;
        }

        previousRanksByUserId[userId] = prev;
        currentRanksByUserId[userId] = curr;
        affectedUserIds.push(userId);

        if (isMoved) {
          movedUserIds.push(userId);
        }
      });

      if (!affectedUserIds.length) {
        return null;
      }

      affectedUserIds.sort(function (left, right) {
        return currentRanksByUserId[left] - currentRanksByUserId[right];
      });

      var previousRank = currentUserId ? previousSnapshot.ranksByUserId[currentUserId] : null;
      var currentRank = currentUserId ? currentSnapshot.ranksByUserId[currentUserId] : null;
      var currentUserMoved = !!previousRank && !!currentRank && previousRank !== currentRank;
      var direction = currentUserMoved
        ? (currentRank < previousRank ? 'up' : 'down')
        : 'neutral';
      var message = 'Latest move: standings updated.';

      if (currentUserMoved) {
        message = direction === 'up'
          ? 'Latest move: you climbed from #' + previousRank + ' to #' + currentRank + '.'
          : 'Latest move: you dropped from #' + previousRank + ' to #' + currentRank + '.';
      } else if (movedUserIds.length) {
        message = 'Latest move: standings shuffled. ' + movedUserIds.length + ' player'
          + (movedUserIds.length === 1 ? ' moved.' : 's moved.');
      }

      return {
        signature: currentSnapshot.signature,
        displayStat: displayStat || FHPB.SessionStandings.getDisplayStatKey(previousRoot),
        currentUserId: currentUserId,
        previousRank: previousRank,
        currentRank: currentRank,
        previousRanksByUserId: previousRanksByUserId,
        currentRanksByUserId: currentRanksByUserId,
        previousRowMarkupByUserId: previousRowMarkupByUserId,
        affectedUserIds: affectedUserIds,
        movedUserIds: movedUserIds,
        direction: direction,
        message: message
      };
    },

    captureRowMarkupByUserId: function (root) {
      var rows = root ? Array.prototype.slice.call(root.querySelectorAll('[data-session-row]')) : [];
      var markupByUserId = {};

      rows.forEach(function (row) {
        var userId = row.getAttribute('data-session-row-user-id');
        if (!userId) {
          return;
        }
        markupByUserId[userId] = row.outerHTML;
      });

      return markupByUserId;
    },

    applyReplayBanner: function (root, replay) {
      var banner = root ? root.querySelector('[data-session-replay-banner]') : null;
      if (!banner) {
        return;
      }

      banner.classList.remove('is-session-replay-up', 'is-session-replay-down');
      banner.textContent = '';

      if (!replay) {
        banner.setAttribute('hidden', 'hidden');
        return;
      }

      banner.textContent = replay.message;
      if (replay.direction === 'up') {
        banner.classList.add('is-session-replay-up');
      } else if (replay.direction === 'down') {
        banner.classList.add('is-session-replay-down');
      }
      banner.removeAttribute('hidden');
    },

    startReplayLoop: function (root) {
      if (!root) {
        return;
      }

      FHPB.SessionStandings.stopReplayLoop(root);

      if (!root._sessionReplay) {
        FHPB.SessionStandings.updateReplayControl(root);
        return;
      }

      FHPB.SessionStandings.primeReplayScene(root);
      FHPB.SessionStandings.updateReplayControl(root);
      root._sessionReplayTimer = window.setTimeout(function () {
        FHPB.SessionStandings.runReplayCycle(root);
      }, FHPB.SessionStandings.replayInitialDelayMs);
    },

    stopReplayLoop: function (root) {
      if (!root) {
        return;
      }

      if (root._sessionReplayTimer) {
        window.clearTimeout(root._sessionReplayTimer);
        root._sessionReplayTimer = null;
      }
      if (root._sessionReplayCleanupTimer) {
        window.clearTimeout(root._sessionReplayCleanupTimer);
        root._sessionReplayCleanupTimer = null;
      }
      if (root._sessionReplayRankTimer) {
        window.clearTimeout(root._sessionReplayRankTimer);
        root._sessionReplayRankTimer = null;
      }
      FHPB.SessionStandings.clearReplayArtifacts(root);
      FHPB.SessionStandings.updateReplayControl(root);
    },

    runReplayCycle: function (root) {
      if (!root || !root.isConnected || !root._sessionReplay || root._sessionReplayHasPlayed) {
        FHPB.SessionStandings.updateReplayControl(root);
        return;
      }

      FHPB.SessionStandings.prepareReplayDisplay(root);
      var scene = root._sessionReplayScene || FHPB.SessionStandings.primeReplayScene(root);
      if (!scene || !scene.items.length) {
        root._sessionReplayHasPlayed = true;
        root._sessionReplay = null;
        FHPB.SessionStandings.restoreReplayDisplay(root);
        FHPB.SessionStandings.updateReplayControl(root);
        return;
      }

      scene.items.forEach(function (item, index) {
        FHPB.SessionStandings.prepareReplayItem(root, item, index);
        FHPB.SessionStandings.playReplayItem(root, item, index);
      });

      var totalCycleMs = FHPB.SessionStandings.replayMoveMs
        + Math.max(0, scene.items.length - 1) * FHPB.SessionStandings.replayStaggerMs;

      root._sessionReplayCleanupTimer = window.setTimeout(function () {
        FHPB.SessionStandings.clearReplayArtifacts(root);
        root._sessionReplayHasPlayed = true;
        root._sessionReplay = null;
        FHPB.SessionStandings.restoreReplayDisplay(root);
        FHPB.SessionStandings.updateReplayControl(root);
        FHPB.SessionStandings.resumeRefreshLoop(root);
      }, totalCycleMs);
    },

    buildReplayScene: function (root, replay) {
      var list = root.querySelector('[data-session-standings-list="true"]');
      if (!list) {
        return null;
      }

      var rows = Array.prototype.slice.call(list.querySelectorAll('[data-session-row]'));
      if (!rows.length) {
        return null;
      }

      var rowMap = {};
      rows.forEach(function (row) {
        var userId = row.getAttribute('data-session-row-user-id');
        if (userId) {
          rowMap[userId] = row;
        }
      });

      var slotTopByRank = {};
      var orderedRows = rows.slice();

      orderedRows.forEach(function (row, index) {
        slotTopByRank[index + 1] = row.offsetTop;
      });

      var layer = FHPB.SessionStandings.ensureReplayLayer(list);
      layer.innerHTML = '';
      layer.style.height = list.scrollHeight + 'px';

      var items = [];
      var affectedRows = [];
      replay.affectedUserIds.forEach(function (userId) {
        var row = rowMap[userId];
        var previousRank = replay.previousRanksByUserId[userId];
        var currentRank = replay.currentRanksByUserId[userId];
        var isStationary = previousRank === currentRank;
        if (!row || !previousRank || !currentRank) {
          return;
        }

        var currentTop = slotTopByRank[currentRank];
        var previousTop = slotTopByRank[previousRank];
        if (currentTop === undefined || previousTop === undefined) {
          return;
        }

        var clone = FHPB.SessionStandings.createReplayClone(
          replay.previousRowMarkupByUserId ? replay.previousRowMarkupByUserId[userId] : null,
          row);
        clone.classList.add('session-standings-replay-clone');
        clone.classList.add(isStationary ? 'is-session-replay-static' : 'is-session-replay-moved');
        if (userId === replay.currentUserId) {
          clone.classList.add('is-session-replay-current-user');
        }
        clone.style.top = previousTop + 'px';
        clone.style.width = list.clientWidth + 'px';
        clone.style.opacity = '1';
        layer.appendChild(clone);

        var rankCell = clone.querySelector('[data-session-row-rank]');
        if (rankCell) {
          rankCell.textContent = String(previousRank);
        }

        affectedRows.push(row);
        items.push({
          row: row,
          clone: clone,
          rankCell: rankCell,
          previousRank: previousRank,
          currentRank: currentRank,
          currentTop: currentTop,
          previousTop: previousTop,
          isStationary: isStationary,
          isCurrentUser: userId === replay.currentUserId
        });
      });

      return {
        root: root,
        list: list,
        layer: layer,
        items: items,
        affectedRows: affectedRows
      };
    },

    prepareReplayItem: function (root, item, index) {
      if (!item || !item.clone) {
        return;
      }

      item.clone.style.zIndex = String(20 + index);
      item.clone.style.willChange = 'transform, opacity';
      if (item.rankCell) {
        item.rankCell.textContent = String(item.previousRank);
      }
    },

    playReplayItem: function (root, item, index) {
      if (!item || !item.clone) {
        return;
      }

      var clone = item.clone;
      var reducedMotion = FHPB.SessionStandings.prefersReducedMotion();
      var deltaY = item.isStationary
        ? -FHPB.SessionStandings.replayStaticLiftPx
        : item.currentTop - item.previousTop;
      var finalY = item.isStationary ? 0 : deltaY;
      var delay = index * FHPB.SessionStandings.replayStaggerMs;
      var travelOffset = reducedMotion
        ? 0.96
        : (item.isStationary ? 0.88 : 0.97);
      var settleOffset = reducedMotion
        ? 1
        : (item.isStationary ? 0.98 : 0.995);
      var overshootY = reducedMotion
        ? (item.isStationary ? 0 : deltaY)
        : (item.isStationary ? 2 : deltaY + (deltaY * 0.03));
      var startScale = reducedMotion
        ? 0.996
        : (item.isStationary
        ? FHPB.SessionStandings.replayStaticScale
        : FHPB.SessionStandings.replayMoveScale);
      var peakScale = reducedMotion
        ? (item.isCurrentUser ? 1.006 : 1.003)
        : (item.isCurrentUser
        ? FHPB.SessionStandings.replayBounceScale + 0.006
        : FHPB.SessionStandings.replayBounceScale);

      if (clone._sessionReplayAnimation) {
        clone._sessionReplayAnimation.cancel();
        clone._sessionReplayAnimation = null;
      }

      if (typeof clone.animate === 'function') {
        clone._sessionReplayAnimation = clone.animate([
          {
            transform: 'translate3d(0,0,0) scale(1)',
            offset: 0
          },
          {
            transform: 'translate3d(0,' + deltaY.toFixed(2) + 'px,0) scale(' + startScale + ')',
            offset: travelOffset
          },
          {
            transform: 'translate3d(0,' + overshootY.toFixed(2) + 'px,0) scale(' + peakScale + ')',
            offset: settleOffset
          },
          {
            transform: 'translate3d(0,' + finalY.toFixed(2) + 'px,0) scale(1)',
            offset: 1
          }
        ], {
          duration: FHPB.SessionStandings.replayMoveMs,
          delay: delay,
          easing: 'ease-in-out',
          fill: 'both'
        });
        return;
      }

      clone.style.transition = 'none';
      clone.style.transform = 'translate3d(0,0,0) scale(1)';
      clone.getBoundingClientRect();
      window.setTimeout(function () {
        clone.style.transition = 'transform ' + FHPB.SessionStandings.replayMoveMs + 'ms ease-in-out';
        clone.style.transform = 'translate3d(0,' + finalY.toFixed(2) + 'px,0) scale(1)';
      }, delay);
    },

    clearReplayArtifacts: function (root) {
      if (!root) {
        return;
      }
      root._sessionReplayScene = null;
      var list = root.querySelector('[data-session-standings-list="true"]');
      if (list) {
        var layer = list.querySelector('[data-session-replay-layer]');
        if (layer) {
          layer.innerHTML = '';
        }
      }

      Array.prototype.slice.call(root.querySelectorAll('[data-session-row].is-session-replay-hidden')).forEach(function (row) {
        row.classList.remove('is-session-replay-hidden');
      });
    },

    ensureReplayLayer: function (list) {
      var layer = list.querySelector('[data-session-replay-layer]');
      if (layer) {
        return layer;
      }

      layer = document.createElement('div');
      layer.className = 'session-standings-replay-layer';
      layer.setAttribute('data-session-replay-layer', 'true');
      layer.setAttribute('aria-hidden', 'true');
      list.appendChild(layer);
      return layer;
    },

    createReplayClone: function (markup, fallbackRow) {
      if (markup) {
        var template = document.createElement('template');
        template.innerHTML = markup.trim();
        if (template.content.firstElementChild) {
          return template.content.firstElementChild;
        }
      }

      return fallbackRow.cloneNode(true);
    },

    hideSourceRows: function (scene) {
      if (!scene || !scene.affectedRows) {
        return;
      }

      scene.affectedRows.forEach(function (row) {
        row.classList.add('is-session-replay-hidden');
      });
    },

    revealSourceRows: function (scene) {
      if (!scene || !scene.affectedRows) {
        return;
      }

      scene.affectedRows.forEach(function (row) {
        row.classList.remove('is-session-replay-hidden');
      });
    },

    buildSnapshot: function (root) {
      var rows = root ? Array.prototype.slice.call(root.querySelectorAll('[data-session-row]')) : [];
      var currentUserId = FHPB.SessionStandings.readUserId(root ? root.getAttribute('data-session-current-user-id') : null);
      var ordered = [];
      var ranksByUserId = {};
      var statsSignatureByUserId = {};
      var statusByUserId = {};

      rows.forEach(function (row, index) {
        var userId = FHPB.SessionStandings.readUserId(row.getAttribute('data-session-row-user-id'));
        var rank = index + 1;
        var formElement = row.querySelector('.session-roster-form');
        var formIcon = formElement ? formElement.querySelector('.bi') : null;
        var awaitingConfirmation = row.getAttribute('data-session-awaiting-confirmation') === 'true';
        var statsSignature = [
          row.getAttribute('data-session-sort-rating') || '',
          row.getAttribute('data-session-sort-wins') || '',
          row.getAttribute('data-session-sort-points-for') || '',
          row.getAttribute('data-session-rating-label') || '',
          formElement ? (formElement.getAttribute('title') || '') : '',
          formIcon ? formIcon.className : ''
        ].join('~');
        if (userId === null || !rank) {
          return;
        }
        ordered.push({
          userId: userId,
          rank: rank,
          statsSignature: statsSignature
        });
        ranksByUserId[userId] = rank;
        statsSignatureByUserId[userId] = statsSignature;
        statusByUserId[userId] = awaitingConfirmation ? 'awaiting' : 'settled';
      });

      return {
        currentUserId: currentUserId,
        ranksByUserId: ranksByUserId,
        statsSignatureByUserId: statsSignatureByUserId,
        statusSignature: ordered.map(function (item) {
          return item.userId + ':' + (statusByUserId[item.userId] || 'settled');
        }).join('|'),
        signature: ordered.map(function (item) {
          return item.userId + ':' + item.rank + ':' + item.statsSignature;
        }).join('|')
      };
    },

    buildStorageBaseKey: function (root) {
      if (!root) {
        return null;
      }

      var ladderId = root.getAttribute('data-session-ladder-id');
      var seasonId = root.getAttribute('data-session-season-id');
      var currentUserId = root.getAttribute('data-session-current-user-id');
      if (!ladderId || !seasonId || !currentUserId) {
        return null;
      }

      return 'fhpb.session.standings.' + ladderId + '.' + seasonId + '.' + currentUserId;
    },

    readPreferredStat: function (root) {
      var storageKey = FHPB.SessionStandings.buildStorageBaseKey(root);
      if (!storageKey) {
        return null;
      }

      var value = FHPB.SessionStandings.readValue(window.sessionStorage, storageKey + '.stat');
      if (value === 'rating' || value === 'wins' || value === 'points-for') {
        return value;
      }
      return null;
    },

    getDisplayStatKey: function (root) {
      if (!root) {
        return 'rating';
      }

      return root._sessionStandingsCurrentStat
        || FHPB.SessionStandings.readPreferredStat(root)
        || 'rating';
    },

    syncStatDisplay: function (root, statKey) {
      if (!root) {
        return;
      }

      FHPB.SessionStandings.initializeSortControls(root);
      if (typeof root._sessionStandingsUpdateStat === 'function') {
        root._sessionStandingsUpdateStat(statKey || FHPB.SessionStandings.getDisplayStatKey(root), { persist: false });
      }
    },

    carryForwardMomentumDisplay: function (currentRoot, freshRoot) {
      if (!currentRoot || !freshRoot) {
        return;
      }

      var previousFormStates = FHPB.SessionStandings.captureFormStateByUserId(currentRoot);
      var freshFormStates = FHPB.SessionStandings.captureFormStateByUserId(freshRoot);
      var previousUserIds = Object.keys(previousFormStates);
      var freshUserIds = Object.keys(freshFormStates);
      if (!previousUserIds.length || !freshUserIds.length) {
        return;
      }

      var previousHasMeaningfulForm = previousUserIds.some(function (userId) {
        var state = previousFormStates[userId];
        return state && !state.isZero;
      });
      var freshLooksZeroed = freshUserIds.every(function (userId) {
        var state = freshFormStates[userId];
        return state && state.isZero;
      });
      if (!previousHasMeaningfulForm || !freshLooksZeroed) {
        return;
      }

      Array.prototype.slice.call(freshRoot.querySelectorAll('[data-session-row]')).forEach(function (row) {
        var userId = row.getAttribute('data-session-row-user-id');
        var previousState = userId ? previousFormStates[userId] : null;
        if (previousState && !previousState.isZero) {
          FHPB.SessionStandings.applyFormState(row, previousState);
        }
      });
    },

    captureFormStateByUserId: function (root) {
      var rows = root ? Array.prototype.slice.call(root.querySelectorAll('[data-session-row]')) : [];
      var formStateByUserId = {};

      rows.forEach(function (row) {
        var userId = row.getAttribute('data-session-row-user-id');
        var formElement = row.querySelector('.session-roster-form');
        var icon = formElement ? formElement.querySelector('.bi') : null;
        if (!userId || !formElement || !icon) {
          return;
        }

        var title = formElement.getAttribute('title') || '';
        var iconClass = icon.className || '';
        formStateByUserId[userId] = {
          title: title,
          formClass: formElement.className || '',
          iconClass: iconClass,
          isZero: /(^|[^0-9-])0 form$/i.test(title) || iconClass.indexOf('bi-dash-circle') !== -1
        };
      });

      return formStateByUserId;
    },

    applyFormState: function (row, formState) {
      if (!row || !formState) {
        return;
      }

      var formElement = row.querySelector('.session-roster-form');
      var icon = formElement ? formElement.querySelector('.bi') : null;
      if (!formElement || !icon) {
        return;
      }

      if (formState.formClass) {
        formElement.className = formState.formClass;
      }
      formElement.setAttribute('title', formState.title || '');
      if (formState.iconClass) {
        icon.className = formState.iconClass;
      }
    },

    writePreferredStat: function (root, statKey) {
      var storageKey = FHPB.SessionStandings.buildStorageBaseKey(root);
      if (!storageKey) {
        return;
      }

      FHPB.SessionStandings.writeValue(window.sessionStorage, storageKey + '.stat', statKey || 'rating');
    },

    prefersReducedMotion: function () {
      try {
        return !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
      } catch (error) {
        return false;
      }
    },

    isPending: function (root) {
      if (!root) {
        return false;
      }

      return root.getAttribute('data-session-standings-pending') === 'true';
    },

    readValue: function (storage, key) {
      if (!storage || !key) {
        return null;
      }

      try {
        return storage.getItem(key);
      } catch (error) {
        return null;
      }
    },

    writeValue: function (storage, key, value) {
      if (!storage || !key) {
        return;
      }

      try {
        storage.setItem(key, value);
      } catch (error) {
        // Ignore storage write failures.
      }
    },

    parseNumber: function (value) {
      if (value === null || value === undefined || value === '') {
        return null;
      }

      var parsed = Number(value);
      return Number.isFinite(parsed) ? parsed : null;
    },

    readUserId: function (value) {
      if (value === null || value === undefined || value === '') {
        return null;
      }
      return String(value);
    }
  };

  FHPB.SessionStandings.init();
})(window, document);

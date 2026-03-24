/*!
 * FHPB Component-Specific JavaScript
 * Scripts for specific components like passphrase cards, ladders, etc.
 */

(function(window, document) {
  'use strict';

  window.FHPB = window.FHPB || {};

  // Per-user passphrase UI removed; PassphraseCard component deprecated.

  /**
   * Match Logging Component
   */
  FHPB.MatchLog = {
    init: function() {
      document.addEventListener('DOMContentLoaded', function() {
        FHPB.MatchLog.bindValidation();
        FHPB.MatchLog.bindAutoComplete();
      });
    },

    bindValidation: function() {
      var form = document.querySelector('form[data-match-log]');
      if (!form) return;

      form.addEventListener('submit', function(e) {
        if (!FHPB.MatchLog.validateMatchData()) {
          e.preventDefault();
          return false;
        }
      });
    },

    validateMatchData: function() {
      var scoreA = document.querySelector('input[name="scoreA"]');
      var scoreB = document.querySelector('input[name="scoreB"]');
      // Keep the DOM hook but disable client-side score validation.
      // Players now confirm matches, so arbitrary scores are allowed.
      if (!scoreA || !scoreB) return true;

      // Mark fields as valid to clear any previous invalid state (if present).
      try {
        FHPB.Form.setFieldState(scoreA, 'valid');
        FHPB.Form.setFieldState(scoreB, 'valid');
      } catch (e) {
        // If the Form helper isn't available for some pages, ignore.
      }
      return true;
    },

    bindAutoComplete: function() {
      var playerInputs = document.querySelectorAll('input[data-player-search]');
      playerInputs.forEach(function(input) {
        var searchHandler = FHPB.Performance.debounce(function() {
          FHPB.MatchLog.searchPlayers(input);
        }, 300);

        input.addEventListener('input', searchHandler);
      });
    },

    searchPlayers: function(input) {
      var query = input.value.trim();
      if (query.length < 2) return;

      // This would typically make an AJAX call to search for players
      // For now, just a placeholder
      console.log('Searching for players:', query);
    }
  };

  /**
   * Ladder Display Component
   */
  FHPB.Ladder = {
    init: function() {
      document.addEventListener('DOMContentLoaded', function() {
        FHPB.Ladder.bindInteractions();
      });
    },

    bindInteractions: function() {
      // Add click handlers for ladder interactions
      var ladderRows = document.querySelectorAll('.ladder-row');
      ladderRows.forEach(function(row) {
        row.addEventListener('click', function() {
          FHPB.Ladder.showPlayerDetails(row);
        });
      });
    },

    showPlayerDetails: function(row) {
      var playerId = row.dataset.playerId;
      if (!playerId) return;

      // This would show player details modal or navigate to profile
      console.log('Show player details:', playerId);
    }
  };

  /**
   * Registration Form Component
   */
  FHPB.Registration = {
    init: function() {
      document.addEventListener('DOMContentLoaded', function() {
        FHPB.Registration.bindPasswordValidation();
        FHPB.Registration.bindEmailValidation();
      });
    },

    bindPasswordValidation: function() {
      var passwordField = document.getElementById('password');
      var confirmField = document.getElementById('confirmPassword');

      if (!passwordField || !confirmField) return;

      var validatePasswords = function() {
        var password = passwordField.value;
        var confirm = confirmField.value;

        if (password.length < 8) {
          FHPB.Form.setFieldState(passwordField, 'invalid', 'Password must be at least 8 characters');
          return false;
        }

        if (password !== confirm && confirm.length > 0) {
          FHPB.Form.setFieldState(confirmField, 'invalid', 'Passwords do not match');
          return false;
        }

        FHPB.Form.setFieldState(passwordField, 'valid');
        if (confirm.length > 0) {
          FHPB.Form.setFieldState(confirmField, 'valid');
        }
        return true;
      };

      passwordField.addEventListener('input', validatePasswords);
      confirmField.addEventListener('input', validatePasswords);
    },

    bindEmailValidation: function() {
      var emailField = document.getElementById('email');
      if (!emailField) return;

      emailField.addEventListener('blur', function() {
        var email = emailField.value;
        var emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

        if (email && !emailRegex.test(email)) {
          FHPB.Form.setFieldState(emailField, 'invalid', 'Please enter a valid email address');
        } else if (email) {
          FHPB.Form.setFieldState(emailField, 'valid');
        }
      });
    }
  };

  /**
   * Card Collapse
   * Promotes top-level app cards into the shared collapsible card pattern.
   */
  FHPB.CardCollapse = {
    collapseIdPrefix: 'fhpbCardCollapse',
    interactiveSelector: 'a, button, input, select, textarea, summary, [role="button"]',

    init: function() {
      document.addEventListener('DOMContentLoaded', function() {
        FHPB.CardCollapse.upgradeAll(document);
      });
    },

    upgradeAll: function(root) {
      var scope = root || document;
      var cards = FHPB.CardCollapse.collectCards(scope);
      var keys = Object.create(null);

      for (var index = 0; index < cards.length; index += 1) {
        var card = cards[index];
        if (!FHPB.CardCollapse.shouldUpgrade(card, scope)) {
          continue;
        }
        FHPB.CardCollapse.upgradeCard(card, keys);
      }
    },

    collectCards: function(root) {
      if (!root) {
        return [];
      }

      var cards = [];
      if (root.nodeType === 1 && root.matches && root.matches('.card')) {
        cards.push(root);
      }
      if (!root.querySelectorAll) {
        return cards;
      }

      var found = root.querySelectorAll('.card');
      for (var index = 0; index < found.length; index += 1) {
        cards.push(found[index]);
      }
      return cards;
    },

    shouldUpgrade: function(card, root) {
      if (!card || !card.classList) {
        return false;
      }
      if (card.dataset.cardCollapse === 'off' || card.dataset.cardCollapseUpgraded === 'true') {
        return false;
      }
      if (card.classList.contains('card-flat')) {
        return false;
      }

      var allowDetachedScope = !!root && root !== document;
      if (!allowDetachedScope && !card.closest('.app-page, .app-home')) {
        return false;
      }

      var nestedContainer = card.parentElement
        ? card.parentElement.closest('.card-body, .modal-body, .accordion-body, .offcanvas-body, .list-group-item')
        : null;
      if (nestedContainer) {
        return false;
      }

      var header = FHPB.CardCollapse.directChild(card, 'card-header');
      var body = FHPB.CardCollapse.directChild(card, 'card-body');
      if (!header || !body) {
        return false;
      }
      if (header.querySelector('.card-collapse-toggle')) {
        return false;
      }
      if (header.querySelector(FHPB.CardCollapse.interactiveSelector)) {
        return false;
      }

      return true;
    },

    directChild: function(card, className) {
      if (!card || !card.children) {
        return null;
      }
      for (var index = 0; index < card.children.length; index += 1) {
        var child = card.children[index];
        if (child.classList && child.classList.contains(className)) {
          return child;
        }
      }
      return null;
    },

    buildCollapseId: function(card, header, keys) {
      var source = card.getAttribute('data-card-collapse-key')
        || card.id
        || FHPB.CardCollapse.labelText(header)
        || 'section';
      var base = FHPB.CardCollapse.slugify(source);
      if (!base) {
        base = 'section';
      }

      keys[base] = (keys[base] || 0) + 1;
      var id = FHPB.CardCollapse.collapseIdPrefix + '-' + base + '-' + keys[base];
      while (document.getElementById(id)) {
        keys[base] += 1;
        id = FHPB.CardCollapse.collapseIdPrefix + '-' + base + '-' + keys[base];
      }
      return id;
    },

    slugify: function(value) {
      return String(value || '')
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '')
        .slice(0, 48);
    },

    labelText: function(header) {
      if (!header) {
        return 'section';
      }
      return String(header.textContent || '')
        .replace(/\s+/g, ' ')
        .trim();
    },

    upgradeCard: function(card, keys) {
      var header = FHPB.CardCollapse.directChild(card, 'card-header');
      var body = FHPB.CardCollapse.directChild(card, 'card-body');
      if (!header || !body) {
        return;
      }

      var collapseId = FHPB.CardCollapse.buildCollapseId(card, header, keys);
      var expandedByDefault = card.getAttribute('data-card-collapse-default') !== 'closed';
      var button = document.createElement('button');
      var title = document.createElement('div');
      var labelText = FHPB.CardCollapse.labelText(header) || 'section';
      var collapse = document.createElement('div');

      button.type = 'button';
      button.className = expandedByDefault ? 'btn card-collapse-toggle' : 'btn card-collapse-toggle collapsed';
      button.setAttribute('data-bs-toggle', 'collapse');
      button.setAttribute('data-bs-target', '#' + collapseId);
      button.setAttribute('aria-expanded', expandedByDefault ? 'true' : 'false');
      button.setAttribute('aria-controls', collapseId);
      button.setAttribute('aria-label', 'Toggle ' + labelText);

      title.className = 'card-collapse-title';
      while (header.firstChild) {
        title.appendChild(header.firstChild);
      }

      button.innerHTML = '<span class="card-collapse-spacer" aria-hidden="true"></span>';
      button.appendChild(title);
      button.insertAdjacentHTML('beforeend', '<i class="bi bi-chevron-down card-collapse-icon" aria-hidden="true"></i>');

      header.classList.add('card-collapse-header');
      header.appendChild(button);

      collapse.id = collapseId;
      collapse.className = expandedByDefault ? 'collapse show' : 'collapse';
      card.insertBefore(collapse, body);
      collapse.appendChild(body);

      card.dataset.cardCollapseUpgraded = 'true';
    }
  };

  /**
   * Card Collapse State
   * Keeps card-collapse widgets consistent across AJAX replacement and full reloads.
   */
  FHPB.CollapseState = {
    storagePrefix: 'fhpb.cardCollapse',
    bound: false,

    init: function() {
      document.addEventListener('DOMContentLoaded', function() {
        FHPB.CollapseState.bindEvents();
        FHPB.CollapseState.restoreAll(document);
      });
    },

    bindEvents: function() {
      if (FHPB.CollapseState.bound) return;

      document.addEventListener('shown.bs.collapse', function(event) {
        FHPB.CollapseState.persistFromEvent(event, true);
      });
      document.addEventListener('hidden.bs.collapse', function(event) {
        FHPB.CollapseState.persistFromEvent(event, false);
      });

      FHPB.CollapseState.bound = true;
    },

    persistFromEvent: function(event, expanded) {
      var collapse = event && event.target;
      if (!FHPB.CollapseState.isTrackedCollapse(collapse)) return;
      FHPB.CollapseState.writeState(collapse.id, expanded);
    },

    isTrackedCollapse: function(collapse) {
      if (!collapse || !collapse.id || !collapse.matches) return false;
      if (!collapse.matches('.collapse')) return false;
      return FHPB.CollapseState.findToggles(collapse.id, document).length > 0;
    },

    findToggles: function(collapseId, root) {
      if (!collapseId || !root || !root.querySelectorAll) return [];
      return Array.prototype.slice.call(
        root.querySelectorAll('.card-collapse-toggle[data-bs-target="#' + collapseId + '"]')
      );
    },

    storageKey: function(collapseId) {
      return FHPB.CollapseState.storagePrefix + '::' + window.location.pathname + '::' + collapseId;
    },

    writeState: function(collapseId, expanded) {
      if (!collapseId || !window.sessionStorage) return;
      try {
        window.sessionStorage.setItem(
          FHPB.CollapseState.storageKey(collapseId),
          expanded ? 'open' : 'closed'
        );
      } catch (e) {
        /* ignore storage failures */
      }
    },

    readState: function(collapseId) {
      if (!collapseId || !window.sessionStorage) return null;
      try {
        return window.sessionStorage.getItem(FHPB.CollapseState.storageKey(collapseId));
      } catch (e) {
        return null;
      }
    },

    captureCurrentState: function(collapseId, root) {
      if (!collapseId) return;
      var scope = root || document;
      var collapse = scope.querySelector ? scope.querySelector('#' + collapseId) : null;
      if (!FHPB.CollapseState.isTrackedCollapse(collapse)) return;
      FHPB.CollapseState.writeState(collapseId, collapse.classList.contains('show'));
    },

    restoreAll: function(root) {
      var scope = root || document;
      if (!scope || !scope.querySelectorAll) return;

      var collapses = scope.querySelectorAll('.collapse[id]');
      for (var i = 0; i < collapses.length; i++) {
        FHPB.CollapseState.restoreCollapse(collapses[i], scope);
      }
    },

    restoreCollapse: function(collapse, root) {
      if (!FHPB.CollapseState.isTrackedCollapse(collapse)) return;

      var state = FHPB.CollapseState.readState(collapse.id);
      if (state !== 'open' && state !== 'closed') return;

      FHPB.CollapseState.applyExpandedState(collapse, state === 'open', root || document);
    },

    applyExpandedState: function(collapse, expanded, root) {
      if (!collapse || !collapse.id || !collapse.classList) return;

      collapse.classList.toggle('show', !!expanded);
      var toggles = FHPB.CollapseState.findToggles(collapse.id, root || document);
      for (var i = 0; i < toggles.length; i++) {
        toggles[i].classList.toggle('collapsed', !expanded);
        toggles[i].setAttribute('aria-expanded', expanded ? 'true' : 'false');
      }
    }
  };

  // Initialize components
  FHPB.MatchLog.init();
  FHPB.Ladder.init();
  FHPB.Registration.init();
  FHPB.CardCollapse.init();
  FHPB.CollapseState.init();

  /**
   * Match confirmation handler (delegated)
   * Centralized so confirmation works everywhere (dashboard, season log, user fragments)
   */
  FHPB.Confirmations = {
    init: function() {
      document.addEventListener('DOMContentLoaded', function() {
        // Use event delegation to handle confirm buttons added anywhere on the page
        document.body.addEventListener('click', function(evt) {
          var btn = evt.target.closest && evt.target.closest('.confirm-match-btn');
          if (!btn) return;
          evt.preventDefault();
          evt.stopPropagation();

          FHPB.Confirmations.handleConfirm(btn);
        });

        // If standings are already marked as recalculating on page load,
        // keep refreshing until server reports the recalculation is complete.
        FHPB.Confirmations.watchStandingsRecalcOnLoad();
        FHPB.Confirmations.watchSessionInsightsRecalcOnLoad();
        FHPB.Confirmations.showPageLoadFloatingToast();
        FHPB.Confirmations.startMatchDashboardPolling();
      });
    },

      // Also handle form-based confirmations (delegated submit)
      // Forms with class `confirm-match-form` will be intercepted and sent via AJAX
      initFormDelegation: function() {
        document.addEventListener('DOMContentLoaded', function() {
          document.body.addEventListener('submit', function(ev) {
            var form = ev.target;
              if (!form || !form.classList) return;

              if (form.classList.contains('dispute-match-form')) {
                ev.preventDefault();
                ev.stopPropagation();

                var disputeBtn = form.querySelector('button');
                if (disputeBtn) {
                  FHPB.Confirmations.performDispute(form, disputeBtn);
                }
                return;
              }

              // Nullify (delete) match via AJAX: forms marked with .nullify-match-form
              if (form.classList.contains('nullify-match-form')) {
                ev.preventDefault();
                ev.stopPropagation();

                // Try to find match id on form attribute or data-match-id
                var matchId = form.getAttribute('data-match-id') || form.dataset.matchId;
                if (!matchId) {
                  var action = form.getAttribute('action');
                  if (action) {
                    var m = action.match(/\/matches\/(\d+)\/nullify/);
                    if (m) matchId = m[1];
                  }
                }

                var submitBtn = form.querySelector('button[type="submit"]');
                if (submitBtn) {
                  submitBtn.disabled = true;
                  submitBtn.dataset._fhpb_original = submitBtn.innerHTML;
                  submitBtn.innerHTML = '<i class="bi bi-hourglass-split me-1"></i>Deleting...';
                }

                var headers = FHPB.Csrf.headers({
                  'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                  'X-Requested-With': 'XMLHttpRequest'
                });

                var actionUrl = form.getAttribute('action');
                fetch(actionUrl, {
                  method: 'POST',
                  headers: headers,
                  credentials: 'same-origin',
                  body: FHPB.Confirmations.formBody(form)
                })
                  .then(function(response) {
                    if (response.ok) return response.json();
                    return response.text().then(function(text) { throw new Error(text || 'Delete failed'); });
                  })
                  .then(function(data) {
                    if (data && data.success) {
                      // Simpler: reload the page so server-side nullified state is authoritative
                      window.location.reload();
                      return;
                    }
                    throw new Error((data && data.message) ? data.message : 'Failed to delete match');
                  })
                  .catch(function(err) {
                    console.error('Nullify error:', err);
                    if (submitBtn) { submitBtn.disabled = false; submitBtn.innerHTML = submitBtn.dataset._fhpb_original || 'Delete'; }
                    FHPB.Confirmations.showFloatingMessage(err && err.message ? err.message : 'Delete failed', submitBtn, 'error');
                  });

                return;
              }

              // Forms with class `confirm-match-form` will be intercepted and sent via AJAX
              if (!form.classList.contains('confirm-match-form')) return;
              ev.preventDefault();
              ev.stopPropagation();

            // Try to find match id on form attribute or data-match-id
            var matchId = form.getAttribute('data-match-id') || form.dataset.matchId;
            if (!matchId) {
              // Try to parse from action url e.g. /matches/{id}/confirm
              var action = form.getAttribute('action');
              if (action) {
                var m = action.match(/\/matches\/(\d+)\/confirm/);
                if (m) matchId = m[1];
              }
            }

            // Disable submit button
            var submitBtn = form.querySelector('button[type="submit"]');
            if (submitBtn) {
              submitBtn.disabled = true;
              submitBtn.dataset._fhpb_original = submitBtn.innerHTML;
              submitBtn.innerHTML = '<i class="bi bi-hourglass-split me-1"></i>Confirming...';
            }

            // Use same handler as button flow
            if (matchId) {
              // Ensure the element passed to handleConfirm contains data-match-id
              if (submitBtn) {
                submitBtn.setAttribute('data-match-id', matchId);
              } else {
                form.setAttribute('data-match-id', matchId);
              }
              // Create a fake button element reference for positioning
              var fakeBtn = submitBtn || form;
              FHPB.Confirmations.handleConfirm(fakeBtn);
            } else {
              // Re-enable on failure to find id
              if (submitBtn) { submitBtn.disabled = false; submitBtn.innerHTML = submitBtn.dataset._fhpb_original || 'Confirm'; }
              console.warn('Confirm form submitted without match id');
            }
          });
        });
      },

    showFloatingMessage: function(message, referenceElement, variant) {
      var tone = variant || 'success';
      var background = 'linear-gradient(135deg, #28a745, #20c997)';
      if (tone === 'warning') {
        background = 'linear-gradient(135deg, #f59e0b, #f97316)';
      } else if (tone === 'error') {
        background = 'linear-gradient(135deg, #dc3545, #b91c1c)';
      } else if (tone === 'info') {
        background = 'linear-gradient(135deg, #0d6efd, #2563eb)';
      }

      // Re-implement a small floating message to match fragment behaviour
      var floatingDiv = document.createElement('div');
      floatingDiv.textContent = message;
      floatingDiv.style.cssText = '\n      position: fixed;\n      background: ' + background + ';\n      color: white;\n      padding: 8px 16px;\n      border-radius: 20px;\n      font-weight: bold;\n      font-size: 14px;\n      box-shadow: 0 4px 12px rgba(0,0,0,0.3);\n      z-index: 9999;\n      pointer-events: none;\n      animation: fhpbFloatUp 2s ease-out forwards;\n    ';

      // Position it relative to the reference element
      try {
        var rect = referenceElement.getBoundingClientRect();
        floatingDiv.style.left = (rect.left + rect.width / 2) + 'px';
        floatingDiv.style.top = (rect.top - 10) + 'px';
        floatingDiv.style.transform = 'translateX(-50%) translateY(0)';
      } catch (e) {
        // Fallback to the same centered toast lane used for page-level success feedback.
        floatingDiv.style.left = '50%';
        floatingDiv.style.top = '33vh';
        floatingDiv.style.transform = 'translateX(-50%) translateY(0)';
      }

      document.body.appendChild(floatingDiv);
      setTimeout(function() {
        if (floatingDiv.parentNode) floatingDiv.parentNode.removeChild(floatingDiv);
      }, 2000);
    },

    showPageLoadFloatingToast: function() {
      var toastState = FHPB.Confirmations.resolvePageLoadFloatingToast();
      if (!toastState) return;

      window.setTimeout(function() {
        FHPB.Confirmations.showFloatingMessage(toastState.message);
      }, 120);

      FHPB.Confirmations.clearPageLoadFloatingToastParams();
    },

    resolvePageLoadFloatingToast: function() {
      try {
        var url = new URL(window.location.href);
        var params = url.searchParams;
        var toastKey = params.get('toast');
        if (toastKey !== 'matchLogged') return null;

        var matchId = params.get('matchId');
        return {
          key: toastKey,
          message: matchId ? 'Match #' + matchId + ' logged!' : 'Match logged!'
        };
      } catch (e) {
        return null;
      }
    },

    clearPageLoadFloatingToastParams: function() {
      try {
        var url = new URL(window.location.href);
        var params = url.searchParams;
        if (params.get('toast') !== 'matchLogged') return;

        params.delete('toast');
        params.delete('toastMessage');
        params.delete('matchId');

        var nextUrl = url.pathname + (params.toString() ? '?' + params.toString() : '') + url.hash;
        window.history.replaceState({}, '', nextUrl);
      } catch (e) {
        console.warn('Unable to clear match logged toast parameters from URL:', e);
      }
    },

    matchDashboardPollTimer: null,
    matchDashboardPollInFlight: false,
    matchDashboardPollingBound: false,

    findMatchDashboardContainer: function() {
      return document.getElementById('matchDashboardContainer');
    },

    getMatchDashboardPollIntervalMs: function() {
      var container = FHPB.Confirmations.findMatchDashboardContainer();
      if (!container) return 60000;

      var raw = parseInt(container.getAttribute('data-refresh-interval-ms'), 10);
      if (!Number.isFinite(raw) || raw < 15000) return 60000;
      return raw;
    },

    computeNextMatchDashboardPollDelay: function() {
      var base = FHPB.Confirmations.getMatchDashboardPollIntervalMs();
      return base + Math.floor(Math.random() * 5000);
    },

    scheduleNextMatchDashboardPoll: function(delayMs) {
      var container = FHPB.Confirmations.findMatchDashboardContainer();
      if (!container || container.getAttribute('data-match-dashboard-auto-refresh') !== 'true') {
        return;
      }

      if (FHPB.Confirmations.matchDashboardPollTimer) {
        window.clearTimeout(FHPB.Confirmations.matchDashboardPollTimer);
      }

      var delay = Number.isFinite(delayMs) && delayMs >= 0
        ? delayMs
        : FHPB.Confirmations.computeNextMatchDashboardPollDelay();
      FHPB.Confirmations.matchDashboardPollTimer = window.setTimeout(function() {
        FHPB.Confirmations.pollMatchDashboard();
      }, delay);
    },

    startMatchDashboardPolling: function() {
      var container = FHPB.Confirmations.findMatchDashboardContainer();
      if (!container || container.getAttribute('data-match-dashboard-auto-refresh') !== 'true') {
        return;
      }

      if (!FHPB.Confirmations.matchDashboardPollingBound) {
        document.addEventListener('visibilitychange', function() {
          if (document.visibilityState === 'visible') {
            FHPB.Confirmations.scheduleNextMatchDashboardPoll(10000);
          }
        });
        FHPB.Confirmations.matchDashboardPollingBound = true;
      }

      FHPB.Confirmations.scheduleNextMatchDashboardPoll(FHPB.Confirmations.computeNextMatchDashboardPollDelay());
    },

    refreshMatchDashboardSection: function(options) {
      var settings = options || {};
      var currentContainer = FHPB.Confirmations.findMatchDashboardContainer();
      if (!currentContainer || !currentContainer.parentNode) {
        return Promise.resolve(false);
      }
      if (FHPB.Confirmations.matchDashboardPollInFlight) {
        return Promise.resolve(false);
      }

      FHPB.Confirmations.matchDashboardPollInFlight = true;

      return fetch(window.location.href, {
        cache: 'no-store',
        credentials: 'same-origin',
        headers: { 'X-Requested-With': 'XMLHttpRequest' }
      })
      .then(function(response) {
        if (response.ok) return response.text();
        throw new Error('Failed to refresh match dashboard');
      })
      .then(function(html) {
        var parser = new DOMParser();
        var doc = parser.parseFromString(html, 'text/html');
        var freshContainer = doc.getElementById('matchDashboardContainer');
        if (!freshContainer || !currentContainer.parentNode) {
          if (settings.reloadOnFailure) {
            window.location.reload();
          }
          return false;
        }

        var changed = currentContainer.innerHTML !== freshContainer.innerHTML;
        currentContainer.parentNode.replaceChild(freshContainer, currentContainer);
        if (window.FHPB && FHPB.DateTime && typeof FHPB.DateTime.initializeLocalTimes === 'function') {
          FHPB.DateTime.initializeLocalTimes();
        }
        return changed;
      })
      .catch(function(err) {
        console.error('Failed to refresh match dashboard:', err);
        if (settings.reloadOnFailure) {
          window.location.reload();
        }
        return false;
      })
      .then(function(changed) {
        FHPB.Confirmations.matchDashboardPollInFlight = false;
        if (settings.scheduleNext !== false) {
          FHPB.Confirmations.scheduleNextMatchDashboardPoll();
        }
        return changed;
      });
    },

    pollMatchDashboard: function() {
      var container = FHPB.Confirmations.findMatchDashboardContainer();
      if (!container || container.getAttribute('data-match-dashboard-auto-refresh') !== 'true') {
        return;
      }

      if (document.visibilityState === 'hidden') {
        FHPB.Confirmations.scheduleNextMatchDashboardPoll();
        return;
      }

      FHPB.Confirmations.refreshMatchDashboardSection({ scheduleNext: true, reloadOnFailure: false });
    },

    refreshStandingsAfterConfirm: function(attempt) {
      var tries = Number.isFinite(attempt) ? attempt : 0;
      var currentContainer = document.getElementById('ladderStandingsContainer');
      if (!currentContainer) return;

      fetch(window.location.href, {
        cache: 'no-store',
        credentials: 'same-origin',
        headers: { 'X-Requested-With': 'XMLHttpRequest' }
      })
      .then(function(response) {
        if (response.ok) return response.text();
        throw new Error('Failed to reload standings container');
      })
      .then(function(html) {
        var parser = new DOMParser();
        var doc = parser.parseFromString(html, 'text/html');
        var freshContainer = doc.getElementById('ladderStandingsContainer');
        if (!freshContainer || !currentContainer.parentNode) {
          window.location.reload();
          return;
        }

        currentContainer.parentNode.replaceChild(freshContainer, currentContainer);

        // Keep the local indicator visible briefly after confirm so users always see feedback.
        var pending = freshContainer.querySelector('.standings-recalc-indicator');
        if (pending) {
          FHPB.Confirmations.serverRecalcPendingSeen = true;
        }
        var localUntil = FHPB.Confirmations.localRecalcIndicatorUntil || 0;
        var shouldShowLocal = !pending && Date.now() < localUntil;
        if (shouldShowLocal) {
          FHPB.Confirmations.showLocalRecalcIndicator(0);
        }

        FHPB.Confirmations.maybeRefreshClimbFaster(doc, tries, pending);

        // Poll for a short window even when pending is not yet visible server-side.
        if ((pending || shouldShowLocal || tries < 3) && tries < 8) {
          window.setTimeout(function() {
            FHPB.Confirmations.refreshStandingsAfterConfirm(tries + 1);
          }, 700);
        }
      })
      .catch(function(err) {
        console.error('Failed to refresh standings container:', err);
        window.location.reload();
      });
    },

    watchStandingsRecalcOnLoad: function() {
      var slot = document.getElementById('standingsRecalcIndicatorSlot');
      if (!slot) return;
      var pending = slot.querySelector('.standings-recalc-indicator');
      if (!pending) return;
      FHPB.Confirmations.climbFasterRefreshPending = !!document.getElementById('climbFasterCardContainer');
      FHPB.Confirmations.serverRecalcPendingSeen = true;
      FHPB.Confirmations.refreshStandingsWhilePending(0);
    },

    refreshStandingsWhilePending: function(attempt) {
      var tries = Number.isFinite(attempt) ? attempt : 0;
      var maxTries = 90; // ~90s at 1s cadence
      var currentContainer = document.getElementById('ladderStandingsContainer');
      if (!currentContainer) return;

      fetch(window.location.href, {
        cache: 'no-store',
        credentials: 'same-origin',
        headers: { 'X-Requested-With': 'XMLHttpRequest' }
      })
      .then(function(response) {
        if (response.ok) return response.text();
        throw new Error('Failed to refresh standings container');
      })
      .then(function(html) {
        var parser = new DOMParser();
        var doc = parser.parseFromString(html, 'text/html');
        var freshContainer = doc.getElementById('ladderStandingsContainer');
        if (!freshContainer || !currentContainer.parentNode) {
          return;
        }

        currentContainer.parentNode.replaceChild(freshContainer, currentContainer);
        var pending = freshContainer.querySelector('.standings-recalc-indicator');
        FHPB.Confirmations.maybeRefreshClimbFaster(doc, tries, pending);
        if (pending && tries < maxTries) {
          window.setTimeout(function() {
            FHPB.Confirmations.refreshStandingsWhilePending(tries + 1);
          }, 1000);
        }
      })
      .catch(function(err) {
        console.error('Failed to poll standings recalculation state:', err);
      });
    },

    hasSessionInsights: function() {
      return !!document.getElementById('sessionStandingContainer')
        || !!document.getElementById('sessionReportCardContainer')
        || !!document.getElementById('climbFasterCardContainer');
    },

    isSessionInsightsPendingInDoc: function(doc) {
      var source = doc || document;
      var container = source.getElementById && source.getElementById('sessionReportCardContainer');
      if (!container) return false;
      return container.getAttribute('data-session-standings-pending') === 'true';
    },

    replaceContainerPreservingCollapse: function(containerId, doc, collapseId) {
      var currentContainer = document.getElementById(containerId);
      if (!currentContainer || !currentContainer.parentNode || !doc) return false;

      var freshContainer = doc.getElementById(containerId);
      if (!freshContainer) return false;

      if (collapseId && window.FHPB && FHPB.CollapseState) {
        FHPB.CollapseState.captureCurrentState(collapseId, currentContainer);
      }

      currentContainer.parentNode.replaceChild(freshContainer, currentContainer);

      if (window.FHPB && FHPB.CardCollapse) {
        FHPB.CardCollapse.upgradeAll(freshContainer);
      }
      if (window.FHPB && FHPB.CollapseState) {
        FHPB.CollapseState.restoreAll(freshContainer);
      }

      return true;
    },

    refreshSessionInsightContainers: function(doc) {
      if (!doc) return false;

      var replaced = false;
      replaced = FHPB.Confirmations.replaceContainerPreservingCollapse(
        'climbFasterCardContainer',
        doc,
        'climbFasterCardCollapse'
      ) || replaced;
      replaced = FHPB.Confirmations.replaceContainerPreservingCollapse(
        'sessionStandingContainer',
        doc
      ) || replaced;
      replaced = FHPB.Confirmations.replaceContainerPreservingCollapse(
        'sessionReportCardContainer',
        doc,
        'sessionReportCardCollapse'
      ) || replaced;

      if (replaced && window.FHPB && FHPB.DateTime && typeof FHPB.DateTime.initializeLocalTimes === 'function') {
        FHPB.DateTime.initializeLocalTimes();
      }

      return replaced;
    },

    refreshSessionInsightsOnce: function(options) {
      var settings = options || {};
      if (!FHPB.Confirmations.hasSessionInsights()) {
        if (settings.reloadOnFailure) {
          window.location.reload();
        }
        return Promise.resolve(false);
      }

      return fetch(window.location.href, {
        cache: 'no-store',
        credentials: 'same-origin',
        headers: { 'X-Requested-With': 'XMLHttpRequest' }
      })
      .then(function(response) {
        if (response.ok) return response.text();
        throw new Error('Failed to refresh session insights');
      })
      .then(function(html) {
        var parser = new DOMParser();
        var doc = parser.parseFromString(html, 'text/html');
        var replaced = false;
        var changed = false;

        ['climbFasterCardContainer', 'sessionStandingContainer', 'sessionReportCardContainer'].forEach(function(containerId) {
          var currentContainer = document.getElementById(containerId);
          var freshContainer = doc.getElementById(containerId);
          if (!currentContainer || !freshContainer) return;
          if (currentContainer.innerHTML !== freshContainer.innerHTML) {
            changed = true;
          }
        });

        replaced = FHPB.Confirmations.refreshSessionInsightContainers(doc);
        if (!replaced && settings.reloadOnFailure) {
          window.location.reload();
        }
        return {
          replaced: replaced,
          changed: changed,
          pending: FHPB.Confirmations.isSessionInsightsPendingInDoc(doc)
        };
      })
      .catch(function(err) {
        console.error('Failed to refresh session insights:', err);
        if (settings.reloadOnFailure) {
          window.location.reload();
        }
        return { replaced: false, changed: false, pending: false };
      });
    },

    refreshSessionInsightsAfterConfirm: function() {
      if (!FHPB.Confirmations.hasSessionInsights()) return;

      FHPB.Confirmations.refreshSessionInsightsOnce({ reloadOnFailure: true })
        .then(function(result) {
          if (!result) return;
        });
    },

    watchSessionInsightsRecalcOnLoad: function() {
      if (!FHPB.Confirmations.hasSessionInsights()) return;
      if (!FHPB.Confirmations.isSessionInsightsPendingInDoc(document)) return;
      FHPB.Confirmations.refreshSessionInsightsOnce({ reloadOnFailure: false });
    },

    localRecalcIndicatorUntil: 0,
    climbFasterRefreshPending: false,
    serverRecalcPendingSeen: false,

    showLocalRecalcIndicator: function(holdMs) {
      var slot = document.getElementById('standingsRecalcIndicatorSlot');
      if (!slot) return;

      var hold = Number.isFinite(holdMs) ? holdMs : 0;
      if (hold > 0) {
        var until = Date.now() + hold;
        if (until > FHPB.Confirmations.localRecalcIndicatorUntil) {
          FHPB.Confirmations.localRecalcIndicatorUntil = until;
        }
      }

      var existing = slot.querySelector('.standings-recalc-indicator');
      if (existing) return;

      var indicator = document.createElement('span');
      indicator.id = 'standingsRecalcIndicator';
      indicator.className = 'standings-recalc-indicator';
      indicator.setAttribute('role', 'status');
      indicator.setAttribute('aria-live', 'polite');
      indicator.innerHTML = '<i class="bi bi-arrow-repeat"></i> Please wait, recalculating standings..';
      slot.appendChild(indicator);
    },

    maybeRefreshClimbFaster: function(doc, tries, pending) {
      if (!FHPB.Confirmations.climbFasterRefreshPending || pending) return;

      var serverPendingSeen = !!FHPB.Confirmations.serverRecalcPendingSeen;
      if (!serverPendingSeen && tries < 3) return;

      FHPB.Confirmations.refreshClimbFasterCard(doc);
      FHPB.Confirmations.climbFasterRefreshPending = false;
    },

    refreshClimbFasterCard: function(doc) {
      if (FHPB.Confirmations.replaceContainerPreservingCollapse(
        'climbFasterCardContainer',
        doc,
        'climbFasterCardCollapse'
      ) && window.FHPB && FHPB.DateTime && typeof FHPB.DateTime.initializeLocalTimes === 'function') {
        FHPB.DateTime.initializeLocalTimes();
      }
    },

    parseResponsePayload: function(response) {
      return response.text().then(function(text) {
        if (!text) return {};
        try {
          return JSON.parse(text);
        } catch (e) {
          return { message: text };
        }
      });
    },

    findMatchRoot: function(element) {
      if (!element || !element.closest) return null;
      return element.closest('[data-match-id]');
    },

    shouldRemoveOnConfirm: function(matchRoot) {
      if (!matchRoot || !matchRoot.closest) return false;
      return !!matchRoot.closest('[data-remove-on-confirm="true"]');
    },

    removeConfirmedMatchCard: function(matchRoot) {
      if (!matchRoot) return false;
      var removable = (matchRoot.closest && matchRoot.closest('.match-dashboard-card')) || matchRoot;
      if (!removable || !removable.parentNode) return false;

      removable.parentNode.removeChild(removable);
      FHPB.Confirmations.updateDashboardEmptyState();
      return true;
    },

    updateDashboardEmptyState: function() {
      var dashboardList = document.getElementById('matchDashboardList');
      var emptyMessage = document.getElementById('confirmationCompleteMessage');
      if (!dashboardList || !emptyMessage) return;

      if (dashboardList.querySelector('.match-dashboard-card')) return;
      emptyMessage.classList.remove('d-none');
    },

    refreshMatchRowFragment: function(matchId, currentRoot) {
      if (!currentRoot || !currentRoot.parentNode) {
        window.location.reload();
        return;
      }

      fetch('/matches/' + encodeURIComponent(matchId) + '/fragment', { credentials: 'same-origin' })
        .then(function(response) {
          if (!response.ok) throw new Error('Failed to load fragment');
          return response.text();
        })
        .then(function(html) {
          var parser = new DOMParser();
          var doc = parser.parseFromString(html, 'text/html');
          var newNode = doc.querySelector('[data-match-id="' + matchId + '"]') || doc.body.firstElementChild;
          if (!newNode) {
            window.location.reload();
            return;
          }

          currentRoot.parentNode.replaceChild(newNode, currentRoot);
        })
        .catch(function(err) {
          console.error('Failed to refresh match row (fragment):', err);
          window.location.reload();
        });
    },

    syncAfterConfirmationStateChange: function(matchId, button, skipFragmentRefresh) {
      if (skipFragmentRefresh) {
        window.setTimeout(function() {
          window.location.reload();
        }, 450);
        return;
      }

      var currentRoot = FHPB.Confirmations.findMatchRoot(button)
        || (button.closest && button.closest('.match-dashboard-card'));
      if (!currentRoot) {
        window.location.reload();
        return;
      }

      if (currentRoot.closest && currentRoot.closest('#matchDashboardContainer')) {
        FHPB.Confirmations.refreshMatchDashboardSection({ scheduleNext: true, reloadOnFailure: true });
        return;
      }

      if (FHPB.Confirmations.shouldRemoveOnConfirm(currentRoot)
          && FHPB.Confirmations.removeConfirmedMatchCard(currentRoot)) {
        return;
      }

      FHPB.Confirmations.refreshMatchRowFragment(matchId, currentRoot);
    },

    formBody: function(form, extras) {
      var params = new URLSearchParams();
      if (!form || typeof FormData !== 'function') {
        if (extras) {
          Object.keys(extras).forEach(function(key) {
            if (extras[key] !== undefined && extras[key] !== null && extras[key] !== '') {
              params.append(key, extras[key]);
            }
          });
        }
        return params;
      }
      var formData = new FormData(form);
      formData.forEach(function(value, key) {
        params.append(key, value);
      });
      if (extras) {
        Object.keys(extras).forEach(function(key) {
          if (extras[key] === undefined || extras[key] === null || extras[key] === '') return;
          params.set(key, extras[key]);
        });
      }
      return params;
    },

    showDuplicateConfirmWarning: function(button, payload) {
      var duplicateWarningMatchId = payload && payload.duplicateWarningMatchId;
      var message = (payload && payload.message)
        ? payload.message
        : 'It looks like another copy of this match was already confirmed. Are you sure you want to confirm this one?';

      window.fhpbConfirm(message, function() {
        FHPB.Confirmations.handleConfirm(button, {
          acceptedDuplicateWarningMatchId: duplicateWarningMatchId
        });
      }, { confirmText: 'Confirm Anyway' });
    },

    handleConfirmationConflict: function(matchId, button, skipFragmentRefresh, message) {
      FHPB.Confirmations.showFloatingMessage(message || 'That match was already updated.', button, 'info');

      if (document.getElementById('ladderStandingsContainer')) {
        FHPB.Confirmations.climbFasterRefreshPending = !!document.getElementById('climbFasterCardContainer');
        FHPB.Confirmations.serverRecalcPendingSeen = false;
        FHPB.Confirmations.showLocalRecalcIndicator(2500);
        FHPB.Confirmations.refreshStandingsAfterConfirm(0);
      } else if (FHPB.Confirmations.hasSessionInsights()) {
        FHPB.Confirmations.refreshSessionInsightsAfterConfirm();
      }

      window.setTimeout(function() {
        FHPB.Confirmations.syncAfterConfirmationStateChange(matchId, button, skipFragmentRefresh);
      }, 250);
    },

    handleConfirm: function(button, options) {
      options = options || {};
      var matchId = button.getAttribute('data-match-id') || button.dataset.matchId;
      if (!matchId) return;
      var skipFragmentRefresh = !!(button.closest && button.closest('[data-skip-confirm-fragment-refresh="true"]'));
      var form = button.closest && button.closest('form');

      // Remove the confirmation hint text from the surrounding row if present
      try {
        var _search = 'Confirm this match to improve';
        var _anc = button;
        while (_anc && _anc !== document.body) {
          var _nodes = _anc.querySelectorAll && _anc.querySelectorAll('*');
          if (_nodes && _nodes.length) {
            for (var _i = 0; _i < _nodes.length; _i++) {
              var _n = _nodes[_i];
              if (_n && _n.textContent && _n.textContent.indexOf(_search) !== -1) {
                _n.parentNode && _n.parentNode.removeChild(_n);
                _anc = null; break;
              }
            }
          }
          _anc = _anc && _anc.parentNode;
        }
      } catch (e) {
        /* ignore DOM removal errors */
      }

      // Disable and show loading
      button.disabled = true;
      var originalHtml = button.innerHTML;
      button.innerHTML = '<i class="bi bi-hourglass-split me-1"></i>Confirming...';

      var headers = FHPB.Csrf.headers({
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
        'X-Requested-With': 'XMLHttpRequest'
      });

      fetch('/matches/' + encodeURIComponent(matchId) + '/confirm', {
        method: 'POST',
        headers: headers,
        credentials: 'same-origin',
        body: FHPB.Confirmations.formBody(form, {
          duplicateWarningAcceptedMatchId: options.acceptedDuplicateWarningMatchId
        })
      })
      .then(function(response) {
        return FHPB.Confirmations.parseResponsePayload(response).then(function(payload) {
          if (response.ok) return payload;

          var message = (payload && payload.message) ? payload.message : 'Confirmation failed';
          var error = new Error(message);
          error.status = response.status;
          error.payload = payload;
          throw error;
        });
      })
      .then(function(data) {
        if (data.success) {
          // If this was a first manual confirmation, show floating rating boost
          if (data.firstManual) {
            FHPB.Confirmations.showFloatingMessage('+ Confirmed!', button);
          }

          FHPB.Confirmations.climbFasterRefreshPending = !!document.getElementById('climbFasterCardContainer');
          FHPB.Confirmations.serverRecalcPendingSeen = false;
          FHPB.Confirmations.showLocalRecalcIndicator(4000);
          if (document.getElementById('ladderStandingsContainer')) {
            FHPB.Confirmations.refreshStandingsAfterConfirm(0);
          } else if (FHPB.Confirmations.hasSessionInsights()) {
            FHPB.Confirmations.refreshSessionInsightsAfterConfirm();
          }
          FHPB.Confirmations.syncAfterConfirmationStateChange(matchId, button, skipFragmentRefresh);
        } else {
          throw new Error(data.message || 'Failed to confirm match');
        }
      })
      .catch(function(err) {
        if (err && err.payload && err.payload.warningCode === 'duplicateConfirmedMatch') {
          button.disabled = false;
          button.innerHTML = originalHtml;
          FHPB.Confirmations.showDuplicateConfirmWarning(button, err.payload);
          return;
        }

        if (err && err.status === 409) {
          FHPB.Confirmations.handleConfirmationConflict(matchId, button, skipFragmentRefresh, err.message);
          return;
        }

        console.error('Confirmation error:', err);
        button.disabled = false;
        button.innerHTML = '<i class="bi bi-exclamation-triangle me-1"></i>Retry';
        if (err && err.message) {
          FHPB.Confirmations.showFloatingMessage(err.message, button, 'error');
        }
        setTimeout(function() { if (button) button.innerHTML = originalHtml; }, 2000);
      });
    }
    ,
    // Handle nullify button which uses a modal or native confirm and then performs AJAX
    // Signature supports either (event, button, message) or the older (button, message)
    handleNullifyButton: function(eventOrButton, maybeButton, maybeMessage) {
      var event = null, button, message;
      if (maybeButton === undefined) {
        // Called as handleNullifyButton(button, message)
        button = eventOrButton;
        message = maybeButton;
      } else {
        // Called as handleNullifyButton(event, button, message)
        event = eventOrButton;
        button = maybeButton;
        message = maybeMessage;
      }

      // If we have an event, stop propagation to prevent enclosing link navigation
      try {
        if (event && typeof event.preventDefault === 'function') {
          event.preventDefault();
          event.stopPropagation();
        }
      } catch (e) {
        // ignore
      }

      if (!button) return;
      var form = button.closest && button.closest('form');
      if (!form) return;
      var useModal = false;
      try { useModal = window.matchMedia && window.matchMedia('(max-width:767px)').matches; } catch (e) { useModal = false; }

      var perform = function() { FHPB.Confirmations.performNullify(form, button); };
      if (!useModal) {
        if (window.confirm(message)) perform();
        return;
      }
      window.fhpbConfirm(message, perform, { confirmText: 'Delete' });
    },

    performNullify: function(form, button) {
      // Try to find match id on form attribute or data-match-id
      var matchId = form.getAttribute('data-match-id') || form.dataset.matchId;
      if (!matchId) {
        var action = form.getAttribute('action');
        if (action) {
          var m = action.match(/\/matches\/(\d+)\/nullify/);
          if (m) matchId = m[1];
        }
      }

      var submitBtn = button;
      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.dataset._fhpb_original = submitBtn.innerHTML;
        submitBtn.innerHTML = '<i class="bi bi-hourglass-split me-1"></i>Deleting...';
      }

      var headers = FHPB.Csrf.headers({
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
        'X-Requested-With': 'XMLHttpRequest'
      });

      var actionUrl = form.getAttribute('action');
      fetch(actionUrl, {
        method: 'POST',
        headers: headers,
        credentials: 'same-origin',
        body: FHPB.Confirmations.formBody(form)
      })
        .then(function(response) {
          if (response.ok) return response.json();
          return response.text().then(function(text) { throw new Error(text || 'Delete failed'); });
        })
        .then(function(data) {
          if (data && data.success) {
            // Reload the page so server state is authoritative
            window.location.reload();
            return;
          }
          throw new Error((data && data.message) ? data.message : 'Failed to delete match');
        })
        .catch(function(err) {
          console.error('Nullify error:', err);
          if (submitBtn) { submitBtn.disabled = false; submitBtn.innerHTML = submitBtn.dataset._fhpb_original || '<i class="bi bi-trash3"></i>'; }
          FHPB.Confirmations.showFloatingMessage(err && err.message ? err.message : 'Delete failed', submitBtn, 'error');
        });
    },

    handleConfirmedNullifyButton: function(eventOrButton, maybeButton, maybeMessage) {
      var event = null, button, message;
      if (maybeButton === undefined) {
        button = eventOrButton;
        message = maybeButton;
      } else {
        event = eventOrButton;
        button = maybeButton;
        message = maybeMessage;
      }

      try {
        if (event && typeof event.preventDefault === 'function') {
          event.preventDefault();
          event.stopPropagation();
        }
      } catch (e) {
        // ignore
      }

      if (!button) return;
      var form = button.closest && button.closest('form');
      if (!form) return;
      var useModal = false;
      try { useModal = window.matchMedia && window.matchMedia('(max-width:767px)').matches; } catch (e) { useModal = false; }

      var perform = function() { FHPB.Confirmations.performConfirmedNullify(form, button); };
      if (!useModal) {
        if (window.confirm(message)) perform();
        return;
      }
      window.fhpbConfirm(message, perform, { confirmText: 'Continue' });
    },

    performConfirmedNullify: function(form, button) {
      var matchId = form.getAttribute('data-match-id') || form.dataset.matchId;
      if (!matchId) {
        var action = form.getAttribute('action');
        if (action) {
          var m = action.match(/\/matches\/(\d+)\/request-nullify/);
          if (m) matchId = m[1];
        }
      }
      if (!matchId) return;

      var skipFragmentRefresh = !!(button.closest && button.closest('[data-skip-confirm-fragment-refresh="true"]'));
      var submitBtn = button;
      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.dataset._fhpb_original = submitBtn.innerHTML;
        submitBtn.innerHTML = '<i class="bi bi-hourglass-split me-1"></i>';
      }

      var headers = FHPB.Csrf.headers({
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
        'X-Requested-With': 'XMLHttpRequest'
      });

      fetch(form.getAttribute('action'), {
        method: 'POST',
        headers: headers,
        credentials: 'same-origin',
        body: FHPB.Confirmations.formBody(form)
      })
        .then(function(response) {
          return FHPB.Confirmations.parseResponsePayload(response).then(function(payload) {
            if (response.ok) return payload;

            var message = (payload && payload.message) ? payload.message : 'Removal request failed';
            var error = new Error(message);
            error.status = response.status;
            error.payload = payload;
            throw error;
          });
        })
        .then(function(data) {
          if (data && data.success) {
            FHPB.Confirmations.showFloatingMessage(
              (data && data.message) ? data.message : 'Removal updated',
              submitBtn,
              'info'
            );
            FHPB.Confirmations.syncAfterConfirmationStateChange(matchId, submitBtn, skipFragmentRefresh);
            return;
          }
          throw new Error((data && data.message) ? data.message : 'Removal request failed');
        })
        .catch(function(err) {
          console.error('Confirmed nullify error:', err);
          if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.innerHTML = submitBtn.dataset._fhpb_original || '<i class="bi bi-arrow-counterclockwise"></i>';
          }
          FHPB.Confirmations.showFloatingMessage(
            err && err.message ? err.message : 'Removal request failed',
            submitBtn,
            'error'
          );
        });
    },

    handleDisputeButton: function(eventOrButton, maybeButton, maybeMessage) {
      var event = null, button, message;
      if (maybeButton === undefined) {
        button = eventOrButton;
        message = maybeButton;
      } else {
        event = eventOrButton;
        button = maybeButton;
        message = maybeMessage;
      }

      try {
        if (event && typeof event.preventDefault === 'function') {
          event.preventDefault();
          event.stopPropagation();
        }
      } catch (e) {
        // ignore
      }

      if (!button) return;
      var form = button.closest && button.closest('form');
      if (!form) return;
      var useModal = false;
      try { useModal = window.matchMedia && window.matchMedia('(max-width:767px)').matches; } catch (e) { useModal = false; }

      var perform = function() { FHPB.Confirmations.performDispute(form, button); };
      if (!useModal) {
        if (window.confirm(message)) perform();
        return;
      }
      window.fhpbConfirm(message, perform, { confirmText: 'Dispute' });
    },

    performDispute: function(form, button) {
      var matchId = form.getAttribute('data-match-id') || form.dataset.matchId;
      if (!matchId) {
        var action = form.getAttribute('action');
        if (action) {
          var m = action.match(/\/matches\/(\d+)\/dispute/);
          if (m) matchId = m[1];
        }
      }
      if (!matchId) return;

      var skipFragmentRefresh = !!(button.closest && button.closest('[data-skip-confirm-fragment-refresh="true"]'));
      var submitBtn = button;
      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.dataset._fhpb_original = submitBtn.innerHTML;
        submitBtn.innerHTML = '<i class="bi bi-hourglass-split me-1"></i>';
      }

      var headers = FHPB.Csrf.headers({
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
        'X-Requested-With': 'XMLHttpRequest'
      });

      fetch(form.getAttribute('action'), {
        method: 'POST',
        headers: headers,
        credentials: 'same-origin',
        body: FHPB.Confirmations.formBody(form)
      })
        .then(function(response) {
          return FHPB.Confirmations.parseResponsePayload(response).then(function(payload) {
            if (response.ok) return payload;

            var message = (payload && payload.message) ? payload.message : 'Dispute failed';
            var error = new Error(message);
            error.status = response.status;
            error.payload = payload;
            throw error;
          });
        })
        .then(function(data) {
          if (data && data.success) {
            FHPB.Confirmations.showFloatingMessage('Match disputed', submitBtn, 'warning');
            FHPB.Confirmations.syncAfterConfirmationStateChange(matchId, submitBtn, skipFragmentRefresh);
            return;
          }
          throw new Error((data && data.message) ? data.message : 'Dispute failed');
        })
        .catch(function(err) {
          console.error('Dispute error:', err);
          if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.innerHTML = submitBtn.dataset._fhpb_original || '<i class="bi bi-flag"></i>';
          }
          FHPB.Confirmations.showFloatingMessage(err && err.message ? err.message : 'Dispute failed', submitBtn, 'error');
        });
    }
  };

  // Initialize confirmations
  FHPB.Confirmations.init();
  FHPB.Confirmations.initFormDelegation();

  // Ensure floating animation keyframes exist
  (function() {
    if (!document.getElementById('fhpb-floating-style')) {
      var style = document.createElement('style');
      style.id = 'fhpb-floating-style';
      style.textContent = '\n@keyframes fhpbFloatUp {\n  0% { opacity: 0; transform: translateX(-50%) translateY(0) scale(0.8); }\n  10% { opacity: 1; transform: translateX(-50%) translateY(-10px) scale(1); }\n  90% { opacity: 1; transform: translateX(-50%) translateY(-50px) scale(1); }\n  100% { opacity: 0; transform: translateX(-50%) translateY(-70px) scale(0.9); }\n}\n';
      document.head.appendChild(style);
    }
  })();

})(window, document);

// Expose a small helper for the dashboard refresh button used in the fragment
window.refreshMatchDashboard = function() {
  try {
    if (window.FHPB && FHPB.Confirmations && typeof FHPB.Confirmations.refreshMatchDashboardSection === 'function') {
      FHPB.Confirmations.refreshMatchDashboardSection({ reloadOnFailure: true, scheduleNext: true });
      return false;
    }
    window.location.reload();
  } catch (e) {
    console.error('refreshMatchDashboard failed', e);
  }
  return false;
};

window.refreshSessionReportCard = function() {
  try {
    if (window.FHPB && FHPB.Confirmations && typeof FHPB.Confirmations.refreshSessionInsightsOnce === 'function') {
      FHPB.Confirmations.refreshSessionInsightsOnce({ reloadOnFailure: true });
      return false;
    }
    window.location.reload();
  } catch (e) {
    console.error('refreshSessionReportCard failed', e);
  }
  return false;
};

// Small cross-page confirm helper that uses native confirm on larger screens
// and a Bootstrap modal on small/mobile screens so text is reliably visible.
window.fhpbConfirm = function(message, onConfirm, options) {
  // If no callback provided, fall back to simple synchronous confirm
  if (typeof onConfirm !== 'function') {
    try { return window.confirm(message); } catch (e) { return false; }
  }

  var useModal = false;
  try {
    useModal = window.matchMedia && window.matchMedia('(max-width:767px)').matches;
  } catch (e) { useModal = false; }

  if (!useModal) {
    // Synchronous path: native confirm
    if (window.confirm(message)) onConfirm();
    return;
  }

  // Ensure modal exists
  var modalId = 'fhpbConfirmModal';
  var modalEl = document.getElementById(modalId);
  if (!modalEl) {
    modalEl = document.createElement('div');
    modalEl.id = modalId;
    modalEl.className = 'modal fade';
    modalEl.tabIndex = -1;
    modalEl.innerHTML = '\n    <div class="modal-dialog modal-dialog-centered">\n      <div class="modal-content">\n        <div class="modal-header">\n          <h5 class="modal-title">Confirm</h5>\n          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>\n        </div>\n        <div class="modal-body">\n          <p class="fhpb-confirm-message mb-0"></p>\n        </div>\n        <div class="modal-footer">\n          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>\n          <button type="button" class="btn btn-primary fhpb-confirm-yes">Confirm</button>\n        </div>\n      </div>\n    </div>\n    ';
    document.body.appendChild(modalEl);
  }

  // Populate and show modal
  var msgEl = modalEl.querySelector('.fhpb-confirm-message');
  if (msgEl) msgEl.textContent = message;
  var yesBtn = modalEl.querySelector('.fhpb-confirm-yes');

  // Safe guard: remove any previous handlers
  var newYes = yesBtn.cloneNode(true);
  // If caller provided a custom confirm button label, set it here
  try {
    if (options && options.confirmText) {
      // replace text content while preserving possible inner HTML structure
      newYes.textContent = options.confirmText;
    }
  } catch (e) {
    // ignore
  }
  yesBtn.parentNode.replaceChild(newYes, yesBtn);

  var bsModal = new bootstrap.Modal(modalEl);
  newYes.addEventListener('click', function () {
    try { onConfirm(); } catch (e) { console.error('fhpbConfirm callback error', e); }
    bsModal.hide();
  });
  bsModal.show();
};

// Helper to attach to form onsubmit handlers: returns boolean (true to allow submit)
window.handleConfirmForm = function(form, message) {
  if (!form) return false;
  var useModal = false;
  try { useModal = window.matchMedia && window.matchMedia('(max-width:767px)').matches; } catch (e) { useModal = false; }
  if (!useModal) {
    return window.confirm(message);
  }
  // Use modal: prevent default submit and show modal; submit when confirmed
  window.fhpbConfirm(message, function() { form.submit(); });
  return false;
};

(function(document) {
  function resetBadgeDetailPosition(trigger) {
    if (!trigger) {
      return;
    }
    var overlay = trigger.querySelector('[data-badge-detail-overlay]');
    if (overlay) {
      overlay.style.removeProperty('--badge-detail-offset-x');
    }
  }

  function positionBadgeDetail(trigger) {
    if (!trigger) {
      return;
    }
    var overlay = trigger.querySelector('[data-badge-detail-overlay]');
    if (!overlay || overlay.classList.contains('d-none')) {
      return;
    }
    overlay.style.setProperty('--badge-detail-offset-x', '0px');

    var viewportWidth = window.innerWidth || document.documentElement.clientWidth || 0;
    if (!viewportWidth) {
      return;
    }

    var viewportPadding = 12;
    var overlayRect = overlay.getBoundingClientRect();
    var shiftX = 0;
    var minLeft = viewportPadding;
    var maxRight = viewportWidth - viewportPadding;

    if (overlayRect.left < minLeft) {
      shiftX += minLeft - overlayRect.left;
    }
    if (overlayRect.right > maxRight) {
      shiftX -= overlayRect.right - maxRight;
    }

    overlay.style.setProperty('--badge-detail-offset-x', Math.round(shiftX) + 'px');
  }

  function closeBadgeDetails(except) {
    Array.prototype.slice.call(document.querySelectorAll('[data-badge-detail-trigger].is-details-open')).forEach(function(trigger) {
      if (trigger !== except) {
        trigger.classList.remove('is-details-open');
        trigger.setAttribute('aria-expanded', 'false');
        resetBadgeDetailPosition(trigger);
      }
    });
  }

  function toggleBadgeDetail(trigger) {
    if (!trigger) {
      return false;
    }
    var overlay = trigger.querySelector('[data-badge-detail-overlay]');
    if (!overlay || overlay.classList.contains('d-none')) {
      return false;
    }
    var nextOpen = !trigger.classList.contains('is-details-open');
    closeBadgeDetails(nextOpen ? trigger : null);
    trigger.classList.toggle('is-details-open', nextOpen);
    trigger.setAttribute('aria-expanded', nextOpen ? 'true' : 'false');
    if (nextOpen) {
      if (typeof window.requestAnimationFrame === 'function') {
        window.requestAnimationFrame(function() {
          positionBadgeDetail(trigger);
        });
      } else {
        positionBadgeDetail(trigger);
      }
    } else {
      resetBadgeDetailPosition(trigger);
    }
    return true;
  }

  document.addEventListener('click', function(event) {
    var trigger = event.target.closest('[data-badge-detail-trigger]');
    if (!trigger) {
      closeBadgeDetails();
      return;
    }
    if (toggleBadgeDetail(trigger)) {
      event.preventDefault();
      event.stopPropagation();
    }
  });

  document.addEventListener('keydown', function(event) {
    var trigger = event.target.closest('[data-badge-detail-trigger]');
    if (event.key === 'Escape') {
      closeBadgeDetails();
      return;
    }
    if (!trigger) {
      return;
    }
    if (event.key === 'Enter' || event.key === ' ') {
      if (toggleBadgeDetail(trigger)) {
        event.preventDefault();
      }
    }
  });

  window.addEventListener('resize', function() {
    Array.prototype.slice.call(document.querySelectorAll('[data-badge-detail-trigger].is-details-open')).forEach(function(trigger) {
      positionBadgeDetail(trigger);
    });
  });
})(document);

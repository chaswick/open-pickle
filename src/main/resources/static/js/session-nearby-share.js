(function (window, document) {
  'use strict';

  function csrfHeaders(baseHeaders) {
    if (window.FHPB && window.FHPB.Csrf && typeof window.FHPB.Csrf.headers === 'function') {
      return window.FHPB.Csrf.headers(baseHeaders || {});
    }
    return baseHeaders || {};
  }

  function isSecureForGeolocation() {
    if (typeof window.isSecureContext === 'boolean') {
      return window.isSecureContext;
    }
    const host = (window.location && window.location.hostname) || '';
    return window.location.protocol === 'https:' || host === 'localhost' || host === '127.0.0.1';
  }

  function requestJson(url, options) {
    return fetch(url, options || {}).then(function (response) {
      return response.json().catch(function () {
        return {};
      }).then(function (payload) {
        if (!response.ok) {
          const message = payload && payload.message ? payload.message : 'Request failed.';
          throw new Error(message);
        }
        return payload;
      });
    });
  }

  function postJson(url, body) {
    return requestJson(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: csrfHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(body || {})
    });
  }

  function getJson(url) {
    return requestJson(url, {
      credentials: 'same-origin',
      headers: { 'Accept': 'application/json' }
    });
  }

  function getCurrentPosition() {
    return new Promise(function (resolve, reject) {
      if (!navigator.geolocation) {
        reject(new Error('This browser does not support location access.'));
        return;
      }
      navigator.geolocation.getCurrentPosition(resolve, reject, {
        enableHighAccuracy: true,
        timeout: 15000,
        maximumAge: 0
      });
    });
  }

  function geolocationErrorMessage(error) {
    if (!error) return 'Unable to get your location.';
    if (error.code === 1) return 'Location access was denied. Allow location access and try again.';
    if (error.code === 2) return 'Your location could not be determined right now.';
    if (error.code === 3) return 'Location lookup timed out. Try again.';
    return error.message || 'Unable to get your location.';
  }

  function setStatus(root, message, level) {
    const el = root.querySelector('[data-nearby-status]');
    if (!el) return;
    el.className = 'alert mt-0 mb-0';
    el.classList.add(level === 'danger' ? 'alert-danger' : level === 'warning' ? 'alert-warning' : 'alert-info');
    el.textContent = message;
    el.classList.remove('d-none');
  }

  function clearStatus(root) {
    const el = root.querySelector('[data-nearby-status]');
    if (!el) return;
    el.className = 'alert mt-0 mb-0 d-none';
    el.textContent = '';
  }

  function hideNamePrompt(root, state) {
    const card = root.querySelector('[data-nearby-name-card]');
    if (card) {
      card.classList.add('d-none');
    }
    if (state) {
      state.locationId = null;
    }
  }

  function renderSuggestions(root, suggestions) {
    const list = root.querySelector('[data-nearby-suggestion-list]');
    if (!list) return;
    list.innerHTML = '';

    (suggestions || []).forEach(function (suggestion, index) {
      const id = 'sessionNearbySuggestion' + index;
      const wrapper = document.createElement('label');
      wrapper.className = 'check-in-suggestion-option';
      wrapper.setAttribute('for', id);
      wrapper.innerHTML = '' +
        '<input class="form-check-input mt-1" type="radio" name="nearbySelectedName" id="' + id + '">' +
        '<span class="check-in-suggestion-copy">' +
        '  <span class="check-in-suggestion-name"></span>' +
        '  <span class="check-in-suggestion-meta"></span>' +
        '</span>';
      wrapper.querySelector('input').value = suggestion.name || '';
      wrapper.querySelector('.check-in-suggestion-name').textContent = suggestion.name || '';
      wrapper.querySelector('.check-in-suggestion-meta').textContent =
        suggestion.usageCount === 1 ? 'Seen once here' : ('Seen ' + suggestion.usageCount + ' times here');
      list.appendChild(wrapper);
    });
  }

  function showNamePrompt(root, state, payload) {
    const card = root.querySelector('[data-nearby-name-card]');
    const message = root.querySelector('[data-nearby-prompt-message]');
    const customName = root.querySelector('[data-nearby-custom-name]');
    if (!card || !message || !customName) return;

    state.locationId = payload.locationId || null;
    renderSuggestions(root, payload.suggestions || []);
    message.textContent = payload.message || 'Confirm the location name below or enter your own.';
    customName.value = '';
    card.classList.remove('d-none');
    customName.focus();
  }

  function setBusy(root, isBusy, busyLabel) {
    const startButton = root.querySelector('[data-nearby-start]');
    const formButton = root.querySelector('[data-nearby-submit]');
    if (startButton) {
      if (!startButton.dataset.defaultLabel) {
        startButton.dataset.defaultLabel = startButton.textContent.trim();
      }
      startButton.disabled = isBusy;
      startButton.textContent = isBusy ? busyLabel : startButton.dataset.defaultLabel;
    }
    if (formButton) {
      formButton.disabled = isBusy;
    }
  }

  function clearResults(root) {
    const container = root.querySelector('[data-nearby-results]');
    const list = root.querySelector('[data-nearby-results-list]');
    const summary = root.querySelector('[data-nearby-results-summary]');
    if (list) {
      list.innerHTML = '';
    }
    if (summary) {
      summary.textContent = '';
    }
    if (container) {
      container.classList.add('d-none');
    }
  }

  function createCheckInFlow(root, onCheckedIn) {
    const state = {
      latitude: null,
      longitude: null,
      locationId: null
    };

    const form = root.querySelector('[data-nearby-name-form]');
    const cancel = root.querySelector('[data-nearby-cancel]');

    async function start() {
      hideNamePrompt(root, state);
      clearStatus(root);
      clearResults(root);

      if (!isSecureForGeolocation()) {
        setStatus(root, 'Location access requires HTTPS or localhost.', 'warning');
        return;
      }

      setBusy(root, true, 'Checking location...');
      try {
        const position = await getCurrentPosition();
        state.latitude = position.coords.latitude;
        state.longitude = position.coords.longitude;

        const payload = await postJson('/api/check-in/resolve', {
          latitude: state.latitude,
          longitude: state.longitude
        });

        if (payload.status === 'checked_in') {
          await onCheckedIn(root, payload);
          return;
        }

        if (payload.status === 'choose_name' || payload.status === 'name_required') {
          showNamePrompt(root, state, payload);
          return;
        }

        setStatus(root, 'Unexpected response while checking in.', 'danger');
      } catch (error) {
        const isGeoError = typeof error.code === 'number';
        setStatus(root, isGeoError ? geolocationErrorMessage(error) : (error.message || 'Unable to use location.'), 'danger');
      } finally {
        setBusy(root, false);
      }
    }

    async function submitNameSelection(event) {
      event.preventDefault();
      clearStatus(root);

      if (state.latitude == null || state.longitude == null) {
        setStatus(root, 'Location is no longer available. Try again.', 'warning');
        return;
      }

      const customName = root.querySelector('[data-nearby-custom-name]');
      const selected = root.querySelector('input[name="nearbySelectedName"]:checked');
      const selectedName = selected ? selected.value : '';
      const ownName = customName ? customName.value.trim() : '';

      if (!selectedName && !ownName) {
        setStatus(root, 'Choose a suggested name or enter your own.', 'warning');
        return;
      }

      setBusy(root, true, 'Finishing check-in...');
      try {
        const payload = await postJson('/api/check-in/complete', {
          latitude: state.latitude,
          longitude: state.longitude,
          locationId: state.locationId,
          selectedName: selectedName,
          customName: ownName
        });
        hideNamePrompt(root, state);
        await onCheckedIn(root, payload);
      } catch (error) {
        setStatus(root, error.message || 'Unable to finish your check-in.', 'danger');
      } finally {
        setBusy(root, false);
      }
    }

    if (form) {
      form.addEventListener('submit', submitNameSelection);
    }
    if (cancel) {
      cancel.addEventListener('click', function () {
        hideNamePrompt(root, state);
        clearStatus(root);
      });
    }

    return {
      start: start
    };
  }

  function renderNearbySessions(root, payload) {
    const container = root.querySelector('[data-nearby-results]');
    const list = root.querySelector('[data-nearby-results-list]');
    const summary = root.querySelector('[data-nearby-results-summary]');
    if (!container || !list || !summary) return;

    list.innerHTML = '';
    const sessions = payload && payload.sessions ? payload.sessions : [];
    const locationName = payload && payload.locationName ? payload.locationName : 'your current location';
    summary.textContent = sessions.length
      ? ('Sessions near ' + locationName)
      : ('No sessions are discoverable near ' + locationName + ' right now.');

    sessions.forEach(function (session) {
      const item = document.createElement('article');
      item.className = 'list-group-item text-start';
      item.innerHTML = '' +
        '<div class="d-flex flex-column gap-2">' +
        '  <div class="d-flex flex-wrap align-items-center justify-content-between gap-2">' +
        '    <div>' +
        '      <div class="fw-semibold" data-nearby-session-title></div>' +
        '      <div class="text-muted small" data-nearby-session-meta></div>' +
        '    </div>' +
        '    <button type="button" class="btn btn-action-primary btn-inline" data-nearby-session-join>Ask to Join</button>' +
        '  </div>' +
        '</div>';

      item.querySelector('[data-nearby-session-title]').textContent = session.sessionTitle || 'Session';
      item.querySelector('[data-nearby-session-meta]').textContent =
        (session.ownerDisplayName || 'Host')
        + ' | '
        + (session.locationName || locationName)
        + ' | '
        + (session.activeMemberCount === 1 ? '1 player' : ((session.activeMemberCount || 0) + ' players'));

      const joinButton = item.querySelector('[data-nearby-session-join]');
      joinButton.addEventListener('click', function () {
        joinButton.disabled = true;
        joinButton.textContent = 'Requesting...';
        postJson('/api/sessions/' + session.sessionId + '/nearby-sharing/join', {})
          .then(function (result) {
            if (result && result.redirectUrl) {
              window.location.assign(result.redirectUrl);
              return;
            }
            setStatus(root, result && result.message ? result.message : 'Join request sent.', 'info');
          })
          .catch(function (error) {
            setStatus(root, error.message || 'Unable to request that session.', 'danger');
            joinButton.disabled = false;
            joinButton.textContent = 'Ask to Join';
          });
      });

      list.appendChild(item);
    });

    container.classList.remove('d-none');
  }

  function initHostNearby(root) {
    const sessionId = root.getAttribute('data-session-id');
    if (!sessionId) return;

    const flow = createCheckInFlow(root, async function () {
      setStatus(root, 'Updating nearby sharing...', 'info');
      await postJson('/api/sessions/' + sessionId + '/nearby-sharing', {});
      window.location.reload();
    });

    const startButton = root.querySelector('[data-nearby-start]');
    if (startButton) {
      startButton.addEventListener('click', function () {
        flow.start();
      });
    }
  }

  function initJoinNearby(root) {
    const flow = createCheckInFlow(root, async function () {
      setStatus(root, 'Looking for nearby sessions...', 'info');
      const payload = await getJson('/api/sessions/nearby-sharing/candidates');
      renderNearbySessions(root, payload);
      if (payload.sessions && payload.sessions.length) {
        setStatus(root, 'Choose a nearby session below. The host will still approve your request.', 'info');
      } else {
        setStatus(root, 'No nearby sessions found. You can still use the shared code below.', 'warning');
      }
    });

    const startButton = root.querySelector('[data-nearby-start]');
    if (startButton) {
      startButton.addEventListener('click', function () {
        flow.start();
      });
    }
  }

  function init() {
    document.querySelectorAll('[data-session-nearby-host]').forEach(initHostNearby);
    document.querySelectorAll('[data-session-nearby-join]').forEach(initJoinNearby);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})(window, document);

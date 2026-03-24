(function (window, document) {
  'use strict';

  function csrfHeaders(baseHeaders) {
    return window.FHPB.Csrf.headers(baseHeaders);
  }

  function isSecureForGeolocation() {
    if (typeof window.isSecureContext === 'boolean') {
      return window.isSecureContext;
    }
    const host = (window.location && window.location.hostname) || '';
    return window.location.protocol === 'https:' || host === 'localhost' || host === '127.0.0.1';
  }

  function setStatus(message, level) {
    const el = document.getElementById('checkInStatus');
    if (!el) return;
    el.className = 'alert mt-3 mb-0';
    el.classList.add(level === 'danger' ? 'alert-danger' : level === 'warning' ? 'alert-warning' : 'alert-info');
    el.textContent = message;
    el.classList.remove('d-none');
  }

  function clearStatus() {
    const el = document.getElementById('checkInStatus');
    if (!el) return;
    el.className = 'alert mt-3 mb-0 d-none';
    el.textContent = '';
  }

  function defaultButtonLabel(button) {
    if (!button) return 'Check In Using My Location';
    return button.dataset.defaultLabel || 'Check In Using My Location';
  }

  function renderStartButtonLabel(button, label) {
    if (!button) return;
    const resolvedLabel = label || defaultButtonLabel(button);
    button.innerHTML = '<i class="bi bi-geo-alt-fill me-2" aria-hidden="true"></i><span>' + resolvedLabel + '</span>';
  }

  function setBusy(isBusy) {
    const button = document.getElementById('checkInStartButton');
    const formButton = document.querySelector('#checkInNameForm button[type="submit"]');
    if (button) {
      button.disabled = isBusy;
      renderStartButtonLabel(button, isBusy ? 'Checking location...' : defaultButtonLabel(button));
    }
    if (formButton) {
      formButton.disabled = isBusy;
    }
  }

  async function postJson(url, body) {
    const response = await fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: csrfHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(body)
    });

    let payload = null;
    try {
      payload = await response.json();
    } catch (err) {
      payload = null;
    }

    if (!response.ok) {
      const message = payload && payload.message ? payload.message : 'Request failed.';
      throw new Error(message);
    }

    return payload;
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

  function redirectWithToast(message) {
    const url = new URL(window.location.href);
    url.searchParams.delete('autostart');
    url.searchParams.set('toastMessage', message || 'Checked in.');
    window.location.assign(url.pathname + '?' + url.searchParams.toString());
  }

  function clearAutostartParam() {
    const url = new URL(window.location.href);
    if (!url.searchParams.has('autostart')) {
      return;
    }
    url.searchParams.delete('autostart');
    const nextUrl = url.pathname + (url.searchParams.toString() ? '?' + url.searchParams.toString() : '');
    window.history.replaceState({}, '', nextUrl);
  }

  const state = {
    latitude: null,
    longitude: null,
    locationId: null
  };

  function hideNamePrompt() {
    const card = document.getElementById('checkInNameCard');
    if (!card) return;
    card.classList.add('d-none');
    state.locationId = null;
  }

  function renderSuggestions(suggestions) {
    const list = document.getElementById('checkInSuggestionList');
    if (!list) return;
    list.innerHTML = '';

    (suggestions || []).forEach(function (suggestion, index) {
      const id = 'checkInSuggestion' + index;
      const wrapper = document.createElement('label');
      wrapper.className = 'check-in-suggestion-option';
      wrapper.setAttribute('for', id);
      wrapper.innerHTML = '' +
        '<input class="form-check-input mt-1" type="radio" name="selectedName" id="' + id + '">' +
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

  function showNamePrompt(payload) {
    const card = document.getElementById('checkInNameCard');
    const message = document.getElementById('checkInPromptMessage');
    const customName = document.getElementById('checkInCustomName');
    if (!card || !message || !customName) return;

    state.locationId = payload.locationId || null;
    renderSuggestions(payload.suggestions || []);
    message.textContent = payload.message || 'Confirm the location name below or enter your own.';
    customName.value = '';
    card.classList.remove('d-none');
    customName.focus();
  }

  async function startCheckIn() {
    hideNamePrompt();
    clearStatus();

    if (!isSecureForGeolocation()) {
      setStatus('Check-in location requires HTTPS or localhost.', 'warning');
      return;
    }

    setBusy(true);
    try {
      const position = await getCurrentPosition();
      state.latitude = position.coords.latitude;
      state.longitude = position.coords.longitude;

      const payload = await postJson('/api/check-in/resolve', {
        latitude: state.latitude,
        longitude: state.longitude
      });

      if (payload.status === 'checked_in') {
        redirectWithToast(payload.message);
        return;
      }

      if (payload.status === 'choose_name' || payload.status === 'name_required') {
        showNamePrompt(payload);
        return;
      }

      setStatus('Unexpected response while checking in.', 'danger');
    } catch (error) {
      const isGeoError = typeof error.code === 'number';
      setStatus(isGeoError ? geolocationErrorMessage(error) : (error.message || 'Unable to check in.'), 'danger');
    } finally {
      setBusy(false);
    }
  }

  async function submitNameSelection(event) {
    event.preventDefault();
    clearStatus();

    if (state.latitude == null || state.longitude == null) {
      setStatus('Location is no longer available. Start the check-in again.', 'warning');
      return;
    }

    const customName = document.getElementById('checkInCustomName');
    const selected = document.querySelector('input[name="selectedName"]:checked');
    const selectedName = selected ? selected.value : '';
    const ownName = customName ? customName.value.trim() : '';

    if (!selectedName && !ownName) {
      setStatus('Choose a suggested name or enter your own.', 'warning');
      return;
    }

    setBusy(true);
    try {
      const payload = await postJson('/api/check-in/complete', {
        latitude: state.latitude,
        longitude: state.longitude,
        locationId: state.locationId,
        selectedName: selectedName,
        customName: ownName
      });
      redirectWithToast(payload.message);
    } catch (error) {
      setStatus(error.message || 'Unable to finish your check-in.', 'danger');
    } finally {
      setBusy(false);
    }
  }

  function init() {
    const root = document.querySelector('.check-in-page');
    if (!root) return;
    const autoStart = root.dataset.autostart === 'true';

    const startButton = document.getElementById('checkInStartButton');
    const cancelButton = document.getElementById('checkInCancelButton');
    const form = document.getElementById('checkInNameForm');

    if (startButton) {
      startButton.addEventListener('click', startCheckIn);
    }
    if (cancelButton) {
      cancelButton.addEventListener('click', function () {
        hideNamePrompt();
        clearStatus();
      });
    }
    if (form) {
      form.addEventListener('submit', submitNameSelection);
    }

    clearAutostartParam();

    if (autoStart) {
      window.setTimeout(startCheckIn, 150);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})(window, document);

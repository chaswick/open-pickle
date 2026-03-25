/*
 * PWA + Push helper
 * - Registers service worker
 * - Shows "Install" prompt when supported
 * - Lets user enable Play Plan push notifications
 */

(function (window, document) {
  'use strict';

  window.FHPB = window.FHPB || {};

  const STORAGE_KEYS = {
    installBannerDismissedAt: 'fhpb_install_banner_dismissed_at',
    iosHintDismissedAt: 'fhpb_ios_a2hs_hint_dismissed_at',
    installHelperUnlocked: 'fhpb_install_helper_unlocked'
  };

  const DISMISS_FOREVER = 'forever';

  const INSTALL_BANNER_SNOOZE_DAYS = 14;
  const IOS_HINT_SNOOZE_DAYS = 2;
  const DEFAULT_PLAYPLANS_PUSH_HELP = 'Tip: Works best after installing the app to your home screen.';

  function brandingConfig() {
    return window.FHPB.Branding || {};
  }

  function appName() {
    const configuredName = brandingConfig().appName;
    return (typeof configuredName === 'string' && configuredName.trim()) ? configuredName.trim() : 'Open-Pickle';
  }

  function serviceWorkerPath() {
    const configuredPath = brandingConfig().serviceWorkerPath;
    return (typeof configuredPath === 'string' && configuredPath.trim()) ? configuredPath.trim() : '/sw.js';
  }

  function nowMs() {
    return Date.now();
  }

  function daysMs(days) {
    return days * 24 * 60 * 60 * 1000;
  }

  function csrfHeaders(baseHeaders) {
    return window.FHPB.Csrf.headers(baseHeaders);
  }

  function isIos() {
    const ua = window.navigator.userAgent || '';
    return /iPad|iPhone|iPod/.test(ua) && !window.MSStream;
  }

  function isStandalone() {
    if (typeof window.navigator.standalone !== 'undefined') {
      return !!window.navigator.standalone;
    }
    return window.matchMedia && window.matchMedia('(display-mode: standalone)').matches;
  }

  function isPushSupportedByBrowser() {
    return ('Notification' in window) && ('serviceWorker' in navigator) && ('PushManager' in window);
  }

  function isInstallHelperEligibleByAge() {
    const body = document.body;
    if (!body || !body.dataset) return false;
    return body.dataset.pwaInstallEligible === 'true';
  }

  function isHomePath() {
    const path = (window.location && window.location.pathname) || '';
    return path === '/home' || path === '/home/';
  }

  function unlockInstallHelperIfEligible() {
    if (!isInstallHelperEligibleByAge()) return;
    if (!isHomePath()) return;
    try {
      window.localStorage.setItem(STORAGE_KEYS.installHelperUnlocked, '1');
    } catch (e) {
      // ignore
    }
  }

  function hasUnlockedInstallHelper() {
    if (!isInstallHelperEligibleByAge()) return false;
    if (isHomePath()) return true;
    try {
      return window.localStorage.getItem(STORAGE_KEYS.installHelperUnlocked) === '1';
    } catch (e) {
      return false;
    }
  }

  function isSecureContextForPush() {
    if (typeof window.isSecureContext === 'boolean') {
      return window.isSecureContext;
    }
    const host = (window.location && window.location.hostname) || '';
    return window.location.protocol === 'https:' || host === 'localhost' || host === '127.0.0.1';
  }

  function getPushEligibility() {
    if (!isPushSupportedByBrowser()) {
      return {
        ok: false,
        reason: 'unsupported',
        message: 'This browser does not support web push notifications.'
      };
    }

    if (!isSecureContextForPush()) {
      return {
        ok: false,
        reason: 'insecure-context',
        message: 'Notifications require a secure HTTPS connection.'
      };
    }

    if (isIos() && !isStandalone()) {
      return {
        ok: false,
        reason: 'ios-not-standalone',
        message: 'On iPhone, install to Home Screen and open the app icon before enabling notifications.'
      };
    }

    return {
      ok: true,
      reason: null,
      message: null
    };
  }

  function getPushFailureMessage(error) {
    if (!error) return 'Unknown error';

    const name = (error.name || '').toLowerCase();
    const message = String(error.message || error).toLowerCase();

    if (name === 'notallowederror' || message.includes('permission denied') || message.includes('permission not granted')) {
      if (isIos()) {
        if (!isStandalone()) {
          return 'Permission denied. On iPhone, install to Home Screen and retry from the app icon.';
        }
        return 'Permission denied. Check iPhone Settings > Notifications and allow notifications for this app.';
      }
      return 'Permission denied. Check browser notification settings and try again.';
    }

    return error.message || String(error);
  }

  function setPlayPlansHelpText(message) {
    const help = document.getElementById('fhpb-playplan-push-help');
    if (!help) return;
    help.textContent = message || DEFAULT_PLAYPLANS_PUSH_HELP;
  }

  function showToast(html, options) {
    options = options || {};

    const containerId = 'fhpb-pwa-toast-container';
    let container = document.getElementById(containerId);
    if (!container) {
      container = document.createElement('div');
      container.id = containerId;
      container.className = 'toast-container position-fixed bottom-0 end-0 p-3';
      container.style.zIndex = '1080';
      document.body.appendChild(container);
    }

    const wrapper = document.createElement('div');
    wrapper.innerHTML = html;
    const toastEl = wrapper.firstElementChild;
    container.appendChild(toastEl);

    try {
      const toast = new bootstrap.Toast(toastEl, {
        autohide: options.autohide !== false,
        delay: options.delay || 8000
      });
      toast.show();
      return { toastEl, toast };
    } catch (e) {
      return { toastEl };
    }
  }

  async function registerServiceWorker() {
    if (!('serviceWorker' in navigator)) return null;

    const isLocalhost = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
    const isSecure = window.location.protocol === 'https:' || isLocalhost;
    if (!isSecure) return null;

    try {
      return await navigator.serviceWorker.register(serviceWorkerPath(), { scope: '/' });
    } catch (e) {
      console.warn('[pwa] service worker registration failed', e);
      return null;
    }
  }

  function shouldShowDismissableBanner(storageKey, snoozeDays) {
    const raw = window.localStorage.getItem(storageKey);
    if (!raw) return true;
    if (raw === DISMISS_FOREVER) return false;
    const ts = parseInt(raw, 10);
    if (!ts || isNaN(ts)) return true;
    const days = (typeof snoozeDays === 'number' && snoozeDays > 0) ? snoozeDays : 14;
    return (nowMs() - ts) > daysMs(days);
  }

  function markDismissed(storageKey, forever) {
    try {
      window.localStorage.setItem(storageKey, forever ? DISMISS_FOREVER : String(nowMs()));
    } catch (e) {
      // ignore
    }
  }

  function wireInstallPrompt() {
    let deferredPrompt = null;
    let installToastShown = false;
    let iosHintShown = false;

    function shouldInterceptInstallPrompt() {
      if (isStandalone()) return false;
      if (!hasUnlockedInstallHelper()) return false;
      return shouldShowDismissableBanner(STORAGE_KEYS.installBannerDismissedAt, INSTALL_BANNER_SNOOZE_DAYS);
    }

    function maybeShowInstallBanner() {
      if (!deferredPrompt) return;
      if (installToastShown) return;
      if (!hasUnlockedInstallHelper()) return;
      if (isStandalone()) return;
      if (!shouldShowDismissableBanner(STORAGE_KEYS.installBannerDismissedAt, INSTALL_BANNER_SNOOZE_DAYS)) return;

      installToastShown = true;

      const toastHtml = '' +
        '<div class="toast" role="alert" aria-live="assertive" aria-atomic="true">' +
        '  <div class="toast-header">' +
        '    <strong class="me-auto">' + appName() + '</strong>' +
        '    <button type="button" class="btn-close" data-bs-dismiss="toast" aria-label="Close"></button>' +
        '  </div>' +
        '  <div class="toast-body">' +
        '    Install the ' + appName() + ' app for faster access.' +
        '    <div class="mt-2 pt-2 border-top d-flex gap-2">' +
        '      <button type="button" class="btn btn-primary btn-sm" id="fhpb-install-btn">Install</button>' +
        '      <button type="button" class="btn btn-outline-secondary btn-sm" id="fhpb-install-dismiss">Not now</button>' +
        '    </div>' +
        '  </div>' +
        '</div>';

      const shown = showToast(toastHtml, { delay: 12000 });
      const installBtn = shown.toastEl.querySelector('#fhpb-install-btn');
      const dismissBtn = shown.toastEl.querySelector('#fhpb-install-dismiss');

      if (dismissBtn) {
        dismissBtn.addEventListener('click', () => {
          markDismissed(STORAGE_KEYS.installBannerDismissedAt, false);
          if (shown.toast && typeof shown.toast.hide === 'function') {
            shown.toast.hide();
          } else if (shown.toastEl && shown.toastEl.parentNode) {
            shown.toastEl.parentNode.removeChild(shown.toastEl);
          }
        });
      }

      if (installBtn) {
        installBtn.addEventListener('click', async () => {
          if (!deferredPrompt) return;
          try {
            deferredPrompt.prompt();
            await deferredPrompt.userChoice;
          } catch (err) {
            console.warn('[pwa] install prompt failed', err);
          } finally {
            deferredPrompt = null;
            markDismissed(STORAGE_KEYS.installBannerDismissedAt, false);
          }
        });
      }
    }

    function maybeShowIosHint() {
      if (iosHintShown) return;
      if (!hasUnlockedInstallHelper()) return;
      if (!isIos() || isStandalone()) return;
      if (!shouldShowDismissableBanner(STORAGE_KEYS.iosHintDismissedAt, IOS_HINT_SNOOZE_DAYS)) return;

      iosHintShown = true;

      const toastHtml = '' +
        '<div class="toast" role="alert" aria-live="assertive" aria-atomic="true">' +
        '  <div class="toast-header">' +
        '    <strong class="me-auto">Install ' + appName() + '</strong>' +
        '    <button type="button" class="btn-close" data-bs-dismiss="toast" aria-label="Close"></button>' +
        '  </div>' +
        '  <div class="toast-body">' +
        '    On iPhone: tap <strong>Share</strong> -> <strong>Add to Home Screen</strong>.' +
        '    <div class="mt-2 pt-2 border-top">' +
        '      <button type="button" class="btn btn-light btn-sm" id="fhpb-ios-dismiss">Got it</button>' +
        '    </div>' +
        '  </div>' +
        '</div>';

      const shown = showToast(toastHtml, { delay: 15000 });
      const dismiss = shown.toastEl.querySelector('#fhpb-ios-dismiss');
      if (dismiss) {
        dismiss.addEventListener('click', () => {
          markDismissed(STORAGE_KEYS.iosHintDismissedAt, false);
          if (shown.toast && typeof shown.toast.hide === 'function') {
            shown.toast.hide();
          } else if (shown.toastEl && shown.toastEl.parentNode) {
            shown.toastEl.parentNode.removeChild(shown.toastEl);
          }
        });
      }
    }

    window.addEventListener('beforeinstallprompt', (e) => {
      if (!shouldInterceptInstallPrompt()) {
        deferredPrompt = null;
        return;
      }
      e.preventDefault();
      deferredPrompt = e;
      maybeShowInstallBanner();
    });

    maybeShowIosHint();
  }

  function urlBase64ToUint8Array(base64String) {
    const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
    const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
    const rawData = window.atob(base64);
    const outputArray = new Uint8Array(rawData.length);
    for (let i = 0; i < rawData.length; ++i) {
      outputArray[i] = rawData.charCodeAt(i);
    }
    return outputArray;
  }

  async function getVapidPublicKey() {
    const res = await fetch('/api/push/vapid-public-key', { credentials: 'same-origin' });
    if (!res.ok) {
      return null;
    }
    const json = await res.json();
    return json && json.publicKey ? json.publicKey : null;
  }

  async function ensurePushSubscribed() {
    const eligibility = getPushEligibility();
    if (!eligibility.ok) {
      throw new Error(eligibility.message);
    }

    const reg = await registerServiceWorker();
    if (!reg || !('PushManager' in window)) {
      throw new Error('Push not supported');
    }

    if (Notification.permission === 'denied') {
      throw new Error('Notifications blocked');
    }

    let sub = await reg.pushManager.getSubscription();
    if (!sub) {
      const publicKey = await getVapidPublicKey();
      if (!publicKey) {
        throw new Error('Push not configured');
      }

      const requestPermission = await Notification.requestPermission();
      if (requestPermission !== 'granted') {
        throw new Error('Permission not granted');
      }

      sub = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(publicKey)
      });
    }

    const json = sub.toJSON();
    const payload = {
      endpoint: json.endpoint,
      p256dh: json.keys && json.keys.p256dh,
      auth: json.keys && json.keys.auth,
      userAgent: window.navigator.userAgent || ''
    };

    const headers = csrfHeaders({ 'Content-Type': 'application/json' });

    const resp = await fetch('/api/push/subscribe', {
      method: 'POST',
      credentials: 'same-origin',
      headers,
      body: JSON.stringify(payload)
    });

    if (!resp.ok) {
      throw new Error('Subscribe API failed');
    }

    return true;
  }

  async function ensurePushUnsubscribed() {
    const reg = await registerServiceWorker();
    if (!reg || !('PushManager' in window)) {
      throw new Error('Push not supported');
    }

    const sub = await reg.pushManager.getSubscription();
    if (!sub) {
      return true;
    }

    const json = sub.toJSON();
    const endpoint = json && json.endpoint ? json.endpoint : null;
    if (!endpoint) {
      // Still attempt to unsubscribe locally, even if endpoint is missing.
      await sub.unsubscribe();
      return true;
    }

    const headers = csrfHeaders({ 'Content-Type': 'application/json' });
    const resp = await fetch('/api/push/unsubscribe', {
      method: 'POST',
      credentials: 'same-origin',
      headers,
      body: JSON.stringify({ endpoint })
    });

    if (!resp.ok) {
      throw new Error('Unsubscribe API failed');
    }

    await sub.unsubscribe();
    return true;
  }

  async function getExistingPushSubscription() {
    try {
      const reg = await registerServiceWorker();
      if (!reg || !('PushManager' in window)) {
        return null;
      }
      return await reg.pushManager.getSubscription();
    } catch (e) {
      return null;
    }
  }

  function setPlayPlansButtonState(btn, state) {
    if (!btn) return;

    if (state === 'unavailable') {
      btn.disabled = true;
      btn.classList.remove('btn-primary', 'btn-success', 'btn-danger', 'btn-outline-danger');
      btn.classList.add('btn-secondary');
      btn.textContent = 'Notifications unavailable';
      return;
    }

    if (state === 'blocked') {
      btn.disabled = true;
      btn.classList.remove('btn-primary', 'btn-success', 'btn-danger', 'btn-outline-danger');
      btn.classList.add('btn-secondary');
      btn.textContent = 'Notifications blocked';
      return;
    }

    if (state === 'enabled') {
      btn.disabled = false;
      btn.classList.remove('btn-primary', 'btn-secondary', 'btn-success');
      btn.classList.add('btn-outline-danger');
      btn.textContent = 'Disable notifications';
      return;
    }

    // default: disabled
    btn.disabled = false;
    btn.classList.remove('btn-secondary', 'btn-success', 'btn-danger', 'btn-outline-danger');
    btn.classList.add('btn-primary');
    btn.textContent = 'Enable notifications';
  }

  async function syncPlayPlansEnableButtonState() {
    const btn = document.getElementById('fhpb-enable-playplan-push');
    if (!btn) return;

    const eligibility = getPushEligibility();
    if (!eligibility.ok) {
      setPlayPlansButtonState(btn, 'unavailable');
      setPlayPlansHelpText(eligibility.message);
      return;
    }

    // If the user has explicitly blocked notifications, reflect that.
    if (Notification.permission === 'denied') {
      setPlayPlansButtonState(btn, 'blocked');
      if (isIos()) {
        setPlayPlansHelpText('Notifications are blocked. Open iPhone Settings > Notifications and allow notifications.');
      } else {
        setPlayPlansHelpText('Notifications are blocked. Update this browser\'s notification permission to re-enable.');
      }
      return;
    }

    setPlayPlansHelpText(null);

    // If we're already subscribed in this browser, keep the UI consistent across refreshes.
    const existingSub = await getExistingPushSubscription();
    if (existingSub) {
      setPlayPlansButtonState(btn, 'enabled');
    } else {
      setPlayPlansButtonState(btn, 'disabled');
    }
  }

  function maybeWirePlayPlansEnableButton() {
    const btn = document.getElementById('fhpb-enable-playplan-push');
    if (!btn) return;

    btn.addEventListener('click', async () => {
      const eligibility = getPushEligibility();
      if (!eligibility.ok) {
        await syncPlayPlansEnableButtonState();
        alert(eligibility.message);
        return;
      }

      btn.disabled = true;
      const existingSub = await getExistingPushSubscription();
      const isEnabled = !!existingSub;
      btn.textContent = isEnabled ? 'Disabling...' : 'Enabling...';
      try {
        if (isEnabled) {
          await ensurePushUnsubscribed();
          setPlayPlansButtonState(btn, 'disabled');
        } else {
          await ensurePushSubscribed();
          setPlayPlansButtonState(btn, 'enabled');
        }
      } catch (e) {
        console.warn('[push] enable failed', e);
        btn.disabled = false;
        // Re-sync from actual browser state, then show error.
        await syncPlayPlansEnableButtonState();
        alert('Could not update notifications: ' + getPushFailureMessage(e));
      }
    });
  }

  FHPB.PWA = {
    registerServiceWorker,
    ensurePushSubscribed,
    ensurePushUnsubscribed
  };

  FHPB.PWA.init = function () {
    unlockInstallHelperIfEligible();
    registerServiceWorker();
    wireInstallPrompt();
    maybeWirePlayPlansEnableButton();
    syncPlayPlansEnableButtonState();
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', FHPB.PWA.init);
  } else {
    FHPB.PWA.init();
  }

})(window, document);

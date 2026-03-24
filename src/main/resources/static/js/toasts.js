// Shared toast helpers
(function () {
  'use strict';

  const STANDARD_TOAST_DELAY_MS = 2000;

  window.FHPB = window.FHPB || {};
  if (!Number.isFinite(window.FHPB.standardToastDelayMs) || window.FHPB.standardToastDelayMs <= 0) {
    window.FHPB.standardToastDelayMs = STANDARD_TOAST_DELAY_MS;
  }

  function _getToastContainer() {
    return document.querySelector('#globalToastContainer') || document.querySelector('.toast-container-mid');
  }

  function _ensureToastContainer() {
    let c = _getToastContainer();
    if (!c) {
      c = document.createElement('div');
      c.className = 'toast-container toast-container-mid toast-overlay';
      document.body.appendChild(c);
    }
    return c;
  }

  function _createToastElement(message, type) {
    const toast = document.createElement('div');
    const level = type || 'light';
    toast.className = `toast align-items-center border-0 text-bg-${level}`;
    toast.setAttribute('role', 'alert');
    toast.setAttribute('aria-live', 'assertive');
    toast.setAttribute('aria-atomic', 'true');
    const closeClass = level !== 'light' ? 'btn-close-white' : '';
    toast.innerHTML = `<div class='d-flex'><div class='toast-body'>${message}</div><button type='button' class='btn-close me-2 m-auto ${closeClass}' data-bs-dismiss='toast' aria-label='Close'></button></div>`;
    return toast;
  }

  function _standardToastDelay() {
    const configured = window.FHPB && window.FHPB.standardToastDelayMs;
    return Number.isFinite(configured) && configured > 0 ? configured : STANDARD_TOAST_DELAY_MS;
  }

  function _presentToast(toast, delayMs) {
    const resolvedDelay = Number.isFinite(delayMs) && delayMs > 0 ? delayMs : _standardToastDelay();
    toast.addEventListener('hidden.bs.toast', () => toast.remove(), { once: true });
    new bootstrap.Toast(toast, { delay: resolvedDelay }).show();
    window.setTimeout(() => toast.remove(), resolvedDelay + 500);
  }

  function resetAlerts() {
    const container = _getToastContainer();
    if (!container) return;
    container.querySelectorAll('.toast').forEach(t => {
      const inst = bootstrap.Toast.getInstance(t);
      if (inst) inst.hide();
      t.remove();
    });
  }

  function showToast(message, level) {
    const container = _ensureToastContainer();
    const toast = _createToastElement(message, level || 'light');
    container.appendChild(toast);
    _presentToast(toast);
  }

  function showError(message) {
    showToast(message, 'danger');
  }

  function showInfo(message) {
    const container = _ensureToastContainer();
    const toast = _createToastElement(message, 'info');
    container.appendChild(toast);
    _presentToast(toast);
  }

  // Expose helpers globally
  window._getToastContainer = _getToastContainer;
  window._ensureToastContainer = _ensureToastContainer;
  window._standardToastDelay = _standardToastDelay;
  window.showToast = showToast;
  window.showError = showError;
  window.showInfo = showInfo;
  window.resetAlerts = resetAlerts;

})();

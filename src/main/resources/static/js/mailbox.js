(function () {
  'use strict';
  window.FHPB = window.FHPB || {};
  var inFlight = null;
  var refreshAgain = false;

  function applyFreshContent(doc) {
    var bar = document.getElementById('playerMailbox');
    var freshBar = doc.getElementById('playerMailbox');
    if (!bar || !freshBar) return false;
    ['inbox', 'outbox'].forEach(function (box) {
      var selector = '[data-mailbox-button="' + box + '"]';
      var button = bar.querySelector(selector);
      var freshButton = freshBar.querySelector(selector);
      if (!button || !freshButton) return;
      button.setAttribute('aria-label', freshButton.getAttribute('aria-label'));
      var badge = button.querySelector('[data-mailbox-count]');
      var freshBadge = freshButton.querySelector('[data-mailbox-count]');
      if (badge && freshBadge) {
        badge.textContent = freshBadge.textContent;
        badge.className = freshBadge.className;
      }
    });
    ['playerInboxBody', 'playerOutboxBody'].forEach(function (id) {
      var current = document.getElementById(id);
      var fresh = doc.getElementById(id);
      if (!current || !fresh || current.querySelector('form button:disabled')) return;
      var scrollTop = current.scrollTop;
      current.innerHTML = fresh.innerHTML;
      current.scrollTop = scrollTop;
    });
    if (FHPB.DateTime && typeof FHPB.DateTime.initializeLocalTimes === 'function') {
      FHPB.DateTime.initializeLocalTimes();
    }
    return true;
  }

  function refresh() {
    var bar = document.getElementById('playerMailbox');
    if (!bar) return Promise.resolve(false);
    if (inFlight) {
      refreshAgain = true;
      return inFlight;
    }
    inFlight = fetch(bar.getAttribute('data-mailbox-url'), {
      credentials: 'same-origin',
      cache: 'no-store',
      headers: { 'X-Requested-With': 'XMLHttpRequest' }
    }).then(function (response) {
      if (!response.ok) throw new Error('Mailbox unavailable');
      return response.text();
    }).then(function (html) {
      return applyFreshContent(new DOMParser().parseFromString(html, 'text/html'));
    }).catch(function () {
      // Preserve the last working controls if a refresh fails.
      return false;
    }).finally(function () {
      inFlight = null;
      if (refreshAgain) {
        refreshAgain = false;
        refresh();
      }
    });
    return inFlight;
  }

  FHPB.Mailbox = { refresh: refresh };
  document.addEventListener('DOMContentLoaded', function () {
    if (!document.getElementById('playerMailbox')) return;
    window.setInterval(function () {
      if (!document.hidden) refresh();
    }, 60000);
    window.addEventListener('focus', refresh);
    document.addEventListener('shown.bs.modal', function (event) {
      if (event.target.matches('[data-mailbox-modal]')) refresh();
    });
  });
})();

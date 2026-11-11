(function (window, document) {
  'use strict';

  var widget = document.querySelector('[data-translation-widget]');
  if (!widget) return;

  // Identity text stays as entered. Observe inserted fragments as well as the initial page:
  // confirmations, live matchups, score dialogs, and player pickers all add names dynamically.
  var nameSelectors = [
    '[data-translation-name]', '.player-link', '.nav-user-link', '.player-name-label',
    '.session-player-id', '.identity-code-value', '.competition-public-code',
    'a[href^="/account?member="]', '.site-footer-brand', '.navbar-brand',
    '.matchups-player-chips li', '.matchups-team > span', '.matchups-byes > span',
    '.matchups-result-players > li > span',
    '.matchups-roster > li > div > strong', '[data-score-team-a]', '[data-score-team-b]',
    '[data-pair-list] option:not([value=""])', '[name=replacementId] option:not([value=""])',
    '[data-match-row] tbody tr:not(.match-row-divider) > td:first-child > div',
    '.match-row-context-value', '.log-match-large select option[value]:not([value=""])',
    '.player-picker-option[data-picker-value]:not([data-picker-value=""])',
    '.player-picker-trigger:not(.player-picker-trigger-empty)'
  ].join(',');

  function protectName(element) {
    element.setAttribute('translate', 'no');
    element.classList.add('notranslate');
  }

  function protectNames(root) {
    if (root.nodeType !== 1) return;
    if (root.matches(nameSelectors)) protectName(root);
    root.querySelectorAll(nameSelectors).forEach(protectName);
  }

  protectNames(document.body);
  new MutationObserver(function (records) {
    records.forEach(function (record) {
      record.addedNodes.forEach(protectNames);
    });
  }).observe(document.body, { childList: true, subtree: true });

  window.gtranslateSettings = {
    default_language: 'en',
    languages: ['en', 'es', 'zh-CN', 'zh-TW', 'ja', 'de', 'vi', 'fr'],
    wrapper_selector: '[data-translation-widget]',
    native_language_names: true,
    select_language_label: 'Language'
  };

  // Both controls share their final styling from the first paint. Keep the English
  // placeholder until the provider's control is present, including on slow connections.
  var ready = new MutationObserver(function () {
    var select = widget.querySelector('.gt_selector');
    if (!select) return;
    select.id = 'app-language-select';
    var emptyOption = select.querySelector('option[value=""]');
    if (emptyOption) emptyOption.remove();
    widget.querySelector('[data-translation-placeholder]').remove();
    ready.disconnect();
  });
  ready.observe(widget, { childList: true });

  var script = document.createElement('script');
  script.src = 'https://cdn.gtranslate.net/widgets/latest/dropdown.js';
  script.async = true;
  script.onerror = function () {
    var status = document.querySelector('[data-translation-status]');
    status.textContent = 'Translation is unavailable. You can still use the site in English.';
    status.hidden = false;
    ready.disconnect();
  };
  document.body.appendChild(script);
})(window, document);

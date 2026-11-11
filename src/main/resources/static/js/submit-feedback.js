(function (window, document) {
  'use strict';
  window.FHPB = window.FHPB || {};
  var pending = new Map();
  var submitSelector = 'button:not([type]), button[type="submit"], input[type="submit"]';
  var status = document.createElement('div');
  status.className = 'visually-hidden'; status.setAttribute('role', 'status');
  document.body.appendChild(status);

  function applies(form, button) {
    var target = button && button.getAttribute('formtarget') || form.target;
    return form.dataset.submitFeedback !== 'off' && (!target || target === '_self')
      && form.method.toLowerCase() !== 'dialog';
  }

  function reset(form) {
    var state = pending.get(form);
    if (!state) return;
    state.controls.forEach(function (saved) {
      saved.element.disabled = saved.disabled;
      if (saved.html != null) saved.element.innerHTML = saved.html;
      if (saved.value != null) saved.element.value = saved.value;
      ['style', 'aria-label', 'aria-busy'].forEach(function (name) {
        if (saved.attributes[name] == null) saved.element.removeAttribute(name);
        else saved.element.setAttribute(name, saved.attributes[name]);
      });
      saved.element.removeAttribute('data-submit-pending');
    });
    if (state.hidden) state.hidden.remove();
    if (state.busy == null) form.removeAttribute('aria-busy');
    else form.setAttribute('aria-busy', state.busy);
    form.removeAttribute('data-submit-pending');
    pending.delete(form);
  }

  function begin(form, button) {
    if (pending.has(form)) return false;
    button = button || form.querySelector(submitSelector);
    if (!applies(form, button)) return true;
    var controls = Array.from(form.elements).filter(function (element) {
      return element.tagName === 'BUTTON' || element.matches('input[type="submit"]');
    });
    if (button && !controls.includes(button)) controls.push(button);
    var modal = button && button.closest('.modal') || form.closest('.modal');
    if (modal) modal.querySelectorAll('[data-bs-dismiss="modal"]').forEach(function (close) {
      if (!controls.includes(close)) controls.push(close);
    });
    var state = { busy: form.getAttribute('aria-busy'), controls: [], hidden: null, modal: modal };
    // A disabled submit button is omitted from native form data. Keep its name/value.
    if (button && button.matches(submitSelector) && button.name && !button.disabled) {
      state.hidden = document.createElement('input');
      state.hidden.type = 'hidden'; state.hidden.name = button.name; state.hidden.value = button.value;
      form.appendChild(state.hidden);
    }
    controls.forEach(function (element) {
      var attributes = {};
      ['style', 'aria-label', 'aria-busy'].forEach(function (name) { attributes[name] = element.getAttribute(name); });
      state.controls.push({ element: element, disabled: element.disabled, attributes: attributes,
        html: element === button && element.tagName === 'BUTTON' ? element.innerHTML : null,
        value: element === button && element.tagName === 'INPUT' ? element.value : null });
    });
    pending.set(form, state);
    form.dataset.submitPending = 'true'; form.setAttribute('aria-busy', 'true');
    if (button) {
      var style = window.getComputedStyle(button), width = button.getBoundingClientRect().width;
      button.style.setProperty('--submit-background', style.backgroundColor);
      button.style.setProperty('--submit-background-image', button.classList.contains('btn-close') ? 'none' : style.backgroundImage);
      button.style.setProperty('--submit-color', style.color);
      button.style.setProperty('--submit-border', style.borderColor);
      if (width) button.style.minWidth = 'min(' + width + 'px, 100%)';
      var label = button.dataset.submitLabel || form.dataset.submitLabel || 'Please wait…';
      status.textContent = label;
      var iconOnly = button.tagName === 'BUTTON' && !button.textContent.trim();
      if (button.tagName === 'INPUT') button.value = label;
      else {
        var spinner = document.createElement('span'); spinner.className = 'spinner-border spinner-border-sm'; spinner.setAttribute('aria-hidden', 'true');
        var text = document.createElement('span'); text.textContent = label;
        if (iconOnly) text.className = 'visually-hidden';
        button.replaceChildren(spinner, text);
      }
      button.dataset.submitPending = 'true'; button.setAttribute('aria-busy', 'true'); button.setAttribute('aria-label', label);
    }
    state.controls.forEach(function (saved) { saved.element.disabled = true; });
    return true;
  }

  window.FHPB.SubmitFeedback = {
    begin: begin,
    reset: reset,
    submit: function (form, button) {
      if (!begin(form, button)) return false;
      try { window.HTMLFormElement.prototype.submit.call(form); }
      catch (error) { reset(form); throw error; }
      return true;
    }
  };
  // Run after form validation and handlers that take over submission with fetch.
  document.addEventListener('submit', function (event) {
    var form = event.target;
    if (!(form instanceof window.HTMLFormElement)) return;
    if (pending.has(form)) { event.preventDefault(); return; }
    if (!event.defaultPrevented && !begin(form, event.submitter)) event.preventDefault();
  });
  document.addEventListener('hide.bs.modal', function (event) {
    pending.forEach(function (state) { if (state.modal === event.target) event.preventDefault(); });
  });
  window.addEventListener('pageshow', function (event) {
    if (!event.persisted) return;
    Array.from(pending.keys()).forEach(reset); status.textContent = '';
  });
})(window, document);

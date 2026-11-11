import fs from 'node:fs';
import { JSDOM } from 'jsdom';
import { afterEach, describe, expect, it, vi } from 'vitest';

const source = fs.readFileSync('src/main/resources/static/js/submit-feedback.js', 'utf8');
let dom;
function page(markup = '') {
  dom = new JSDOM(`<!doctype html><body>${markup || `<form method="post" data-submit-label="Saving…">
    <input name="scoreA" value="7" required><input name="scoreB" value="6" required>
    <button type="submit" name="intent" value="save">Save score</button>
    <button type="button" disabled>Unavailable</button></form>`}</body>`,
  { runScripts: 'outside-only', url: 'http://localhost:8090' });
  dom.window.eval(source);
  return { window: dom.window, form: dom.window.document.querySelector('form'), button: dom.window.document.querySelector('button') };
}
function submit(window, form, button) {
  const event = new window.SubmitEvent('submit', { bubbles: true, cancelable: true, submitter: button });
  form.dispatchEvent(event);
  return event;
}
afterEach(() => dom?.window.close());

describe('submission feedback', () => {
  it('acknowledges the first tap, preserves submitted values, and blocks repeated submissions', () => {
    const { window, form, button } = page();
    expect(submit(window, form, button).defaultPrevented).toBe(false);
    expect(button.disabled).toBe(true);
    expect(button.textContent).toBe('Saving…');
    expect(form.getAttribute('aria-busy')).toBe('true');
    expect(button.querySelector('.spinner-border')).not.toBeNull();
    expect(new window.FormData(form).get('scoreA')).toBe('7');
    expect(new window.FormData(form).getAll('intent')).toEqual(['save']);
    expect(form.elements.scoreA.disabled).toBe(false);
    expect(submit(window, form, button).defaultPrevented).toBe(true);
  });

  it('leaves forms usable when validation or an AJAX handler cancels native submission', () => {
    const { window, form, button } = page();
    form.addEventListener('submit', event => event.preventDefault());
    submit(window, form, button);
    expect(button.disabled).toBe(false);
    expect(button.textContent).toBe('Save score');
    expect(form.hasAttribute('aria-busy')).toBe(false);
  });

  it('restores the original controls and values when returning through browser history', () => {
    const { window, form, button } = page();
    button.setAttribute('style', 'min-width: 80px'); button.setAttribute('aria-label', 'Save these scores');
    submit(window, form, button);
    window.dispatchEvent(new window.PageTransitionEvent('pageshow', { persisted: true }));
    expect(button.disabled).toBe(false);
    expect(button.textContent).toBe('Save score');
    expect(button.getAttribute('style')).toBe('min-width: 80px');
    expect(button.getAttribute('aria-label')).toBe('Save these scores');
    expect(form.querySelector('button[type=button]').disabled).toBe(true);
    expect(form.querySelector('input[type=hidden]')).toBeNull();
    expect(form.hasAttribute('aria-busy')).toBe(false);
    expect(submit(window, form, button).defaultPrevented).toBe(false);
  });

  it('keeps an in-flight request locked if the initial page finishes loading after the tap', () => {
    const { window, form, button } = page();
    submit(window, form, button);
    window.dispatchEvent(new window.PageTransitionEvent('pageshow', { persisted: false }));
    expect(button.disabled).toBe(true);
    expect(submit(window, form, button).defaultPrevented).toBe(true);
  });

  it('guards programmatic submissions and unlocks the form if submission throws', () => {
    const { window, form, button } = page();
    const native = vi.spyOn(window.HTMLFormElement.prototype, 'submit').mockImplementation(() => {});
    expect(window.FHPB.SubmitFeedback.submit(form, button)).toBe(true);
    expect(window.FHPB.SubmitFeedback.submit(form, button)).toBe(false);
    expect(native).toHaveBeenCalledOnce();
    window.FHPB.SubmitFeedback.reset(form);
    native.mockImplementation(() => { throw new Error('Submission failed'); });
    expect(() => window.FHPB.SubmitFeedback.submit(form, button)).toThrow('Submission failed');
    expect(button.disabled).toBe(false);
    expect(button.textContent).toBe('Save score');
  });

  it.each(['data-submit-feedback="off"', 'target="_blank"'])('does not leave a non-navigating form locked (%s)', attributes => {
    const { window, form, button } = page(`<form method="post" ${attributes}><button>Download</button></form>`);
    submit(window, form, button);
    expect(button.disabled).toBe(false);
    expect(form.hasAttribute('aria-busy')).toBe(false);
  });

  it('keeps a score modal in place while saving, then restores dismissal after reset', () => {
    const { window, form, button } = page(`<div class="modal"><button type="button" data-bs-dismiss="modal">Close</button>
      <form method="post"><input name="scoreA" value="7"><button type="submit">Save score</button></form></div>`);
    const save = form.querySelector('button');
    submit(window, form, save);
    const modal = form.closest('.modal');
    expect(button.disabled).toBe(true);
    expect(modal.dispatchEvent(new window.Event('hide.bs.modal', { bubbles: true, cancelable: true }))).toBe(false);
    window.FHPB.SubmitFeedback.reset(form);
    expect(button.disabled).toBe(false);
    expect(modal.dispatchEvent(new window.Event('hide.bs.modal', { bubbles: true, cancelable: true }))).toBe(true);
  });

  it('waits for confirmation before locking and sends an approved action only once', () => {
    const { window, form, button } = page();
    const native = vi.spyOn(window.HTMLFormElement.prototype, 'submit').mockImplementation(() => {});
    window.matchMedia = () => ({ matches: true });
    const modal = { show() {}, hide() {} };
    window.bootstrap = { Modal: class { static getOrCreateInstance() { return modal; } } };
    window.eval(fs.readFileSync('src/main/resources/static/js/components.js', 'utf8'));
    expect(window.handleConfirmForm(form, 'End this session?')).toBe(false);
    expect(button.disabled).toBe(false);
    expect(native).not.toHaveBeenCalled();
    const yes = window.document.querySelector('.fhpb-confirm-yes');
    yes.click(); yes.click();
    expect(button.disabled).toBe(true);
    expect(native).toHaveBeenCalledOnce();
  });
});

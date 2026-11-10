import fs from 'node:fs';
import { JSDOM } from 'jsdom';
import { afterEach, describe, expect, it, vi } from 'vitest';

const source = fs.readFileSync('src/main/resources/static/js/components.js', 'utf8');
let dom;

function page({ translation = true, mobile = false } = {}) {
  dom = new JSDOM(`<!doctype html><body>${translation ? '<div data-translation-widget></div>' : ''}
    <form><button type="button">Act</button></form>
    <div id="fhpbConfirmModal"><h5 class="modal-title">Confirm</h5>
      <p class="fhpb-confirm-message"></p><button class="fhpb-confirm-yes">Confirm</button></div>
    </body>`, { runScripts: 'outside-only', url: 'http://localhost:8090' });
  const window = dom.window;
  window.confirm = vi.fn(() => true);
  window.matchMedia = () => ({ matches: mobile });
  const modal = { show: vi.fn(), hide: vi.fn() };
  window.bootstrap = { Modal: class { static getOrCreateInstance() { return modal; } } };
  window.eval(source);
  const form = window.document.querySelector('form');
  form.submit = vi.fn();
  return { window, form, modal, yes: () => window.document.querySelector('.fhpb-confirm-yes') };
}

afterEach(() => dom?.window.close());

describe('translatable confirmation dialogs', () => {
  it('keeps a desktop session-ending prompt in the page and submits only once after approval', () => {
    const { window, form, modal, yes } = page();
    const message = 'End this session and any remaining matchups for everyone? Recorded scores will be saved.';
    expect(window.handleConfirmForm(form, message)).toBe(false);
    expect(window.confirm).not.toHaveBeenCalled();
    expect(window.document.querySelector('.fhpb-confirm-message').textContent).toBe(message);
    expect(modal.show).toHaveBeenCalledOnce();
    expect(form.submit).not.toHaveBeenCalled();

    yes().click();
    yes().click();
    expect(form.submit).toHaveBeenCalledOnce();
  });

  it.each([
    ['handleNullifyButton', 'performNullify', 'Delete'],
    ['handleConfirmedNullifyButton', 'performConfirmedNullify', 'Continue'],
    ['handleDisputeButton', 'performDispute', 'Dispute'],
  ])('keeps %s in the translated page and waits before sending its request', (handler, performer, label) => {
    const { window, form, yes } = page();
    const perform = vi.spyOn(window.FHPB.Confirmations, performer).mockImplementation(() => {});
    window.FHPB.Confirmations[handler](new window.MouseEvent('click'), form.querySelector('button'), 'Confirm this action?');
    expect(window.confirm).not.toHaveBeenCalled();
    expect(yes().textContent).toBe(label);
    expect(perform).not.toHaveBeenCalled();
    yes().click();
    expect(perform).toHaveBeenCalledOnce();
  });

  it('discards a cancelled callback and resets translated button text when another dialog opens', () => {
    const { window, modal, yes } = page();
    const first = vi.fn(), second = vi.fn();
    window.fhpbConfirm('Delete this match?', first, { confirmText: 'Delete' });
    yes().textContent = 'Eliminar';
    modal.hide();
    window.fhpbConfirm('End this session?', second);
    expect(yes().textContent).toBe('Confirm');
    expect(yes().disabled).toBe(false);
    yes().click();
    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledOnce();
  });

  it('preserves native desktop confirmation when translation is disabled', () => {
    const { window, form, modal } = page({ translation: false });
    window.confirm.mockReturnValueOnce(false).mockReturnValueOnce(true);
    expect(window.handleConfirmForm(form, 'End this session?')).toBe(false);
    expect(window.handleConfirmForm(form, 'End this session?')).toBe(true);
    expect(modal.show).not.toHaveBeenCalled();
    expect(form.submit).not.toHaveBeenCalled();
  });

  it('preserves the mobile modal with translation disabled', () => {
    const { window, form, modal } = page({ translation: false, mobile: true });
    expect(window.handleConfirmForm(form, 'End this session?')).toBe(false);
    expect(modal.show).toHaveBeenCalledOnce();
    expect(window.confirm).not.toHaveBeenCalled();
  });

  it('falls back to a working native confirmation when Bootstrap is unavailable', () => {
    const { window, form } = page();
    window.bootstrap = undefined;
    expect(window.handleConfirmForm(form, 'End this session?')).toBe(true);
    expect(window.confirm).toHaveBeenCalledOnce();
  });
});

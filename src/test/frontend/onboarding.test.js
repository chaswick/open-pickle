// @vitest-environment jsdom

import fs from 'node:fs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const source = fs.readFileSync('src/main/resources/static/js/onboarding.js', 'utf8');

function guide(tour) {
  document.body.innerHTML = `<aside data-onboarding-guide data-onboarding-url="/complete"
    ${tour ? `data-onboarding-tour="${tour}"` : ''}>
    <button data-onboarding-dismiss>Dismiss tips</button>
    <p data-onboarding-error hidden></p>
  </aside>`;
  window.eval(source);
  return document.querySelector('aside');
}

describe('inline onboarding dismissal', () => {
  beforeEach(() => {
    window.FHPB = { Csrf: { headers: headers => ({ ...headers, 'X-CSRF-TOKEN': 'token' }) } };
    window.fetch = vi.fn();
  });

  it('saves home completion with CSRF protection before hiding the guide', async () => {
    window.fetch.mockResolvedValue({ ok: true, redirected: false });
    const element = guide();
    element.querySelector('button').click();

    expect(window.fetch).toHaveBeenCalledWith('/complete', expect.objectContaining({
      method: 'POST', credentials: 'same-origin', headers: { 'X-CSRF-TOKEN': 'token' }
    }));
    await vi.waitFor(() => expect(element.hidden).toBe(true));
  });

  it('sends the session role so owner and joiner preferences remain separate', async () => {
    window.fetch.mockResolvedValue({ ok: true, redirected: false });
    const element = guide('joiner');
    element.querySelector('button').click();

    expect(window.fetch).toHaveBeenCalledWith('/complete', expect.objectContaining({
      body: 'tour=joiner',
      headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'token', 'Content-Type': expect.stringContaining('application/x-www-form-urlencoded') })
    }));
    await vi.waitFor(() => expect(element.hidden).toBe(true));
  });

  it.each([
    { ok: false, redirected: false },
    { ok: true, redirected: true }
  ])('keeps tips usable and allows retry when completion is not saved: %j', async response => {
    window.fetch.mockResolvedValueOnce(response).mockResolvedValueOnce({ ok: true, redirected: false });
    const element = guide('owner');
    const button = element.querySelector('button');
    button.click();
    await vi.waitFor(() => expect(button.disabled).toBe(false));
    expect(element.hidden).toBe(false);
    expect(element.querySelector('[data-onboarding-error]').hidden).toBe(false);

    button.click();
    await vi.waitFor(() => expect(element.hidden).toBe(true));
  });
});

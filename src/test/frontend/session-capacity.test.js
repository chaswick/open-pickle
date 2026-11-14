// @vitest-environment jsdom
import fs from 'node:fs';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';

const script = fs.readFileSync('src/main/resources/static/js/session-capacity.js', 'utf8');

beforeEach(() => {
  vi.useFakeTimers();
  Object.defineProperty(document, 'hidden', { configurable: true, value: false });
  document.body.innerHTML = `<section data-session-capacity data-session-id="42">
    <input name="memberLimit" type="number" value="8" min="1">
    <p data-joined-player-count></p>
    <p data-waitlist-empty>No players waiting.</p>
    <ul data-waitlist></ul>
  </section>`;
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  document.body.innerHTML = '';
});

it('updates the minimum as players join without replacing the host’s unsaved limit', async () => {
  let joined = 4;
  vi.stubGlobal('fetch', vi.fn(async url => ({
    ok: true,
    json: async () => url.endsWith('/members') ? { memberCount: joined } : [{
      requesterDisplayName: 'Casey', requesterPublicCode: 'calm-ruby-paddle'
    }]
  })));
  window.eval(script);
  const limit = document.querySelector('input');
  await vi.waitFor(() => expect(limit.min).toBe('4'));
  limit.value = '7';
  joined = 6;
  await vi.advanceTimersByTimeAsync(5000);
  expect(limit.min).toBe('6');
  expect(limit.value).toBe('7');
  expect(document.querySelector('[data-joined-player-count]').textContent).toBe('6 players joined.');
  const row = document.querySelector('[data-waitlist] li');
  expect(row.querySelector('[data-player-name]').textContent).toBe('Casey');
  expect(row.querySelector('small.session-player-id').textContent).toBe('calm-ruby-paddle');
  expect(row.textContent).not.toContain('(');
  expect(document.querySelector('[data-waitlist-empty]').hidden).toBe(true);
});

it('keeps the last queue and minimum when a refresh fails', async () => {
  const fetch = vi.fn(async url => ({
    ok: true,
    json: async () => url.endsWith('/members') ? { memberCount: 1 } : [{ requesterDisplayName: 'Casey' }]
  }));
  vi.stubGlobal('fetch', fetch);
  window.eval(script);
  await vi.waitFor(() => expect(document.querySelector('[data-waitlist] li')).not.toBeNull());
  fetch.mockRejectedValue(new Error('offline'));
  await vi.advanceTimersByTimeAsync(5000);
  expect(document.querySelector('[data-waitlist] li').textContent).toBe('Casey');
  expect(document.querySelector('input').min).toBe('1');
  expect(document.querySelector('[data-joined-player-count]').textContent).toBe('1 player joined.');
});

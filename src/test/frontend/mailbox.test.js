// @vitest-environment jsdom
import fs from 'node:fs';
import { afterEach, beforeAll, beforeEach, expect, it, vi } from 'vitest';

function contents(count, rows = '') {
  return `<section id="playerMailbox" data-mailbox-url="/mailbox">
    <button data-mailbox-button="inbox">Inbox <span data-mailbox-count>${count}</span></button>
    <button data-mailbox-button="outbox">Outbox <span data-mailbox-count>0</span></button>
  </section><div id="playerInboxModal" class="modal show" data-mailbox-modal="inbox">
    <div id="playerInboxBody">${rows || 'No matches need your confirmation.'}</div></div>
  <div id="playerOutboxBody">No matches are waiting on the other team.</div>`;
}
const row = `<div class="match-dashboard-card"><div data-match-row data-match-id="42" data-remove-on-confirm="true">
  <form class="confirm-match-form" action="/matches/42/confirm" data-match-id="42">
    <input name="expectedVersion" value="1"><button type="button" data-match-id="42">Confirm</button>
  </form></div></div>`;

beforeAll(() => {
  window.FHPB = { Csrf: { headers: headers => headers } };
  window.eval(fs.readFileSync('src/main/resources/static/js/components.js', 'utf8'));
  window.eval(fs.readFileSync('src/main/resources/static/js/mailbox.js', 'utf8'));
});
beforeEach(() => {
  document.body.innerHTML = contents(1, row);
  window.fetch = vi.fn();
  vi.spyOn(window.FHPB.Confirmations, 'showFloatingMessage').mockImplementation(() => {});
});
afterEach(() => vi.restoreAllMocks());

it('keeps both controls visible at zero and preserves the open modal after confirming its last match', async () => {
  const modal = document.getElementById('playerInboxModal');
  const inboxButton = document.querySelector('[data-mailbox-button="inbox"]');
  window.fetch.mockResolvedValueOnce({ ok: true, text: async () => JSON.stringify({ success: true }) })
    .mockResolvedValueOnce({ ok: true, text: async () => contents(0) });
  window.FHPB.Confirmations.handleConfirm(document.querySelector('form button'));
  await vi.waitFor(() => expect(document.querySelector('[data-mailbox-count]').textContent).toBe('0'));
  expect(document.querySelectorAll('[data-mailbox-button]')).toHaveLength(2);
  expect(document.querySelectorAll('.match-dashboard-card')).toHaveLength(0);
  expect(document.getElementById('playerInboxBody').textContent).toContain('No matches need your confirmation.');
  expect(document.getElementById('playerInboxModal')).toBe(modal);
  expect(document.querySelector('[data-mailbox-button="inbox"]')).toBe(inboxButton);
  expect(modal.classList.contains('show')).toBe(true);
  expect(window.fetch).toHaveBeenNthCalledWith(2, '/mailbox', expect.objectContaining({ credentials: 'same-origin' }));
});

it('retains the last usable counts and match controls when a refresh fails or returns a login page', async () => {
  const original = document.body.innerHTML;
  window.fetch.mockResolvedValueOnce({ ok: false });
  expect(await window.FHPB.Mailbox.refresh()).toBe(false);
  expect(document.body.innerHTML).toBe(original);
  window.fetch.mockResolvedValueOnce({ ok: true, text: async () => '<html><body>Sign in</body></html>' });
  expect(await window.FHPB.Mailbox.refresh()).toBe(false);
  expect(document.body.innerHTML).toBe(original);
});

it('coalesces overlapping refreshes and fetches again after an action during a refresh', async () => {
  let finish;
  window.fetch.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }))
    .mockResolvedValueOnce({ ok: true, text: async () => contents(0) });
  const initial = window.FHPB.Mailbox.refresh();
  window.FHPB.Mailbox.refresh();
  finish({ ok: true, text: async () => contents(1, row) });
  await initial;
  await vi.waitFor(() => expect(document.querySelector('[data-mailbox-count]').textContent).toBe('0'));
  expect(window.fetch).toHaveBeenCalledTimes(2);
  expect(document.querySelectorAll('[data-mailbox-button]')).toHaveLength(2);
});

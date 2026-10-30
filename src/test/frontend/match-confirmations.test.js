// @vitest-environment jsdom
import fs from 'node:fs';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

const source = fs.readFileSync('src/main/resources/static/js/components.js', 'utf8');
const actions = [
  ['confirm', 'confirm-match-form', 'handleConfirm', 'Confirmed'],
  ['request-nullify', 'request-match-nullify-form', 'performConfirmedNullify', 'Nullify requested'],
  ['request-nullify', 'confirm-match-nullify-form', 'performConfirmedNullify', 'Nullified'],
  ['dispute', 'dispute-match-form', 'performDispute', 'Disputed'],
];

function matchRow(formClass, action, status = 'Original match', version = 1) {
  return `<div data-match-row data-match-id="42" data-remove-on-confirm="false">
    <table class="table"><tbody><tr><td>Player A</td><td>11</td></tr>
    <tr><td colspan="2"><span data-status>${status}</span>
      <form class="${formClass}" action="/matches/42/${action}" data-match-id="42">
        <input name="expectedVersion" value="${version}">
        <button type="button" data-match-id="42">Act</button>
      </form></td></tr><tr><td>Player B</td><td>7</td></tr></tbody></table>
  </div>`;
}

function submit(method) {
  const form = document.querySelector('form');
  const button = form.querySelector('button');
  if (method === 'handleConfirm') window.FHPB.Confirmations[method](button);
  else window.FHPB.Confirmations[method](form, button);
}

beforeAll(() => {
  window.FHPB = { Csrf: { headers: headers => headers } };
  window.eval(source);
});

beforeEach(() => {
  document.body.innerHTML = '';
  window.fetch = vi.fn();
  vi.spyOn(window.FHPB.Confirmations, 'showFloatingMessage').mockImplementation(() => {});
});

afterEach(() => vi.restoreAllMocks());

describe('match action refresh', () => {
  it.each(actions)('%s with %s replaces the recent match instead of nesting it inside a control',
    async (action, formClass, method, status) => {
      document.body.innerHTML = `<ul id="recent-matches"><li>${matchRow(formClass, action)}</li></ul>`;
      window.fetch
        .mockResolvedValueOnce({ ok: true, text: async () => JSON.stringify({ success: true }) })
        .mockResolvedValueOnce({ ok: true, text: async () => matchRow(formClass, action, status, 2) });

      submit(method);

      await vi.waitFor(() => expect(document.querySelector('[data-status]').textContent).toBe(status));
      expect(document.querySelectorAll('#recent-matches table')).toHaveLength(1);
      expect(document.querySelectorAll('[data-match-row]')).toHaveLength(1);
      expect(document.querySelector('form [data-match-row]')).toBeNull();
      expect(document.querySelector('[name="expectedVersion"]').value).toBe('2');
      expect(window.fetch).toHaveBeenNthCalledWith(1, `/matches/42/${action}`,
        expect.objectContaining({ method: 'POST', body: expect.any(URLSearchParams) }));
      expect(window.fetch.mock.calls[0][1].body.get('expectedVersion')).toBe('1');
    });

  it.each(actions)('%s with %s removes the whole session confirmation card',
    async (action, formClass, method) => {
      const row = matchRow(formClass, action).replace('data-remove-on-confirm="false"', 'data-remove-on-confirm="true"');
      document.body.innerHTML = `<div data-session-confirmation-modal="inbox"><div>
        <div class="match-dashboard-card">${row}</div>
        <div class="match-dashboard-card" data-other-match><table><tr><td>Other game</td></tr></table></div>
      </div></div>`;
      window.fetch.mockResolvedValueOnce({ ok: true, text: async () => JSON.stringify({ success: true }) });

      submit(method);

      await vi.waitFor(() => expect(document.querySelectorAll('.match-dashboard-card')).toHaveLength(1));
      expect(document.querySelector('[data-other-match]')).not.toBeNull();
      expect(document.querySelector('[data-match-row]')).toBeNull();
      expect(document.querySelectorAll('table')).toHaveLength(1);
      expect(window.fetch).toHaveBeenCalledTimes(1);
    });

  it('refreshes the standalone Inbox/Outbox dashboard after an action', async () => {
    document.body.innerHTML = `<div id="matchDashboardContainer"><div class="match-dashboard-card">
      ${matchRow('confirm-match-form', 'confirm')}</div></div>`;
    const refresh = vi.spyOn(window.FHPB.Confirmations, 'refreshMatchDashboardSection').mockImplementation(() => {});
    window.fetch.mockResolvedValueOnce({ ok: true, text: async () => JSON.stringify({ success: true }) });

    submit('handleConfirm');

    await vi.waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(document.querySelectorAll('table')).toHaveLength(1);
    expect(window.fetch).toHaveBeenCalledTimes(1);
  });

  it('refreshes a stale-version conflict without nesting a second match', async () => {
    document.body.innerHTML = matchRow('confirm-match-form', 'confirm');
    window.fetch
      .mockResolvedValueOnce({ ok: false, status: 409, text: async () => JSON.stringify({ message: 'Already updated' }) })
      .mockResolvedValueOnce({ ok: true, text: async () => matchRow('confirm-match-form', 'confirm', 'Latest match', 2) });

    submit('handleConfirm');

    await vi.waitFor(() => expect(document.querySelector('[data-status]').textContent).toBe('Latest match'));
    expect(document.querySelectorAll('table')).toHaveLength(1);
    expect(document.querySelectorAll('[data-match-row]')).toHaveLength(1);
    expect(document.querySelector('[name="expectedVersion"]').value).toBe('2');
  });
});

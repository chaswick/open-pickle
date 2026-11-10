// @vitest-environment jsdom
import fs from 'node:fs';
import { beforeEach, afterEach, expect, it, vi } from 'vitest';

const source = fs.readFileSync('src/main/resources/static/js/session-nearby-share.js', 'utf8');
const session = { sessionId: 42, sessionTitle: 'Sunday games', ownerDisplayName: 'Pat', locationName: 'Park courts', activeMemberCount: 4 };
const response = (payload, ok = true) => ({ ok, json: async () => payload });

beforeEach(() => {
  document.body.innerHTML = `<details data-nearby-discovery><summary>Nearby sessions</summary>
    <div data-session-nearby-join><button data-nearby-start>Find nearby sessions</button>
      <div class="d-none" data-nearby-status></div>
      <div class="d-none" data-nearby-results><div data-nearby-results-summary></div><div data-nearby-results-list></div></div>
    </div></details>`;
  window.FHPB = { Csrf: { headers: headers => headers } };
  Object.defineProperty(window, 'isSecureContext', { value: true, configurable: true });
  Object.defineProperty(navigator, 'geolocation', { value: { getCurrentPosition: vi.fn() }, configurable: true });
  window.fetch = vi.fn();
});
afterEach(() => vi.restoreAllMocks());
function start() { window.eval(source); }

it('opens nearby sessions found through an existing check-in without asking for location', async () => {
  window.fetch.mockResolvedValue(response({ locationName: 'Park courts', sessions: [session] }));
  start();
  await vi.waitFor(() => expect(document.querySelector('[data-nearby-discovery]').open).toBe(true));
  expect(document.querySelector('[data-nearby-session-title]').textContent).toBe('Sunday games');
  expect(document.querySelector('[data-nearby-session-join]').textContent).toBe('Ask to Join');
  expect(navigator.geolocation.getCurrentPosition).not.toHaveBeenCalled();
  expect(window.fetch).toHaveBeenCalledTimes(1);
  expect(window.fetch).toHaveBeenCalledWith('/api/sessions/nearby-sharing/candidates', expect.objectContaining({ credentials: 'same-origin' }));
});

it('leaves an empty nearby section collapsed without asking for location', async () => {
  window.fetch.mockResolvedValue(response({ locationName: 'Park courts', sessions: [] }));
  start();
  await vi.waitFor(() => expect(window.fetch).toHaveBeenCalledTimes(1));
  expect(document.querySelector('[data-nearby-discovery]').open).toBe(false);
  expect(document.querySelector('[data-nearby-results]').classList.contains('d-none')).toBe(true);
  expect(navigator.geolocation.getCurrentPosition).not.toHaveBeenCalled();
});

it('keeps manual search usable when there is no active check-in', async () => {
  window.fetch.mockResolvedValue(response({ message: 'Check in first.' }, false));
  start();
  await vi.waitFor(() => expect(window.fetch).toHaveBeenCalledTimes(1));
  expect(document.querySelector('[data-nearby-discovery]').open).toBe(false);
  expect(document.querySelector('[data-nearby-start]').disabled).toBe(false);
  expect(document.querySelector('[data-nearby-status]').classList.contains('d-none')).toBe(true);
  expect(navigator.geolocation.getCurrentPosition).not.toHaveBeenCalled();
});

it('searches only when requested and reveals sessions after a successful location lookup', async () => {
  window.fetch.mockResolvedValueOnce(response({ sessions: [] }))
    .mockResolvedValueOnce(response({ status: 'checked_in' }))
    .mockResolvedValueOnce(response({ locationName: 'Park courts', sessions: [session] }));
  navigator.geolocation.getCurrentPosition.mockImplementation(ok => ok({ coords: { latitude: 40, longitude: -75 } }));
  start();
  await vi.waitFor(() => expect(window.fetch).toHaveBeenCalledTimes(1));
  document.querySelector('[data-nearby-discovery]').open = true;
  document.querySelector('[data-nearby-start]').click();
  await vi.waitFor(() => expect(document.querySelector('[data-nearby-session-title]')?.textContent).toBe('Sunday games'));
  expect(navigator.geolocation.getCurrentPosition).toHaveBeenCalledTimes(1);
  expect(window.fetch).toHaveBeenNthCalledWith(2, '/api/check-in/session-nearby-join', expect.objectContaining({ method: 'POST' }));
  expect(document.querySelector('[data-nearby-start]').disabled).toBe(false);
});

// @vitest-environment jsdom

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const scriptPath = path.resolve(
  __dirname,
  '../../main/resources/static/js/session-standings.js');
const scriptSource = fs.readFileSync(scriptPath, 'utf8');

function ordinalSuffix(rank) {
  const mod100 = rank % 100;
  if (mod100 >= 11 && mod100 <= 13) {
    return 'th';
  }
  if (rank % 10 === 1) {
    return 'st';
  }
  if (rank % 10 === 2) {
    return 'nd';
  }
  if (rank % 10 === 3) {
    return 'rd';
  }
  return 'th';
}

function ratingLabel(rating, rank) {
  return `${rating} (${rank}${ordinalSuffix(rank)})`;
}

function renderStandingRow(player, index, currentUserId, activeStat) {
  const statValue = activeStat === 'wins'
    ? player.wins
    : activeStat === 'points-for'
      ? player.pointsFor
      : player.ratingLabel;
  const currentUserClass = String(player.userId) === String(currentUserId) ? ' current-user-row' : '';

  return `
    <div class="list-group-item ladder-standing-row session-roster-row${currentUserClass}"
         data-session-row
         data-session-original-rank="${index + 1}"
         data-session-row-user-id="${player.userId}"
         data-session-rating-label="${player.ratingLabel}"
         data-session-sort-rating="${player.rating}"
         data-session-sort-wins="${player.wins}"
         data-session-sort-points-for="${player.pointsFor}"
         data-session-awaiting-confirmation="false">
      <div class="ladder-row-inner session-roster-row-inner">
        <span class="session-roster-form-col">
          <span class="${player.formClass}" title="${player.formTitle}">
            <i class="${player.formIconClass}" aria-hidden="true"></i>
          </span>
        </span>
        <span class="ladder-rank text-muted session-roster-rank"
              data-session-row-rank>${index + 1}</span>
        <div class="player-cell session-roster-player-cell">
          <div class="player-name-with-badges">
            <div class="player-name-line">
              <span class="fw-semibold player-name-label">${player.name}</span>
            </div>
          </div>
        </div>
        <span class="ladder-rating text-muted small session-roster-stat-cell"
              data-session-stat-cell>
          <span class="session-roster-stat-stack">
            <span class="session-roster-number session-roster-stat-value"
                  data-session-stat-text>${statValue}</span>
          </span>
        </span>
      </div>
    </div>`;
}

function renderTickerAnchor(items = []) {
  const markup = items.length
    ? `
      <div class="session-recent-ticker-shell" data-session-recent-ticker-shell="true">
        <div class="session-recent-ticker" data-session-recent-ticker="true">
          <span class="session-recent-ticker-viewport">
            <span class="session-recent-ticker-fallback" data-session-recent-source aria-hidden="true">
              ${items.map((item) => `
                <span class="session-recent-ticker-item" data-session-recent-item>
                  <span class="session-recent-ticker-age"
                        data-session-recent-age
                        data-utc-time="${item.confirmedAt}"
                        data-time-format="relative-change">${item.ageLabel}</span>
                  <span class="session-recent-ticker-separator" aria-hidden="true">:</span>
                  <span class="session-recent-ticker-copy">${item.summary}</span>
                </span>`).join('')}
            </span>
            <span class="session-recent-ticker-marquee d-none"
                  data-session-recent-marquee
                  aria-hidden="true"></span>
          </span>
        </div>
      </div>`
    : '';

  return `
    <div data-session-recent-ticker-anchor="true">
      ${markup}
    </div>`;
}

function renderStandingsCard(players, options = {}) {
  const currentUserId = String(options.currentUserId || players[0]?.userId || '1');
  const activeStat = options.activeStat || 'rating';
  const includeCollapse = options.includeCollapse !== false;
  const collapseId = options.collapseId || 'sessionStandingsCollapse';

  const ratingActive = activeStat === 'rating';
  const winsActive = activeStat === 'wins';
  const pointsActive = activeStat === 'points-for';
  const cardBody = `
    <div class="card-body p-0">
      <div class="list-group list-group-flush session-roster-list"
           data-session-standings-list="true">
        <div class="list-group-item ladder-standing-row ladder-header-row">
          <div class="ladder-row-inner session-roster-row-inner text-uppercase small fw-semibold text-muted">
            <span class="session-roster-form-col"></span>
            <span class="ladder-rank">Rank</span>
            <span class="player-cell">Player</span>
            <span class="ladder-rating session-roster-stat-header">
              <div class="session-stat-links">
                <button type="button"
                        class="session-stat-link${ratingActive ? ' is-active' : ''}"
                        data-session-stat-toggle="rating"
                        aria-pressed="${ratingActive}">
                  Rtg
                </button>
                <span class="session-stat-divider" aria-hidden="true">/</span>
                <button type="button"
                        class="session-stat-link${winsActive ? ' is-active' : ''}"
                        data-session-stat-toggle="wins"
                        aria-pressed="${winsActive}">
                  W
                </button>
                <span class="session-stat-divider" aria-hidden="true">/</span>
                <button type="button"
                        class="session-stat-link${pointsActive ? ' is-active' : ''}"
                        data-session-stat-toggle="points-for"
                        aria-pressed="${pointsActive}">
                  PF
                </button>
              </div>
            </span>
          </div>
        </div>
        ${players.map((player, index) => renderStandingRow(player, index, currentUserId, activeStat)).join('')}
      </div>
    </div>`;

  return `
    <div class="card mb-3"
         data-session-standings-root="true"
         data-session-standings-pending="false"
         data-session-ladder-id="7"
         data-session-season-id="11"
         data-session-current-user-id="${currentUserId}">
      <div class="card-header card-header-title d-flex justify-content-between align-items-center">
        <span>Session Standings</span>
        <div class="session-standings-header-tools"></div>
      </div>
      ${includeCollapse ? `<div id="${collapseId}" class="collapse show">${cardBody}</div>` : cardBody}
    </div>`;
}

function installLayoutMetrics(root) {
  const list = root.querySelector('[data-session-standings-list="true"]');
  if (!list) {
    return;
  }

  Object.defineProperty(list, 'clientWidth', {
    configurable: true,
    get() {
      return 360;
    }
  });

  Object.defineProperty(list, 'scrollHeight', {
    configurable: true,
    get() {
      return list.querySelectorAll('[data-session-row]').length * 56;
    }
  });

  Array.from(list.querySelectorAll('[data-session-row]')).forEach((row) => {
    Object.defineProperty(row, 'offsetTop', {
      configurable: true,
      get() {
        return Array.from(list.querySelectorAll('[data-session-row]')).indexOf(row) * 56;
      }
    });

    row.getBoundingClientRect = () => ({
      top: row.offsetTop,
      left: 0,
      width: 360,
      height: 48,
      right: 360,
      bottom: row.offsetTop + 48
    });
  });
}

function installEnvironment() {
  Object.defineProperty(document, 'visibilityState', {
    configurable: true,
    get() {
      return 'visible';
    }
  });

  window.matchMedia = vi.fn().mockReturnValue({
    matches: false,
    addEventListener() {},
    removeEventListener() {},
    addListener() {},
    removeListener() {}
  });

  window.FHPB = {
    CardCollapse: {
      upgradeAll(root) {
        installLayoutMetrics(root);
      }
    },
    CollapseState: {
      captureCurrentState() {},
      restoreAll() {}
    },
    SessionRecentTicker: {
      mountAll: vi.fn(),
      unmount: vi.fn()
    }
  };
  window.fetch = vi.fn();
  global.fetch = window.fetch;
  window.eval(scriptSource);
}

function configureReplayTimings() {
  window.FHPB.SessionStandings.replayInitialDelayMs = 120;
  window.FHPB.SessionStandings.replayMoveMs = 600;
  window.FHPB.SessionStandings.replayStaggerMs = 80;
}

function mountCurrent(players, options = {}) {
  document.body.innerHTML =
    renderTickerAnchor(options.tickerItems || []) + renderStandingsCard(players, options);
  const root = window.FHPB.SessionStandings.getRoot();
  installLayoutMetrics(root);
  window.FHPB.SessionStandings.mount(root);
  if (options.displayStat && options.displayStat !== 'rating') {
    root._sessionStandingsUpdateStat(options.displayStat, { persist: false });
  }
  return root;
}

function buildDocumentHtml(players, options = {}) {
  return `<!doctype html><html><body>${renderTickerAnchor(options.tickerItems || [])}${renderStandingsCard(players, options)}</body></html>`;
}

async function flushMicrotasks() {
  for (let index = 0; index < 8; index += 1) {
    await Promise.resolve();
  }
}

async function refreshTo(players, options = {}) {
  window.fetch = vi.fn().mockResolvedValue({
    ok: true,
    text: async () => buildDocumentHtml(players, options)
  });
  global.fetch = window.fetch;
  window.FHPB.SessionStandings.refreshNow('poll');
  await flushMicrotasks();
  return window.FHPB.SessionStandings.getRoot();
}

function createPlayer(userId, name, rating, wins, pointsFor, formTitle, formClass, formIconClass, rank) {
  return {
    userId: String(userId),
    name,
    rating,
    wins,
    pointsFor,
    formTitle,
    formClass,
    formIconClass,
    ratingLabel: ratingLabel(rating, rank)
  };
}

function getLayer(root) {
  return root.querySelector('[data-session-replay-layer]');
}

function getRow(root, userId) {
  return root.querySelector(`[data-session-row-user-id="${userId}"]`);
}

function getTickerAnchor(scope = document) {
  return scope.querySelector('[data-session-recent-ticker-anchor="true"]');
}

function getRankText(scope) {
  return scope.querySelector('[data-session-row-rank]').textContent.trim();
}

function getStatText(scope) {
  return scope.querySelector('[data-session-stat-text]').textContent.trim();
}

function getFormTitle(scope) {
  return scope.querySelector('.session-roster-form').getAttribute('title');
}

describe('session standings replay', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    document.body.innerHTML = '';
    sessionStorage.clear();
    installEnvironment();
    configureReplayTimings();
  });

  afterEach(() => {
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
    document.body.innerHTML = '';
  });

  it('stages old rows immediately after refresh so fresh standings do not flash before replay', async () => {
    const previousPlayers = [
      createPlayer(1, 'Alice', 1500, 5, 44, '+4 form', 'ladder-momentum session-roster-form text-success', 'bi bi-triangle-half', 1),
      createPlayer(2, 'Bob', 1400, 3, 38, '0 form', 'ladder-momentum session-roster-form text-muted', 'bi bi-dash-circle', 2)
    ];
    const freshPlayers = [
      createPlayer(2, 'Bob', 1510, 6, 49, '+6 form', 'ladder-momentum session-roster-form text-success', 'bi bi-triangle-fill', 1),
      createPlayer(1, 'Alice', 1490, 5, 44, '+3 form', 'ladder-momentum session-roster-form text-success', 'bi bi-triangle', 2)
    ];

    mountCurrent(previousPlayers, { currentUserId: '1' });
    const freshRoot = await refreshTo(freshPlayers, { currentUserId: '1' });

    const hiddenRows = Array.from(
      freshRoot.querySelectorAll('[data-session-row].is-session-replay-hidden'));
    expect(hiddenRows.map((row) => row.getAttribute('data-session-row-user-id')).sort()).toEqual(['1', '2']);

    const layer = getLayer(freshRoot);
    expect(layer).not.toBeNull();
    expect(layer.children.length).toBe(2);

    const bobClone = layer.querySelector('[data-session-row-user-id="2"]');
    const aliceClone = layer.querySelector('[data-session-row-user-id="1"]');
    expect(getRankText(bobClone)).toBe('2');
    expect(getStatText(bobClone)).toBe('1400 (2nd)');
    expect(getRankText(aliceClone)).toBe('1');
    expect(getStatText(aliceClone)).toBe('1500 (1st)');
  });

  it('keeps old rank, form, and selected stat visible until replay ends', async () => {
    const previousPlayers = [
      createPlayer(1, 'Alice', 1500, 5, 44, '+4 form', 'ladder-momentum session-roster-form text-success', 'bi bi-triangle-half', 1),
      createPlayer(2, 'Bob', 1400, 3, 38, '0 form', 'ladder-momentum session-roster-form text-muted', 'bi bi-dash-circle', 2)
    ];
    const freshPlayers = [
      createPlayer(2, 'Bob', 1510, 6, 49, '+6 form', 'ladder-momentum session-roster-form text-success', 'bi bi-triangle-fill', 1),
      createPlayer(1, 'Alice', 1490, 5, 44, '+3 form', 'ladder-momentum session-roster-form text-success', 'bi bi-triangle', 2)
    ];

    const currentRoot = mountCurrent(previousPlayers, {
      currentUserId: '1',
      displayStat: 'wins'
    });
    currentRoot._sessionStandingsUpdateStat('wins', { persist: false });

    const freshRoot = await refreshTo(freshPlayers, {
      currentUserId: '1',
      activeStat: 'wins'
    });
    const layer = getLayer(freshRoot);
    const bobClone = layer.querySelector('[data-session-row-user-id="2"]');
    const bobLive = getRow(freshRoot, '2');

    expect(getRankText(bobClone)).toBe('2');
    expect(getStatText(bobClone)).toBe('3');
    expect(getFormTitle(bobClone)).toBe('0 form');
    expect(bobLive.classList.contains('is-session-replay-hidden')).toBe(true);

    const totalReplayMs = window.FHPB.SessionStandings.replayInitialDelayMs
      + window.FHPB.SessionStandings.replayMoveMs
      + window.FHPB.SessionStandings.replayStaggerMs
      + 20;
    await vi.advanceTimersByTimeAsync(totalReplayMs);

    expect(getLayer(freshRoot).children.length).toBe(0);
    expect(bobLive.classList.contains('is-session-replay-hidden')).toBe(false);
    expect(getRankText(bobLive)).toBe('1');
    expect(getStatText(bobLive)).toBe('6');
    expect(getFormTitle(bobLive)).toBe('+6 form');
  });

  it('reapplies collapse helpers after replacing the standings card', async () => {
    const previousPlayers = [
      createPlayer(1, 'Alice', 1500, 5, 44, '+4 form', 'ladder-momentum session-roster-form text-success', 'bi bi-triangle-half', 1),
      createPlayer(2, 'Bob', 1400, 3, 38, '0 form', 'ladder-momentum session-roster-form text-muted', 'bi bi-dash-circle', 2)
    ];
    const freshPlayers = [
      createPlayer(2, 'Bob', 1510, 6, 49, '+6 form', 'ladder-momentum session-roster-form text-success', 'bi bi-triangle-fill', 1),
      createPlayer(1, 'Alice', 1490, 5, 44, '+3 form', 'ladder-momentum session-roster-form text-success', 'bi bi-triangle', 2)
    ];

    const upgradeAll = vi.fn((root) => installLayoutMetrics(root));
    const captureCurrentState = vi.fn();
    const restoreAll = vi.fn();
    window.FHPB.CardCollapse = { upgradeAll };
    window.FHPB.CollapseState = { captureCurrentState, restoreAll };

    mountCurrent(previousPlayers, {
      currentUserId: '1',
      includeCollapse: true,
      collapseId: 'sessionStandingsCollapse'
    });

    const freshRoot = await refreshTo(freshPlayers, {
      currentUserId: '1',
      includeCollapse: false
    });

    expect(captureCurrentState).toHaveBeenCalledWith('sessionStandingsCollapse', expect.any(HTMLElement));
    expect(upgradeAll).toHaveBeenCalledWith(freshRoot);
    expect(restoreAll).toHaveBeenCalledWith(freshRoot);
  });

  it('refreshes the ticker from the existing standings poll without replacing the standings card', async () => {
    const players = [
      createPlayer(1, 'Alice', 1500, 5, 44, '+4 form', 'ladder-momentum session-roster-form text-success', 'bi bi-triangle-half', 1),
      createPlayer(2, 'Bob', 1400, 3, 38, '0 form', 'ladder-momentum session-roster-form text-muted', 'bi bi-dash-circle', 2)
    ];

    mountCurrent(players, {
      currentUserId: '1',
      tickerItems: [
        {
          ageLabel: '2 minutes ago',
          summary: 'Alice & Bob def Carol & Dave 11-8',
          confirmedAt: '2026-03-31T15:55:00Z'
        }
      ]
    });
    const currentRoot = window.FHPB.SessionStandings.getRoot();
    const originalTickerMarkup = getTickerAnchor().innerHTML;

    await refreshTo(players, {
      currentUserId: '1',
      tickerItems: [
        {
          ageLabel: 'Just now',
          summary: 'Eve & Frank def Gail & Hank 11-6',
          confirmedAt: '2026-03-31T16:05:00Z'
        }
      ]
    });

    expect(window.FHPB.SessionRecentTicker.unmount).toHaveBeenCalledTimes(1);
    expect(window.FHPB.SessionRecentTicker.mountAll).toHaveBeenCalledWith(getTickerAnchor());
    expect(getTickerAnchor().innerHTML).not.toBe(originalTickerMarkup);
    expect(getTickerAnchor().textContent).toContain('Eve & Frank def Gail & Hank 11-6');
    expect(window.FHPB.SessionStandings.getRoot()).toBe(currentRoot);
    expect(getLayer(currentRoot)).toBeNull();
  });

  it('does not restart the ticker on repeated polls when the underlying ticker items have not changed', async () => {
    const players = [
      createPlayer(1, 'Alice', 1500, 5, 44, '+4 form', 'ladder-momentum session-roster-form text-success', 'bi bi-triangle-half', 1),
      createPlayer(2, 'Bob', 1400, 3, 38, '0 form', 'ladder-momentum session-roster-form text-muted', 'bi bi-dash-circle', 2)
    ];
    const tickerItems = [
      {
        ageLabel: 'Just now',
        summary: 'Alice & Bob def Carol & Dave 11-8',
        confirmedAt: '2026-03-31T15:55:00Z'
      }
    ];

    mountCurrent(players, {
      currentUserId: '1',
      tickerItems
    });

    const anchor = getTickerAnchor();
    const marquee = anchor.querySelector('[data-session-recent-marquee]');
    marquee.innerHTML = `
      <span class="session-recent-ticker-track">
        <span class="session-recent-ticker-segment">
          <span class="session-recent-ticker-item" data-session-recent-item>
            <span class="session-recent-ticker-age"
                  data-session-recent-age
                  data-utc-time="2026-03-31T15:55:00Z"
                  data-time-format="relative-change">Just now</span>
            <span class="session-recent-ticker-separator" aria-hidden="true">:</span>
            <span class="session-recent-ticker-copy">Alice & Bob def Carol & Dave 11-8</span>
          </span>
          <span class="session-recent-ticker-item" data-session-recent-item>
            <span class="session-recent-ticker-age"
                  data-session-recent-age
                  data-utc-time="2026-03-31T15:55:00Z"
                  data-time-format="relative-change">Just now</span>
            <span class="session-recent-ticker-separator" aria-hidden="true">:</span>
            <span class="session-recent-ticker-copy">Alice & Bob def Carol & Dave 11-8</span>
          </span>
        </span>
      </span>`;

    await refreshTo(players, {
      currentUserId: '1',
      tickerItems
    });

    expect(window.FHPB.SessionRecentTicker.unmount).not.toHaveBeenCalled();
    expect(window.FHPB.SessionRecentTicker.mountAll).not.toHaveBeenCalled();
    expect(getTickerAnchor().textContent).toContain('Alice & Bob def Carol & Dave 11-8');
  });
});

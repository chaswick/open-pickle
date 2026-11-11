// @vitest-environment jsdom
import fs from 'node:fs';
import { beforeAll, afterEach, expect, it } from 'vitest';

beforeAll(() => {
  window.eval(fs.readFileSync('src/main/resources/static/js/components.js', 'utf8'));
  window.eval(fs.readFileSync('src/main/resources/static/js/session-matchups.js', 'utf8'));
});
afterEach(() => {
  document.body.innerHTML = '';
  window.sessionStorage.clear();
});

it('keeps card controls and the chosen collapsed state after a live matchup refresh', () => {
  const page = revision => `<main class="app-page"><section data-session-matchups-root data-revision="${revision}">
    <section class="card" id="round-robin-summary"><div class="card-header card-header-title">Round robin</div><div class="card-body">Mix partners</div></section>
    <section class="card" id="matchup-schedule"><div class="card-header card-header-title">Matchups</div><div class="card-body">
      <article class="card"><div class="card-header card-header-title">Game 1</div><div class="card-body">Players</div></article>
    </div></section>
    </section>
    <section class="card" id="session-options-card" data-card-collapse="off"><div class="card-header card-header-title">Session options</div><div class="card-body">End Session</div></section>
    </main>`;
  document.body.innerHTML = page('one');
  window.FHPB.CardCollapse.upgradeAll(document);
  const summary = document.querySelector('#round-robin-summary .collapse');
  const collapseId = summary.id;
  window.FHPB.CollapseState.applyExpandedState(summary, false);
  const fresh = new DOMParser().parseFromString(page('two'), 'text/html');
  expect(window.FHPB.SessionMatchups.sync(fresh)).toBe(true);
  expect(document.querySelector('#round-robin-summary .collapse').id).toBe(collapseId);
  expect(document.querySelector('#round-robin-summary .collapse').classList.contains('show')).toBe(false);
  expect(document.querySelector('#round-robin-summary button').getAttribute('aria-expanded')).toBe('false');
  expect(document.querySelector('#matchup-schedule .collapse').classList.contains('show')).toBe(true);
  expect(document.querySelectorAll('#matchup-schedule .card-collapse-toggle')).toHaveLength(1);
  expect(document.querySelectorAll('#session-options-card .card-collapse-toggle')).toHaveLength(0);
  expect(document.querySelectorAll('.card-collapse-toggle')).toHaveLength(2);
});

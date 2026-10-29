// @vitest-environment jsdom
import fs from 'node:fs';
import path from 'node:path';
import { beforeAll, afterEach, describe, expect, it } from 'vitest';

const source = fs.readFileSync(path.resolve('src/main/resources/static/js/session-matchups.js'), 'utf8');
function preparation(count, revision = 'one') {
  const members = Array.from({ length: count }, (_, i) => ({ id: i + 1, name: `Player ${i + 1}` }));
  return `<section data-session-matchups-root data-revision="${revision}"><form data-matchups-setup>
    <input name="organizerScored" type="radio" value="false" checked>
    <input name="organizerScored" type="radio" value="true">
    <input name="format" type="radio" value="ROTATING_PARTNERS" checked>
    <input name="format" type="radio" value="FIXED_TEAMS">
    <div data-fixed-pairs hidden><div data-pair-list></div><input data-fixed-teams></div>
    <div data-rotating-rounds><input name="rounds" value="6"></div>
    <p data-setup-summary></p><p data-setup-error hidden></p><button type="submit">Start</button>
    <script type="application/json" data-matchup-members>${JSON.stringify(members)}</script>
    </form></section>`;
}
function mount(count) {
  document.body.innerHTML = preparation(count);
  const root = document.querySelector('[data-session-matchups-root]');
  window.FHPB.SessionMatchups.mount(root);
  return root;
}
function fixed(root) {
  const radio = root.querySelector('[value=FIXED_TEAMS]'); radio.checked = true;
  radio.dispatchEvent(new Event('change', { bubbles: true }));
}
beforeAll(() => window.eval(source));
afterEach(() => { document.body.innerHTML = ''; document.body.classList.remove('modal-open'); });

describe('Session matchup setup', () => {
  it('requires a date only when Later is selected and preserves it when switching back', () => {
    document.body.innerHTML = `<form><input type="radio" name="sessionWhen" value="NOW" checked>
      <input type="radio" name="sessionWhen" value="LATER">
      <div data-session-date-choice hidden><input name="sessionDate" type="date" value="2027-01-16" disabled></div></form>`;
    const date = document.querySelector('[name=sessionDate]');
    for (const value of ['LATER', 'NOW', 'LATER']) {
      const radio = document.querySelector(`[name=sessionWhen][value=${value}]`);
      radio.checked = true;
      radio.dispatchEvent(new Event('change', { bubbles: true }));
      expect(date.disabled).toBe(value === 'NOW');
      expect(date.required).toBe(value === 'LATER');
      expect(date.parentElement.hidden).toBe(value === 'NOW');
      expect(date.value).toBe('2027-01-16');
    }
  });
  it('swaps selected partners while keeping every joined player in exactly one pair', () => {
    const root = mount(16); fixed(root);
    const selects = root.querySelectorAll('[data-pair-list] select');
    expect(selects).toHaveLength(16);
    const displaced = selects[1].value;
    selects[1].value = selects[2].value;
    selects[1].dispatchEvent(new Event('change', { bubbles: true }));
    expect(selects[2].value).toBe(displaced);
    const teams = JSON.parse(root.querySelector('[data-fixed-teams]').value);
    expect(teams.flat().sort((a, b) => a - b)).toEqual(Array.from({ length: 16 }, (_, i) => i + 1));
    expect(root.querySelector('button[type=submit]').disabled).toBe(false);
  });
  it('explains an odd fixed roster and allows rotating partners for the same players', () => {
    const root = mount(15); fixed(root);
    expect(root.querySelector('button[type=submit]').disabled).toBe(true);
    expect(root.querySelector('[data-setup-error]').textContent).toContain('even number');
    const rotating = root.querySelector('[value=ROTATING_PARTNERS]'); rotating.checked = true;
    rotating.dispatchEvent(new Event('change', { bubbles: true }));
    expect(root.querySelector('button[type=submit]').disabled).toBe(false);
    expect(root.querySelector('[data-setup-summary]').textContent).toContain('3 sitting out');
  });
  it('adds new joins on refresh while preserving the selected pairs', () => {
    const root = mount(16); fixed(root);
    const original = JSON.parse(root.querySelector('[data-fixed-teams]').value);
    const fresh = new DOMParser().parseFromString(preparation(18, 'two'), 'text/html');
    expect(window.FHPB.SessionMatchups.sync(fresh)).toBe(true);
    const teams = JSON.parse(document.querySelector('[data-fixed-teams]').value);
    expect(teams.slice(0, 8)).toEqual(original);
    expect(teams[8]).toEqual([17, 18]);
    expect(document.querySelector('button[type=submit]').disabled).toBe(false);
  });
  it('preserves the organizer scoring choice when another player joins', () => {
    const root = mount(4);
    root.querySelector('[name=organizerScored][value=true]').checked = true;
    const fresh = new DOMParser().parseFromString(preparation(5, 'two'), 'text/html');
    expect(window.FHPB.SessionMatchups.sync(fresh)).toBe(true);
    expect(document.querySelector('[name=organizerScored][value=true]').checked).toBe(true);
  });
  it('opens corrections with the saved score and version, then resets for a new game', () => {
    document.body.innerHTML = `<section data-session-matchups-root><div data-game-id="7"><div data-team-a><span>Alex</span><span>Sam</span></div><div data-team-b><span>Morgan</span><span>Jamie</span></div></div>
      <button data-log-matchup data-entry-id="7" data-round="2" data-correct-score="true" data-score-a="11" data-score-b="8" data-version="3">Correct</button>
      <button data-log-matchup data-entry-id="7" data-round="2">New</button>
      <div id="matchupScoreModal"><h2 id="matchupScoreTitle"></h2><form data-matchup-score-form action="/groups/1/matchups/2/games/0/score"><span data-score-round></span><span data-score-team-a></span><span data-score-team-b></span><input name="scoreA"><input name="scoreB"><input name="expectedVersion"><button type="submit">Save</button></form></div></section>`;
    window.bootstrap = { Modal: { getOrCreateInstance: () => ({ show() {} }) } };
    document.querySelector('[data-correct-score]').click();
    const form = document.querySelector('form');
    expect(form.action).toContain('/games/7/correct');
    expect(form.elements.scoreB.value).toBe('8');
    expect(form.elements.expectedVersion.value).toBe('3');
    document.querySelectorAll('[data-log-matchup]')[1].click();
    expect(form.action).toContain('/games/7/score');
    expect(form.elements.scoreB.value).toBe('');
    expect(form.elements.expectedVersion.value).toBe('');
  });
  it('does not replace the session content while a score dialog is open', () => {
    const root = mount(16);
    document.body.classList.add('modal-open');
    const fresh = new DOMParser().parseFromString(preparation(17, 'two'), 'text/html');
    expect(window.FHPB.SessionMatchups.sync(fresh)).toBe(false);
    expect(document.querySelector('[data-session-matchups-root]')).toBe(root);
  });
  it('refreshes late joins while preserving the selected round and keyboard focus', () => {
    document.body.innerHTML = '<section data-session-matchups-root data-revision="one"><select data-matchup-round><option value="1">1</option><option value="3" selected>3</option></select></section>';
    document.querySelector('select').focus();
    const fresh = new DOMParser().parseFromString(document.body.innerHTML.replace('one', 'two'), 'text/html');
    expect(window.FHPB.SessionMatchups.sync(fresh)).toBe(true);
    expect(document.querySelector('select').value).toBe('3');
    expect(document.activeElement).toBe(document.querySelector('select'));
  });
});

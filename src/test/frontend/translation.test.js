import fs from 'node:fs';
import { JSDOM } from 'jsdom';
import { afterEach, describe, expect, it } from 'vitest';

const source = fs.readFileSync('src/main/resources/static/js/translation.js', 'utf8');
let dom;

function page(content = '', enabled = true) {
  dom = new JSDOM(`<!doctype html><body>${content}${enabled ? `
    <section class="notranslate">
      <div data-translation-widget><select disabled data-translation-placeholder><option>English</option></select></div>
      <small data-translation-status hidden></small>
    </section>` : ''}</body>`, { runScripts: 'outside-only', url: 'http://localhost:8090' });
  dom.window.eval(source);
  return dom.window.document;
}

afterEach(() => dom?.window.close());

describe('optional automatic display translation', () => {
  it('does not contact the translation provider when the feature is absent', () => {
    const document = page('<h1>Play with friends</h1>', false);
    expect(document.querySelector('script')).toBeNull();
    expect(dom.window.gtranslateSettings).toBeUndefined();
  });

  it('protects player identities in initial and replaced match rows while allowing statuses to translate', async () => {
    const document = page(`<div data-match-row><table><tbody><tr><td><div>CoachYoung</div></td></tr>
      <tr class="match-row-divider"><td><div>Waiting on opponents</div></td></tr></tbody></table></div>`);
    expect(document.querySelector('[data-match-row] tr:first-child div').getAttribute('translate')).toBe('no');
    expect(document.querySelector('.match-row-divider div').hasAttribute('translate')).toBe(false);

    const fresh = document.createElement('div');
    fresh.setAttribute('data-match-row', '');
    fresh.innerHTML = '<table><tbody><tr><td><div>Other1</div></td></tr></tbody></table><p>Confirmed</p>';
    document.querySelector('[data-match-row]').replaceWith(fresh);
    await Promise.resolve();

    expect(fresh.querySelector('td div').classList.contains('notranslate')).toBe(true);
    expect(fresh.querySelector('p').hasAttribute('translate')).toBe(false);
  });

  it('protects dynamically created guest names and pair options without changing player IDs or scores', async () => {
    const document = page('<div data-pair-list></div><span data-score-team-a></span><input name="scoreA" value="11">');
    document.querySelector('[data-pair-list]').innerHTML = '<select><option value="">Choose player</option><option value="42">Rose Hill</option></select>';
    document.querySelector('[data-score-team-a]').textContent = 'Rose Hill + CoachYoung';
    await Promise.resolve();

    expect(document.querySelector('option[value="42"]').getAttribute('translate')).toBe('no');
    expect(document.querySelector('option[value=""]').hasAttribute('translate')).toBe(false);
    expect(document.querySelector('[data-score-team-a]').getAttribute('translate')).toBe('no');
    expect(document.querySelector('[name=scoreA]').value).toBe('11');
  });

  it('keeps an English fallback if the provider fails and replaces the placeholder only when ready', async () => {
    const document = page('<h1>Start a session</h1>');
    const provider = document.querySelector('script');
    expect(document.querySelector('[data-translation-placeholder]')).not.toBeNull();
    provider.dispatchEvent(new dom.window.Event('error'));
    expect(document.querySelector('h1').textContent).toBe('Start a session');
    expect(document.querySelector('[data-translation-status]').hidden).toBe(false);
    expect(document.querySelector('[data-translation-placeholder]')).not.toBeNull();
  });

  it('labels the loaded selector and removes the disabled placeholder without adding a second visible control', async () => {
    const document = page();
    const select = document.createElement('select');
    select.className = 'gt_selector';
    document.querySelector('[data-translation-widget]').append(select);
    await Promise.resolve();

    expect(document.querySelectorAll('select')).toHaveLength(1);
    expect(select.id).toBe('app-language-select');
    expect(select.disabled).toBe(false);
  });
});

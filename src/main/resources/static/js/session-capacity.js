(function () {
  const panel = document.querySelector('[data-session-capacity]');
  if (!panel) return;
  const list = panel.querySelector('[data-waitlist]');
  const empty = panel.querySelector('[data-waitlist-empty]');
  const limit = panel.querySelector('[name="memberLimit"]');
  const joined = panel.querySelector('[data-joined-player-count]');
  let loading = false;
  async function refresh() {
    if (loading || document.hidden) return;
    loading = true;
    try {
      const options = { headers: { Accept: 'application/json' }, credentials: 'same-origin' };
      const [response, rosterResponse] = await Promise.all([
        fetch('/api/sessions/' + panel.dataset.sessionId + '/waitlist', options),
        fetch('/api/sessions/' + panel.dataset.sessionId + '/members', options)
      ]);
      if (rosterResponse.ok) {
        const roster = await rosterResponse.json();
        if (limit) limit.min = String(Math.max(1, roster.memberCount));
        if (joined) joined.textContent = roster.memberCount + (roster.memberCount === 1 ? ' player joined.' : ' players joined.');
      }
      if (!response.ok) return;
      const requests = await response.json();
      const items = requests.map(function (request) {
        const item = document.createElement('li');
        item.className = 'list-group-item';
        const player = document.createElement('span');
        player.className = 'session-player-name notranslate';
        player.setAttribute('translate', 'no');
        const name = document.createElement('span');
        name.setAttribute('data-player-name', '');
        name.textContent = request.requesterDisplayName;
        player.appendChild(name);
        if (request.requesterPublicCode) {
          const code = document.createElement('small');
          code.className = 'session-player-id text-muted fw-normal notranslate';
          code.setAttribute('translate', 'no');
          code.textContent = request.requesterPublicCode;
          player.appendChild(code);
        }
        item.appendChild(player);
        return item;
      });
      list.replaceChildren(...items);
      empty.hidden = items.length > 0;
    } catch (_) {
      // Keep the last known queue when the network is unavailable.
    } finally {
      loading = false;
    }
  }
  refresh();
  window.setInterval(refresh, 5000);
})();

(function (window, document) {
  'use strict';
  window.FHPB = window.FHPB || {};
  var api = {
    mount: function (root) {
      if (!root) return;
      var setup = root.querySelector('[data-matchups-setup]');
      if (setup && !setup.dataset.mounted) {
        setup.dataset.mounted = 'true';
        var members = JSON.parse(setup.querySelector('[data-matchup-members]').textContent);
        var pairList = setup.querySelector('[data-pair-list]');
        var hiddenTeams = setup.querySelector('[data-fixed-teams]');
        var pairs = [];
        try { pairs = JSON.parse(setup.dataset.savedPairs || '[]'); } catch (_) { /* start fresh */ }
        var assigned = new Set();
        pairs = pairs.map(function (pair) {
          return pair.filter(function (id) {
            if (!members.some(function (m) { return m.id === id; }) || assigned.has(id)) return false;
            assigned.add(id); return true;
          });
        });
        var remaining = members.filter(function (m) { return !assigned.has(m.id); }).map(function (m) { return m.id; });
        pairs.forEach(function (pair) { while (pair.length < 2 && remaining.length) pair.push(remaining.shift()); });
        while (remaining.length) pairs.push(remaining.splice(0, 2));
        pairs.filter(function (pair) { return pair.length; }).forEach(function (pair, index) {
          var row = document.createElement('div'); row.className = 'matchups-pair';
          [0, 1].forEach(function (side) {
            if (side) { var plus = document.createElement('span'); plus.textContent = '+'; plus.setAttribute('aria-hidden', 'true'); row.appendChild(plus); }
            var select = document.createElement('select'); select.className = 'form-select';
            select.setAttribute('aria-label', 'Pair ' + (index + 1) + ', player ' + (side + 1));
            select.add(new Option('Choose player', ''));
            members.forEach(function (m) { select.add(new Option(m.name, String(m.id))); });
            select.value = pair[side] == null ? '' : String(pair[side]);
            select.dataset.previousValue = select.value;
            select.addEventListener('change', function () {
              if (select.value) pairList.querySelectorAll('select').forEach(function (other) {
                if (other !== select && other.value === select.value) {
                  other.value = select.dataset.previousValue;
                  other.dataset.previousValue = other.value;
                }
              });
              select.dataset.previousValue = select.value;
              update();
            });
            row.appendChild(select);
          });
          pairList.appendChild(row);
        });
        function update() {
          var fixed = setup.querySelector('[name=format]:checked').value === 'FIXED_TEAMS';
          setup.querySelector('[data-fixed-pairs]').hidden = !fixed;
          setup.querySelector('[data-rotating-rounds]').hidden = fixed;
          var count = members.length, rounds = Number(setup.querySelector('[name=rounds]').value);
          var error = '', teams = [];
          if (fixed) {
            if (count % 2) error = 'Fixed partners need an even number of players. Invite one more player or mix partners instead.';
            var ids = [];
            pairList.querySelectorAll('.matchups-pair').forEach(function (row) {
              var team = Array.from(row.querySelectorAll('select')).map(function (s) { return Number(s.value); });
              teams.push(team); ids = ids.concat(team);
            });
            if (!error && (ids.some(function (id) { return !id; }) || new Set(ids).size !== count)) error = 'Choose each player once so everyone has a partner.';
            hiddenTeams.value = JSON.stringify(teams);
            var teamCount = count / 2;
            rounds = teamCount % 2 ? teamCount : teamCount - 1;
          } else {
            hiddenTeams.value = '';
            if (!Number.isInteger(rounds) || rounds < 1 || rounds > 30) error = 'Choose between 1 and 30 rounds.';
          }
          if (count < 4) error = 'Invite at least four players to start matchups.';
          var summary = count + ' players · ' + Math.floor(count / 4) + ' games at once · ' + Math.max(0, rounds) + ' rounds';
          summary += count % 4 === 0 ? ' · everyone plays each round' : ' · ' + (count % 4) + ' sitting out each round';
          setup.querySelector('[data-setup-summary]').textContent = summary;
          var errorNode = setup.querySelector('[data-setup-error]'); errorNode.hidden = !error; errorNode.textContent = error;
          setup.querySelector('button[type=submit]').disabled = !!error;
        }
        setup.addEventListener('change', update);
        setup.querySelector('[name=rounds]').addEventListener('input', update);
        update();
      }
      var roundSelect = root.querySelector('[data-matchup-round]');
      if (roundSelect) {
        roundSelect.value = root.dataset.selectedRound || root.dataset.initialRound;
        if (!roundSelect.value) roundSelect.value = 'all';
      }
      api.filter(root);
    },
    filter: function (root) {
      var select = root.querySelector('[data-matchup-round]');
      if (!select) return;
      var mine = root.dataset.gamesFilter === 'mine';
      root.querySelectorAll('[data-games-filter]').forEach(function (button) {
        var chosen = button.dataset.gamesFilter === (mine ? 'mine' : 'all');
        button.classList.toggle('is-selected', chosen); button.setAttribute('aria-pressed', String(chosen));
      });
      root.querySelectorAll('.matchups-round').forEach(function (round) {
        round.hidden = select.value !== 'all' && round.dataset.round !== select.value;
        var visible = 0;
        round.querySelectorAll('[data-game-id]').forEach(function (game) {
          game.hidden = mine && game.dataset.mine !== 'true'; if (!game.hidden) visible++;
        });
        round.querySelector('.matchups-no-personal-game').hidden = !mine || visible > 0;
      });
    },
    sync: function (freshScope) {
      var current = document.querySelector('[data-session-matchups-root]');
      var fresh = freshScope.querySelector('[data-session-matchups-root]');
      if (!current || !fresh || current.dataset.revision === fresh.dataset.revision) return false;
      // Refresh live rosters and results without interrupting score entry or replacing a form
      // while somebody is using a keyboard. CSRF masking is intentionally not part of revision.
      if (document.body.classList.contains('modal-open') || document.querySelector('.modal.show') || (current.contains(document.activeElement)
          && /^(INPUT|SELECT|TEXTAREA)$/.test(document.activeElement.tagName)
          && !document.activeElement.matches('[data-matchup-round]'))) return false;
      fresh.dataset.gamesFilter = current.dataset.gamesFilter || 'all';
      var picker = current.querySelector('[data-matchup-round]');
      var roundPickerFocused = picker && picker === document.activeElement;
      if (picker) fresh.dataset.selectedRound = picker.value;
      var oldSetup = current.querySelector('[data-matchups-setup]'), newSetup = fresh.querySelector('[data-matchups-setup]');
      if (oldSetup && newSetup) {
        newSetup.querySelector('[name=rounds]').value = oldSetup.querySelector('[name=rounds]').value;
        var format = oldSetup.querySelector('[name=format]:checked').value;
        newSetup.querySelector('[name=format][value="' + format + '"]').checked = true;
        newSetup.dataset.savedPairs = oldSetup.querySelector('[data-fixed-teams]').value || '[]';
        var scoring = oldSetup.querySelector('[name=organizerScored]:checked');
        if (scoring && newSetup.querySelector('[name=organizerScored][value="' + scoring.value + '"]')) newSetup.querySelector('[name=organizerScored][value="' + scoring.value + '"]').checked = true;
      }
      var openOptions = current.querySelector('.matchups-options[open]');
      if (openOptions && fresh.querySelector('.matchups-options')) fresh.querySelector('.matchups-options').open = true;
      if (current.querySelector('[data-matchup-roster][open]') && fresh.querySelector('[data-matchup-roster]')) fresh.querySelector('[data-matchup-roster]').open = true;
      current.replaceWith(fresh); api.mount(fresh);
      if (roundPickerFocused) fresh.querySelector('[data-matchup-round]').focus({ preventScroll: true });
      return true;
    }
  };
  window.FHPB.SessionMatchups = api;
  document.addEventListener('DOMContentLoaded', function () { api.mount(document.querySelector('[data-session-matchups-root]')); });
  document.addEventListener('shown.bs.modal', function (event) {
    if (event.target.id === 'matchupScoreModal') event.target.querySelector('[name=scoreA]').focus();
  });
  function updateIntentScoring() {
    var choice = document.querySelector('[data-organizer-scoring-choice]');
    var selected = document.querySelector('[name=playMode]:checked');
    if (!choice || !selected) return;
    choice.hidden = selected.value !== 'MATCHUPS';
    choice.querySelector('input').disabled = choice.hidden;
  }
  function updateSessionDate() {
    var choice = document.querySelector('[data-session-date-choice]');
    if (!choice) return;
    var later = document.querySelector('[name=sessionWhen]:checked');
    later = later && later.value === 'LATER';
    choice.hidden = !later;
    var input = choice.querySelector('input');
    input.disabled = !later;
    input.required = !!later;
  }
  document.addEventListener('DOMContentLoaded', function () {
    updateIntentScoring();
    updateSessionDate();
  });
  document.addEventListener('change', function (event) {
    if (event.target.matches('[name=sessionWhen]')) updateSessionDate();
    if (event.target.matches('[name=playMode]')) updateIntentScoring();
    var root = event.target.closest('[data-session-matchups-root]');
    if (!root) return;
    if (event.target.matches('[data-matchup-round]')) api.filter(root);
    if (event.target.matches('[name=replacementId]')) {
      var guest = root.querySelector('[name=guestName]');
      guest.disabled = !!event.target.value; guest.required = !event.target.value;
      if (event.target.value) guest.value = '';
    }
  });
  document.addEventListener('click', function (event) {
    if (event.target.closest('[data-open-matchup-roster]')) {
      var roster = document.querySelector('[data-matchup-roster]');
      if (roster) roster.open = true;
    }
    var invite = event.target.closest('[data-open-session-invite]');
    if (invite) {
      var panel = document.querySelector('#session-invite-panel .collapse');
      if (panel && window.bootstrap) window.bootstrap.Collapse.getOrCreateInstance(panel, { toggle: false }).show();
    }
    var root = event.target.closest('[data-session-matchups-root]');
    if (!root) return;
    var filter = event.target.closest('[data-games-filter]');
    if (filter) { root.dataset.gamesFilter = filter.dataset.gamesFilter; api.filter(root); }
    var score = event.target.closest('[data-log-matchup]');
    if (score) {
      var game = root.querySelector('[data-game-id="' + score.dataset.entryId + '"]');
      var form = root.querySelector('[data-matchup-score-form]'); form.reset();
      var correcting = score.dataset.correctScore === 'true';
      form.action = form.action.replace(/\/games\/\d+\/(score|correct)$/, '/games/' + score.dataset.entryId + (correcting ? '/correct' : '/score'));
      form.querySelector('[name=scoreA]').value = correcting ? score.dataset.scoreA : '';
      form.querySelector('[name=scoreB]').value = correcting ? score.dataset.scoreB : '';
      form.querySelector('[name=expectedVersion]').value = correcting ? score.dataset.version : '';
      root.querySelector('#matchupScoreTitle').textContent = correcting ? 'Correct score' : 'Enter score';
      form.querySelector('button[type=submit]').textContent = correcting ? 'Save correction' : 'Save score';
      form.querySelector('[data-score-round]').textContent = 'Round ' + score.dataset.round;
      ['a', 'b'].forEach(function (team) {
        form.querySelector('[data-score-team-' + team + ']').textContent = Array.from(game.querySelectorAll('[data-team-' + team + '] span')).map(function (s) { return s.textContent; }).join(' + ');
      });
      window.bootstrap.Modal.getOrCreateInstance(root.querySelector('#matchupScoreModal')).show();
    }
    var replace = event.target.closest('[data-replace-player]');
    if (replace) {
      var replacementForm = root.querySelector('[data-matchup-replace-form]'); replacementForm.reset();
      replacementForm.querySelector('[name=slotId]').value = replace.dataset.slotId;
      replacementForm.querySelector('[data-replacement-description]').textContent = 'Who is stepping in for ' + replace.dataset.playerName + '?';
      var guestInput = replacementForm.querySelector('[name=guestName]'); guestInput.disabled = false; guestInput.required = true;
      window.bootstrap.Modal.getOrCreateInstance(root.querySelector('#matchupReplaceModal')).show();
    }
  });
})(window, document);

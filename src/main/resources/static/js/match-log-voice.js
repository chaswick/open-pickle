// match-log-voice.js
// File-scoped module for Log Match by Voice (Ladder V2)
document.addEventListener('DOMContentLoaded', function() {
  // Configurable selectors
  const btn = document.getElementById('logMatchVoiceBtn');
  const label = document.getElementById('logMatchVoiceBtnLabel');
  const spinner = document.getElementById('logMatchVoiceSpinner');
  const caption = document.getElementById('logMatchVoiceCaption');
  // (no debug) runtime behavior — keep JS minimal in production
  // Prefer the global toast container if present; fall back to legacy per-page container.
  let toastContainer = document.querySelector('#globalToastContainer') || document.querySelector('.toast-container-mid');
  if (!btn || !label || !spinner || !caption) return;
  // If there's no existing toast container (this script may run on pages without the interpret UI),
  // create a lightweight fallback so toasts still show.
  if (!toastContainer) {
    // If global container appeared after initial check, use it; otherwise create a small fallback.
    const globalNow = document.querySelector('#globalToastContainer');
    if (globalNow) {
      toastContainer = globalNow;
    } else {
      toastContainer = document.createElement('div');
      toastContainer.className = 'toast-container toast-container-mid toast-overlay';
      document.body.appendChild(toastContainer);
    }
  }

  function csrfHeaders(baseHeaders) {
    return window.FHPB.Csrf.headers(baseHeaders);
  }

  // Ladder/Season
  const ladderId = document.getElementById('ladderId')?.value;
  const seasonId = document.getElementById('seasonId')?.value;

  // Default caption for the voice helper when server doesn't render one
  const DEFAULT_VOICE_CAPTION = 'Say something like, "Pam and I beat Ryan and Amy 11-8"';

  // Endpoint
  const INTERPRET_URL = '/voice-match-log/interpret';
  const VOICE_REVIEW_URL = '/log-match';

  const DEFAULT_LANGUAGE = 'en-US';
  const configuredLanguage = (window.matchLogConfig && typeof window.matchLogConfig.language === 'string')
    ? window.matchLogConfig.language.trim()
    : '';
  let lang = configuredLanguage || navigator.language || DEFAULT_LANGUAGE;
  const maxAlternatives = (() => {
    const raw = Number(window.matchLogConfig?.maxAlternatives);
    if (!Number.isFinite(raw)) return 3;
    return Math.max(1, Math.min(5, Math.round(raw)));
  })();
  const grammarHints = Array.isArray(window.matchLogConfig?.phraseHints)
    ? window.matchLogConfig.phraseHints
        .map(h => typeof h === 'string' ? h.trim() : '')
        .filter(Boolean)
    : [];
  const reviewParams = (window.matchLogConfig && typeof window.matchLogConfig.reviewParams === 'object' && window.matchLogConfig.reviewParams !== null)
    ? window.matchLogConfig.reviewParams
    : null;
  const SpeechGrammarList = window.SpeechGrammarList || window.webkitSpeechGrammarList || null;
  const recognitionGrammar = buildRecognitionGrammar(grammarHints);

  // State
  let recognition, recognizing = false, armed = false, interim = '', final = '', timeoutId = null, firstUse = true;
  // Inactivity timeout (ms) - how long of silence before we stop recognition gracefully
  const inactivityTimeoutMs = window.matchLogConfig?.speechTimeoutMs || 6000;

  // Accessibility
  btn.setAttribute('aria-pressed', 'false');
  // Keep the visible label visually hidden so the button shows only the microphone icon.
  // Preserve an accessible name on the button for screen readers.
  if (label && !label.classList.contains('visually-hidden')) {
    label.classList.add('visually-hidden');
  }
  btn.setAttribute('aria-label', 'Log Match by Voice');

  // If server didn't render a helpful caption, populate a sensible default for users.
  if (caption && (!caption.textContent || caption.textContent.trim() === '')) {
    caption.textContent = DEFAULT_VOICE_CAPTION;
  }

  // Observe mutations and attribute changes so we can restore the caption if
  // it's cleared/hidden by other scripts during initial render.
  const captionObserver = new MutationObserver((mutations) => {
    try {
      let c = document.getElementById('logMatchVoiceCaption');
      // If caption was removed, try to recreate it near the voice button.
      if (!c) {
        const buttonWrapper = btn.closest('.position-relative') || btn.parentElement;
        const recreated = document.createElement('div');
        recreated.id = 'logMatchVoiceCaption';
        recreated.className = 'text-center mt-2 mb-2 text-muted d-none';
        recreated.setAttribute('aria-live', 'polite');
        recreated.textContent = DEFAULT_VOICE_CAPTION;
        if (buttonWrapper && buttonWrapper.parentElement) {
          buttonWrapper.insertAdjacentElement('afterend', recreated);
        } else {
          document.body.appendChild(recreated);
        }
        c = recreated;
      }
      // Don't interfere with intentional hiding via d-none class
      // Only restore text content when visible and empty
      const txt = c.textContent ? c.textContent.trim() : '';
      if (!recognizing && !c.classList.contains('d-none') && (!txt || txt === '')) {
        c.textContent = DEFAULT_VOICE_CAPTION;
      }
    } catch (e) { /* ignore */ }
  });
  try {
    captionObserver.observe(document.body, { childList: true, characterData: true, attributes: true, attributeFilter: ['class', 'style'], subtree: true });
  } catch (e) { /* ignore */ }

  // Short-lived retry loop to cover rapid race conditions right after load.
  (function transientRestore() {
    let attempts = 0;
    const maxAttempts = 30; // ~3 seconds at 100ms
    const interval = setInterval(() => {
      try {
        let c = document.getElementById('logMatchVoiceCaption');
        if (!c) return;
        // Only restore text when caption is visible (not d-none)
        const txt = c.textContent ? c.textContent.trim() : '';
        if (!recognizing && !c.classList.contains('d-none') && (!txt || txt === '')) {
          c.textContent = DEFAULT_VOICE_CAPTION;
        }
      } catch (e) { /* ignore */ }
      attempts++;
      if (attempts >= maxAttempts) clearInterval(interval);
    }, 100);
  })();

  // Keep behavior minimal: server renders the caption and JS only sets a default
  // if it's empty. The template uses a CSS data-attr fallback so the helper text
  // remains visible even if other scripts clear innerText.

  // Feature detect
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) {
    btn.style.display = 'none';
    caption.style.display = 'none';
    return;
  }

  // Tooltip for permissions
  function showTooltip(msg) {
    btn.title = msg;
    // Announce permission/tooltip text via aria-label for screen readers.
    btn.setAttribute('aria-label', msg);
    setTimeout(() => {
      btn.title = '';
      btn.setAttribute('aria-label', 'Log Match by Voice');
    }, 4000);
  }

  // Toast
  function showToast(msg, type = 'danger') {
    let toast = document.createElement('div');
    toast.className = `toast align-items-center border-0 text-bg-${type}`;
    toast.setAttribute('role', 'alert');
    toast.setAttribute('aria-live', 'assertive');
    toast.setAttribute('aria-atomic', 'true');
    toast.innerHTML = `<div class='d-flex'><div class='toast-body'>${msg}</div><button type='button' class='btn-close me-2 m-auto btn-close-white' data-bs-dismiss='toast' aria-label='Close'></button></div>`;
    // ensure toastContainer exists (double-check global before creating fallback)
    if (!toastContainer) {
      const globalNow = document.querySelector('#globalToastContainer');
      if (globalNow) {
        toastContainer = globalNow;
      } else {
        toastContainer = document.createElement('div');
        toastContainer.className = 'toast-container toast-container-mid toast-overlay';
        document.body.appendChild(toastContainer);
      }
    }
    toastContainer.appendChild(toast);
    new bootstrap.Toast(toast).show();
    setTimeout(() => toast.remove(), 2000);
  }

  function buildRecognitionGrammar(hints) {
    if (!SpeechGrammarList || !Array.isArray(hints) || hints.length === 0) {
      return null;
    }
    try {
      const clean = hints
        .map(h => h.replace(/[\r\n]+/g, ' '))
        .map(h => h.replace(/["]/g, '\\"'))
        .map(h => h.replace(/[;=]/g, ' '))
        .filter(Boolean);
      if (!clean.length) return null;
      const clauses = clean.map(h => `"${h}"`).join(' | ');
      const grammar = `#JSGF V1.0; grammar phrases; public <phrase> = ${clauses} ;`;
      const list = new SpeechGrammarList();
      list.addFromString(grammar, 1);
      return list;
    } catch (err) {
      console.debug('Failed to build speech grammar', err);
      return null;
    }
  }

  // State machine
  function setState(state) {
    switch(state) {
      case 'idle':
        recognizing = false;
        // Toggle both Bootstrap default classes and our action classes so styling
        // remains consistent whether templates use 'btn-success' or 'btn-action-success'.
        btn.classList.remove('btn-danger','pulse','btn-warning','disabled','btn-action-danger','btn-action-warning');
        btn.classList.add('btn-success','btn-action-success');
        btn.setAttribute('aria-pressed','false');
  // Ensure the visible label remains hidden; update accessible name to reflect idle state.
  if (label && !label.classList.contains('visually-hidden')) label.classList.add('visually-hidden');
  btn.setAttribute('aria-label', 'Log Match by Voice');
        spinner.classList.add('d-none');
        // Preserve any placeholder text rendered server-side. Only clear captions when
        // actively starting to listen or when we intentionally replace with a transcript.
        // If there's no transcript/content, restore the default helper caption so the UI
        // doesn't collapse (CSS :empty hides the element).
        if (caption && (!caption.textContent || caption.textContent.trim() === '')) {
          caption.textContent = DEFAULT_VOICE_CAPTION;
        }
        break;
      case 'listening':
        recognizing = true;
        // Add both default and action-specific danger classes so CSS picks up
        btn.classList.remove('btn-success','btn-warning','disabled','btn-action-success');
        btn.classList.add('btn-danger','pulse','btn-action-danger');
        btn.setAttribute('aria-pressed','true');
  // Keep label hidden; use aria-label for screen reader state.
  if (label && !label.classList.contains('visually-hidden')) label.classList.add('visually-hidden');
  btn.setAttribute('aria-label', 'Listening');
        spinner.classList.add('d-none');
    // Keep the placeholder text initially; it will be replaced when user starts speaking
        break;
      case 'processing':
      // We're no longer actively listening when processing starts
      recognizing = false;
  btn.classList.remove('btn-success','btn-danger','pulse','btn-action-success','btn-action-danger');
  btn.classList.add('btn-warning','disabled','btn-action-warning');
      btn.setAttribute('aria-pressed','true');
  // Keep label hidden; use aria-label for screen reader state.
  if (label && !label.classList.contains('visually-hidden')) label.classList.add('visually-hidden');
  btn.setAttribute('aria-label', 'Processing');
      spinner.classList.remove('d-none');
        break;
    }
  }

  // Timeout helpers
  function clearTimeouts() {
    if (timeoutId) clearTimeout(timeoutId);
    timeoutId = null;
  }
  function startTimeout(ms, cb) {
    clearTimeouts();
    timeoutId = setTimeout(cb, ms);
  }

  // Start recognition
  function startRecognition() {
    if (recognizing) return;
    if (!recognition) {
      recognition = new SpeechRecognition();
      recognition.continuous = false;
      recognition.interimResults = true;
      recognition.lang = lang;
      if (recognitionGrammar) {
        try {
          recognition.grammars = recognitionGrammar;
        } catch (e) {
          console.debug('Unable to attach recognition grammar', e);
        }
      }
      if (maxAlternatives) {
        try {
          recognition.maxAlternatives = maxAlternatives;
        } catch (e) {
          console.debug('Unable to set maxAlternatives', e);
        }
      }
    }
    interim = '';
    final = '';
    setState('listening');
    armed = false;
    recognition.onstart = () => {
      armed = true;
      setState('listening');
      // Start inactivity timeout; use graceful stop so we get final transcript when possible
      startTimeout(inactivityTimeoutMs, stopRecognitionGraceful);
    };
    recognition.onresult = (event) => {
      interim = '';
      for (let i = event.resultIndex; i < event.results.length; ++i) {
        let transcript = event.results[i][0].transcript;
        if (event.results[i].isFinal) {
          final += transcript;
        } else {
          interim += transcript;
        }
      }
      // Replace placeholder with transcript when user starts speaking
      const currentTranscript = interim || final;
      if (currentTranscript) {
        caption.textContent = currentTranscript;
      }
      // Reset inactivity timeout on each result (user is speaking)
      startTimeout(inactivityTimeoutMs, stopRecognitionGraceful);
      if (final) {
        clearTimeouts();
        // Stop the recognition to release the mic immediately — we'll process the final transcript
        try { recognition.stop(); } catch (e) { /* ignore if already stopping */ }
        setState('processing');
        submitInterpret(final);
      }
    };
    recognition.onerror = (event) => {
      let msg = '';
      // Always emit a debug log so we can correlate aborted recognition with server-side errors
      console.debug('SpeechRecognition.onerror', event && event.error, event);
      switch(event.error) {
        case 'not-allowed':
        case 'service-not-allowed':
          msg = 'Microphone access denied.'; break;
        case 'no-speech':
          msg = 'No speech detected.'; break;
        case 'aborted':
          msg = 'Voice input aborted.'; break;
        case 'network':
          msg = 'Network error.'; break;
        default:
          msg = 'Speech recognition error.';
      }
      showToast(msg);
      setState('idle');
    };
    recognition.onend = () => {
      // If there was no transcript at all, revert to idle state; otherwise we're likely processing.
      if (!final && !interim) {
        setState('idle');
      }
      clearTimeouts();
      // Clear the recognition handler so a fresh instance is created on next start and the mic is freed.
      try { recognition = null; } catch (e) { recognition = null; }
    };
    startTimeout(4000, () => {
      if (!armed) abortRecognition(); // start timeout
    });
    recognition.start();
  }

  function abortRecognition() {
    if (recognition && recognizing) {
      // User-initiated abort: cancel immediately
      try { recognition.abort(); } catch (e) { /* ignore */ }
      setState('idle');
    }
    clearTimeouts();
  }

  // Stop recognition gracefully (allow browser to emit final result if possible)
  function stopRecognitionGraceful() {
    if (recognition && recognizing) {
      try { recognition.stop(); } catch (e) { /* ignore */ }
    }
    clearTimeouts();
  }

  function appendParams(params, extraParams) {
    if (!(params instanceof URLSearchParams) || !extraParams || typeof extraParams !== 'object') {
      return;
    }
    Object.entries(extraParams).forEach(([key, value]) => {
      const cleanKey = typeof key === 'string' ? key.trim() : '';
      if (!cleanKey || value === null || value === undefined) {
        return;
      }
      if (typeof value === 'boolean') {
        params.set(cleanKey, value ? 'true' : 'false');
        return;
      }
      const cleanValue = String(value).trim();
      if (cleanValue) {
        params.set(cleanKey, cleanValue);
      }
    });
  }

  function buildContextParams(extraParams) {
    const params = new URLSearchParams();
    if (ladderId) params.set('ladderId', ladderId);
    if (seasonId) params.set('seasonId', seasonId);
    appendParams(params, extraParams);
    return params;
  }

  function redirectToVoiceReview(result) {
    const interpretation = result && typeof result === 'object'
      && Object.prototype.hasOwnProperty.call(result, 'interpretation')
      ? result.interpretation
      : result;
    if (!interpretation || typeof interpretation !== 'object') {
      showToast('I could not turn that into a reviewable match. Please try again.', 'warning');
      setState('idle');
      return;
    }
    try {
      window.localStorage.setItem('fhpb_voice_interpretation', JSON.stringify(interpretation));
    } catch (err) {
      console.warn('Failed to store voice interpretation for review', err);
      showToast('Could not keep that voice result. Please try again.', 'warning');
      setState('idle');
      return;
    }
    const params = buildContextParams(reviewParams);
    params.set('voiceReview', '1');
    window.location.replace(VOICE_REVIEW_URL + (params.toString() ? '?' + params.toString() : ''));
  }

  // Submit transcript
  async function submitInterpret(transcript) {
    setState('processing');
    caption.textContent = transcript;
    try {
      const payload = {
        transcript: transcript,
        ladderConfigId: ladderId ? Number(ladderId) : null,
        seasonId: seasonId ? Number(seasonId) : null,
        language: lang
      };
      const res = await fetch(INTERPRET_URL, {
        method: 'POST',
        headers: csrfHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify(payload)
      });
      if (!res.ok) {
        let text = await res.text();
        // Log full response for debugging (server error bodies may include stack traces or error codes)
        console.error('Interpret endpoint returned', res.status, res.statusText, text);
        let msg = 'Server error interpreting speech; see console for details.';
        try {
          let json = JSON.parse(text);
          msg = json.message || json.error || msg;
          // If server provided a correlation id, show it in the toast so phone users can note it
          if (json.correlationId) {
            msg += ' (ref: ' + json.correlationId + ')';
          }
          // Include exception class if available for faster debugging
          if (json.exception) {
            msg += ' [' + json.exception + ']';
          }
        } catch (e) {
          // non-json body — keep generic message
        }
        // Show debug toast slightly longer so users have time to note the ref id
        showToast(msg, 'danger');
        setState('idle');
        return;
      }
      let result = await res.json();
      // If autoSubmitted, redirect
      if (result && result.autoSubmitted === true) {
        const baseUrl = (window.matchLogConfig && typeof window.matchLogConfig.postMatchLoggedBaseUrl === 'string' && window.matchLogConfig.postMatchLoggedBaseUrl.trim())
          ? window.matchLogConfig.postMatchLoggedBaseUrl.trim()
          : '/home';
        let params = buildContextParams();
        params.set('toast', 'matchLogged');
        if (result.matchId) params.set('matchId', result.matchId);
        try { window.localStorage.removeItem('fhpb_voice_interpretation'); } catch (e) { /* ignore */ }
        // Clear any active recognition and replace location so back-button doesn't return to a still-spinning recorder
        try { abortRecognition(); } catch (e) { /* ignore */ }
        clearTimeouts();
        window.location.replace(baseUrl + (params.toString() ? '?' + params.toString() : ''));
        return;
      }
      // Otherwise, continue on the manual review log page instead of bouncing home.
      try { abortRecognition(); } catch (e) { /* ignore */ }
      clearTimeouts();
      redirectToVoiceReview(result);
    } catch (e) {
      // Network or unexpected error — log to console for investigation and show user-friendly toast
      console.error('Failed to call interpret endpoint', e);
      showToast(e);
      setState('idle');
    }
  }

  // When the page is shown (including back/forward cache restores), ensure the voice UI is reset
  window.addEventListener('pageshow', function(event) {
    // If the page was restored from BFCache or navigating back, make sure we abort any lingering recognition
    try {
      if (recognition) {
        try { recognition.abort(); } catch (e) { /* ignore */ }
        recognition = null;
      }
    } catch (e) { /* ignore */ }
    clearTimeouts();
    setState('idle');
  });

  // If the tab becomes hidden (user switches apps), abort recognition to free microphone and avoid stale UI
  document.addEventListener('visibilitychange', function() {
    if (document.hidden) {
      try { abortRecognition(); } catch (e) { /* ignore */ }
      clearTimeouts();
      setState('idle');
    }
  });

  // Button click
  btn.addEventListener('click', function(e) {
    if (recognizing) {
      abortRecognition();
      return;
    }
    // Show the caption when mic button is clicked
    if (caption.classList.contains('d-none')) {
      caption.classList.remove('d-none');
    }
    if (firstUse) {
      showTooltip("We'll ask for your mic—only while you hold/tap.");
      firstUse = false;
    }
    startRecognition();
  });

  // Keyboard shortcut: Shift+Space
  document.addEventListener('keydown', function(e) {
    if (e.shiftKey && e.code === 'Space') {
      btn.click();
      e.preventDefault();
    }
    if (e.code === 'Escape' && recognizing) {
      abortRecognition();
      e.preventDefault();
    }
  });

  // Initial state
  setState('idle');
});

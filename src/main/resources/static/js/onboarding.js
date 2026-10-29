(function () {
  'use strict';

  document.querySelectorAll('[data-onboarding-guide]').forEach(function (guide) {
    const button = guide.querySelector('[data-onboarding-dismiss]');
    if (!button) return;

    button.addEventListener('click', async function () {
      const error = guide.querySelector('[data-onboarding-error]');
      const tour = guide.getAttribute('data-onboarding-tour');
      const headers = tour ? { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' } : {};
      button.disabled = true;
      if (error) error.hidden = true;

      try {
        const response = await window.fetch(guide.getAttribute('data-onboarding-url'), {
          method: 'POST',
          credentials: 'same-origin',
          headers: window.FHPB.Csrf.headers(headers),
          body: tour ? 'tour=' + encodeURIComponent(tour) : undefined
        });
        if (!response.ok || response.redirected) throw new Error('Dismissal failed');
        guide.hidden = true;
      } catch (failure) {
        button.disabled = false;
        if (error) {
          error.textContent = 'Could not save this preference. You can keep playing and try again later.';
          error.hidden = false;
        }
      }
    });
  });
})();

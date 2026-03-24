/*!
 * FHPB Core JavaScript
 * Common utilities and components used across the application
 */

(function(window, document) {
  'use strict';

  // Namespace for FHPB utilities
  window.FHPB = window.FHPB || {};

  /**
   * CSRF Utilities
   */
  FHPB.Csrf = {
    metaContent: function(name) {
      var el = document.querySelector('meta[name="' + name + '"]');
      return el ? el.getAttribute('content') : null;
    },

    token: function() {
      return FHPB.Csrf.metaContent('_csrf');
    },

    headerName: function() {
      return FHPB.Csrf.metaContent('_csrf_header');
    },

    headers: function(baseHeaders) {
      var headers = Object.assign({}, baseHeaders || {});
      var token = FHPB.Csrf.token();
      var headerName = FHPB.Csrf.headerName();
      if (token && headerName) {
        headers[headerName] = token;
      }
      return headers;
    }
  };

  /**
   * Toast Management
   */
  FHPB.Toast = {
    // Auto-show all toasts on page load
    initializeToasts: function() {
      document.addEventListener('DOMContentLoaded', function() {
        var toastElements = document.querySelectorAll('.toast');
        toastElements.forEach(function(toastElement) {
          var toast = new bootstrap.Toast(toastElement);
          toast.show();
        });
      });
    }
  };

  /**
   * Clipboard Utilities
   */
  FHPB.Clipboard = {
    copyToClipboard: function(text, button) {
      return navigator.clipboard.writeText(text || '').then(function() {
        FHPB.Clipboard.showCopySuccess(button);
      }).catch(function(err) {
        console.warn('Copy failed:', err);
        FHPB.Clipboard.showCopyError(button);
      });
    },

    showCopySuccess: function(button) {
      if (!button) return;
      var originalClasses = button.className;
      var originalText = button.textContent;
      
      button.classList.remove('btn-outline-secondary');
      button.classList.add('btn-success');
      button.textContent = 'Copied';
      
      setTimeout(function() {
        button.className = originalClasses;
        button.textContent = originalText;
      }, 2000);
    },

    showCopyError: function(button) {
      if (!button) return;
      var originalText = button.textContent;
      
      button.textContent = 'Error';
      button.classList.add('btn-danger');
      
      setTimeout(function() {
        button.classList.remove('btn-danger');
        button.textContent = originalText;
      }, 2000);
    }
  };

  /**
   * Date/Time Utilities
   */
  FHPB.DateTime = {
    monthDayLabel: function(date, timeZone) {
      var formatter = new Intl.DateTimeFormat('en-US', {
        timeZone: timeZone,
        month: 'short',
        day: 'numeric'
      });
      return formatter.format(date);
    },

    numberWord: function(value) {
      var words = {
        0: 'Zero',
        1: 'One',
        2: 'Two',
        3: 'Three',
        4: 'Four',
        5: 'Five',
        6: 'Six',
        7: 'Seven',
        8: 'Eight',
        9: 'Nine',
        10: 'Ten',
        11: 'Eleven',
        12: 'Twelve'
      };
      return words[value] || String(value);
    },

    dayDifference: function(earlierDate, laterDate) {
      var startEarlier = new Date(earlierDate.getFullYear(), earlierDate.getMonth(), earlierDate.getDate());
      var startLater = new Date(laterDate.getFullYear(), laterDate.getMonth(), laterDate.getDate());
      return Math.round((startLater.getTime() - startEarlier.getTime()) / 86400000);
    },

    formatRelativeChangeTime: function(utcTimestamp) {
      if (!utcTimestamp) return '';

      var date = new Date(utcTimestamp);
      if (isNaN(date.getTime())) return utcTimestamp;

      var now = new Date();
      var diffMs = now.getTime() - date.getTime();
      if (diffMs < 0) {
        return FHPB.DateTime.monthDayLabel(date, 'America/New_York');
      }

      var diffMinutes = Math.floor(diffMs / 60000);
      if (diffMinutes < 1) return 'Just now';
      if (diffMinutes === 1) return '1 minute ago';
      if (diffMinutes < 60) return diffMinutes + ' minutes ago';

      var diffHours = Math.floor(diffMinutes / 60);
      var remainingMinutes = diffMinutes % 60;
      if (diffHours < 24) {
        if (remainingMinutes === 0) {
          return diffHours + ' hour' + (diffHours === 1 ? '' : 's') + ' ago';
        }
        return diffHours + ' hour' + (diffHours === 1 ? '' : 's')
          + ' ' + remainingMinutes + ' min' + (remainingMinutes === 1 ? '' : 's') + ' ago';
      }

      var dayDiff = FHPB.DateTime.dayDifference(date, now);
      if (dayDiff <= 0) {
        return 'Today';
      }
      if (dayDiff === 1) {
        return 'Yesterday';
      }
      if (dayDiff < 7) {
        return FHPB.DateTime.numberWord(dayDiff) + ' days ago';
      }

      var weekDiff = Math.floor(dayDiff / 7);
      if (weekDiff === 1) {
        return 'Last week';
      }
      if (weekDiff < 5) {
        return FHPB.DateTime.numberWord(weekDiff) + ' weeks ago';
      }

      return FHPB.DateTime.monthDayLabel(date, 'America/New_York');
    },

    // Format a UTC timestamp to Eastern time (America/New_York)
    formatLocalTime: function(utcTimestamp, format) {
      if (!utcTimestamp) return '';
      
      var date = new Date(utcTimestamp);
      if (isNaN(date.getTime())) return utcTimestamp; // fallback if invalid

      var timeZone = 'America/New_York';

      function parts(options) {
        var out = {};
        new Intl.DateTimeFormat('en-US', options).formatToParts(date).forEach(function(p) {
          out[p.type] = p.value;
        });
        return out;
      }
      
      // Default format: "MMM d, yyyy h:mm a" equivalent
      if (!format || format === 'default') {
        var p = parts({
          timeZone: timeZone,
          month: 'short',
          day: 'numeric',
          year: 'numeric',
          hour: 'numeric',
          minute: '2-digit',
          hour12: true
        });

        return p.month + ' ' + p.day + ', ' + p.year + ' ' + p.hour + ':' + p.minute + ' ' + p.dayPeriod;
      }
      
      // Format: "yyyy-MM-dd HH:mm" equivalent
      if (format === 'datetime') {
        var p2 = parts({
          timeZone: timeZone,
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          hour12: false
        });

        return p2.year + '-' + p2.month + '-' + p2.day + ' ' + p2.hour + ':' + p2.minute;
      }

      if (format === 'relative-change') {
        return FHPB.DateTime.formatRelativeChangeTime(utcTimestamp);
      }
      
      return new Intl.DateTimeFormat('en-US', { timeZone: timeZone }).format(date);
    },

    // Initialize all elements with data-utc-time attribute
    initializeLocalTimes: function() {
      document.querySelectorAll('[data-utc-time]').forEach(function(element) {
        var utcTime = element.getAttribute('data-utc-time');
        var format = element.getAttribute('data-time-format') || 'default';
        element.textContent = FHPB.DateTime.formatLocalTime(utcTime, format);
      });
    }
  };

  /**
   * Form Utilities
   */
  FHPB.Form = {
    // Auto-fill current year for copyright notices
    fillCurrentYear: function(elementId) {
      var element = document.getElementById(elementId || 'year');
      if (element) {
        element.textContent = new Date().getFullYear();
      }
    },

    // Handle form validation states
    setFieldState: function(field, state, message) {
      if (!field) return;
      
      field.classList.remove('is-valid', 'is-invalid');
      
      var feedback = field.parentNode.querySelector('.invalid-feedback, .valid-feedback');
      if (feedback) {
        feedback.remove();
      }

      if (state === 'valid') {
        field.classList.add('is-valid');
        if (message) {
          var validFeedback = document.createElement('div');
          validFeedback.className = 'valid-feedback';
          validFeedback.textContent = message;
          field.parentNode.appendChild(validFeedback);
        }
      } else if (state === 'invalid') {
        field.classList.add('is-invalid');
        if (message) {
          var invalidFeedback = document.createElement('div');
          invalidFeedback.className = 'invalid-feedback';
          invalidFeedback.textContent = message;
          field.parentNode.appendChild(invalidFeedback);
        }
      }
    }
  };

  /**
   * Performance and Mobile Optimizations
   */
  FHPB.Performance = {
    // Lazy load images for better mobile performance
    lazyLoadImages: function() {
      if ('IntersectionObserver' in window) {
        var imageObserver = new IntersectionObserver(function(entries, observer) {
          entries.forEach(function(entry) {
            if (entry.isIntersecting) {
              var img = entry.target;
              img.src = img.dataset.src;
              img.classList.remove('lazy');
              observer.unobserve(img);
            }
          });
        });

        document.querySelectorAll('img[data-src]').forEach(function(img) {
          imageObserver.observe(img);
        });
      }
    },

    // Debounce function for search inputs
    debounce: function(func, wait) {
      var timeout;
      return function executedFunction() {
        var context = this;
        var args = arguments;
        var later = function() {
          timeout = null;
          func.apply(context, args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
      };
    }
  };

  /**
   * Initialize core functionality
   */
  FHPB.init = function() {
    // Initialize toasts
    FHPB.Toast.initializeToasts();
    
    // Fill current year
    FHPB.Form.fillCurrentYear();
    
    // Initialize lazy loading
    FHPB.Performance.lazyLoadImages();
    
    // Format timestamps to local time
    FHPB.DateTime.initializeLocalTimes();
  };

  // Auto-initialize when DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', FHPB.init);
  } else {
    FHPB.init();
  }

})(window, document);

(function () {
  "use strict";

  var overlay = document.getElementById("cf-loading-overlay");
  if (!overlay) return;

  function hideLoader() {
    overlay.classList.remove("cf-loading-active");
    overlay.setAttribute("aria-hidden", "true");
    overlay.style.display = "none";
    overlay.hidden = true;
  }

  function showLoader() {
    overlay.hidden = false;
    overlay.style.display = "grid";
    overlay.classList.add("cf-loading-active");
    overlay.setAttribute("aria-hidden", "false");
  }

  function importantForm(form) {
    if (form.dataset.cfLoader === "true") return true;
    if (form.dataset.noLoader === "true") return false;
    if (form.target === "_blank") return false;

    var action;
    try {
      action = new URL(form.getAttribute("action") || window.location.href,
                       window.location.href).pathname.replace(/\/+$/, "");
    } catch (_) {
      return false;
    }

    // Important account and listing operations.
    if (/\/(login|register|report-item|edit-item)$/.test(action)) return true;

    // On the profile page, only show it for name/password changes.
    if (/\/profile$/.test(action)) {
      return !!form.querySelector(
        'input[name="name"], input[name="fullName"], ' +
        'input[name="password"], input[name="currentPassword"], ' +
        'input[name="newPassword"], input[name="confirmPassword"]'
      );
    }

    return false;
  }

  // Mark authentication pages so their containers never jump on hover.
  if (document.querySelector(
      'form[action$="/login"], form[action$="/register"]'
  ) || /\/(login|register)\/?$/.test(window.location.pathname)) {
    document.body.classList.add("cf-auth-page");
  }

  window.CampusFindLoader = {
    show: showLoader,
    hide: hideLoader
  };

  // Only important POST submissions use the full-screen loader.
  document.addEventListener("submit", function (event) {
    var form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    if (event.defaultPrevented) return;
    if (form.method.toLowerCase() !== "post") return;
    if (!importantForm(form)) return;

    showLoader();
  }, true);

  // Normal internal navigation never triggers the full-screen loader.
  window.addEventListener("pageshow", hideLoader);
  window.addEventListener("pagehide", hideLoader);
  document.addEventListener("visibilitychange", function () {
    if (document.visibilityState === "visible") hideLoader();
  });

  hideLoader();
})();

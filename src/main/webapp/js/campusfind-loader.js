(function () {
  "use strict";
  var overlay = document.getElementById("cf-loading-overlay");
  if (!overlay) return;
  var timer = null;

  function showLoader() {
    window.clearTimeout(timer);
    overlay.hidden = false;
    overlay.classList.add("cf-loading-active");
    overlay.setAttribute("aria-hidden", "false");
  }
  function hideLoader() {
    overlay.classList.remove("cf-loading-active");
    overlay.setAttribute("aria-hidden", "true");
    // Keep it out of document flow even if the stylesheet is delayed or unavailable.
    overlay.hidden = true;
  }

  // Expose for JavaScript-driven transitions such as fetch-based Start Chat.
  window.CampusFindLoader = {
    show: showLoader,
    hide: hideLoader
  };

  document.addEventListener("submit", function (event) {
    if (event.defaultPrevented) return;
    var form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    if (form.target === "_blank" || form.dataset.noLoader === "true") return;
    showLoader();
  }, true);

  document.addEventListener("click", function (event) {
    var link = event.target.closest && event.target.closest("a[href]");
    if (!link || event.defaultPrevented || event.button !== 0) return;
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    if (link.target === "_blank" || link.hasAttribute("download")) return;
    var href = link.getAttribute("href");
    if (!href || href.charAt(0) === "#" || href.indexOf("javascript:") === 0) return;
    try {
      var url = new URL(href, window.location.href);
      if (url.origin !== window.location.origin || url.href === window.location.href) return;
    } catch (_) { return; }
    showLoader();
  }, true);

  window.addEventListener("pageshow", hideLoader);
  window.addEventListener("pagehide", function () {
    timer = window.setTimeout(hideLoader, 10000);
  });
  document.addEventListener("visibilitychange", function () {
    if (document.visibilityState === "visible") hideLoader();
  });
  // Defensive: the overlay must never remain visible as a normal page element.
  hideLoader();
})();

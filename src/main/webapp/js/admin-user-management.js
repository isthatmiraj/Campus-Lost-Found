(function () {
    "use strict";

    var search = document.getElementById("admin-user-search-input");
    var table = document.getElementById("admin-users-table");
    if (!table) return;

    var rows = Array.prototype.slice.call(table.querySelectorAll("[data-user-row]"));
    var noResults = document.getElementById("admin-users-no-results");
    var count = document.getElementById("admin-user-search-count");
    // Derive the context path from this page's own admin URL, without assuming root deployment.
    var contextPath = window.location.pathname.replace(/\/admin(?:\/.*)?$/, "");
    var endpoint = contextPath + "/presence/admin-status";
    var lastActivity = Date.now();

    function markActivity() {
        var wasInactive = Date.now() - lastActivity > 120000;
        lastActivity = Date.now();
        if (wasInactive) refreshPresence();
    }
    ["pointerdown", "keydown", "touchstart", "scroll", "mousemove"].forEach(function (eventName) {
        document.addEventListener(eventName, markActivity, { passive: true });
    });

    function applySearch() {
        var term = search ? search.value.trim().toLocaleLowerCase() : "";
        var visible = 0;
        rows.forEach(function (row) {
            var name = (row.getAttribute("data-user-name") || "").toLocaleLowerCase();
            var email = (row.getAttribute("data-user-email") || "").toLocaleLowerCase();
            var match = !term || name.indexOf(term) !== -1 || email.indexOf(term) !== -1;
            row.hidden = !match;
            if (match) visible++;
        });
        if (noResults) noResults.hidden = visible !== 0;
        if (count) count.textContent = visible + " of " + rows.length + " users";
    }

    function setPresence(id, online) {
        var el = null;
        table.querySelectorAll("[data-user-presence]").forEach(function (candidate) {
            if (candidate.getAttribute("data-user-presence") === id) el = candidate;
        });
        if (!el) return;
        el.classList.remove("is-checking", "is-online", "is-offline");
        el.classList.add(online ? "is-online" : "is-offline");
        var label = el.querySelector(".user-presence-label");
        if (label) label.textContent = online ? "Online" : "Offline";
        el.setAttribute("aria-label", online ? "Online" : "Offline");
    }

    function refreshPresence() {
        // Do not let the admin dashboard's polling keep an otherwise idle
        // 30-minute login session alive.
        if (document.visibilityState === "hidden" || Date.now() - lastActivity > 120000) return;
        fetch(endpoint, {
            method: "GET",
            credentials: "same-origin",
            cache: "no-store",
            headers: { "Accept": "application/json" }
        }).then(function (response) {
            if (!response.ok) throw new Error("Presence request failed");
            return response.json();
        }).then(function (data) {
            var online = new Set(data.onlineUserIds || []);
            rows.forEach(function (row) {
                var marker = row.querySelector("[data-user-presence]");
                if (marker) {
                    var id = marker.getAttribute("data-user-presence");
                    setPresence(id, online.has(id));
                }
            });
        }).catch(function () {
            // Keep the last known status if a temporary database/network issue occurs.
        });
    }

    if (search) search.addEventListener("input", applySearch);
    applySearch();
    refreshPresence();
    window.setInterval(refreshPresence, 60000);
    document.addEventListener("visibilitychange", function () {
        if (document.visibilityState === "visible") {
            lastActivity = Date.now();
            refreshPresence();
        }
    });
})();

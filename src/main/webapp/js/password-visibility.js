document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll('input[type="password"]').forEach(function (input) {
        const wrapper = document.createElement("span");
        wrapper.className = "password-field";

        input.parentNode.insertBefore(wrapper, input);
        wrapper.appendChild(input);

        const toggle = document.createElement("button");
        toggle.type = "button";
        toggle.className = "password-visibility-toggle";
        toggle.setAttribute("aria-label", "Show password");
        toggle.setAttribute("aria-pressed", "false");
        toggle.title = "Show password";

        toggle.innerHTML = `
            <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                <path class="password-eye-open"
                    d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z"/>
                <circle class="password-eye-open" cx="12" cy="12" r="3"/>
                <path class="password-eye-slash" d="M3 3 21 21" />
            </svg>`;

        toggle.addEventListener("click", function () {
            const showing = input.type === "password";
            input.type = showing ? "text" : "password";
            toggle.setAttribute("aria-label", showing ? "Hide password" : "Show password");
            toggle.setAttribute("aria-pressed", String(showing));
            toggle.title = showing ? "Hide password" : "Show password";
            toggle.classList.toggle("is-visible", showing);
        });

        wrapper.appendChild(toggle);
    });
});

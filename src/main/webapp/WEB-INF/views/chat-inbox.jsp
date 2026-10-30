<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="com.campus.lostfound.util.WebUtil" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Messages | CampusFind</title>

    <script>
(function () {
    try {
        if (localStorage.getItem("campusfind-theme") === "dark") {
            document.documentElement.setAttribute("data-theme", "dark");
        }
    } catch (e) {}
})();
</script>

    <style>
        * { box-sizing: border-box; }

        body {
            margin: 0;
            font-family: Inter, system-ui, -apple-system, sans-serif;
            background: #f5f7fb;
            color: #172033;
        }

        .page {
            max-width: 1000px;
            margin: 42px auto;
            padding: 0 20px;
        }

        .top {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 16px;
            margin-bottom: 26px;
        }

        .eyebrow {
            color: #64748b;
            font-size: 12px;
            font-weight: 700;
            letter-spacing: 1.5px;
            text-transform: uppercase;
        }

        h1 {
            margin: 7px 0;
            font-size: clamp(28px, 5vw, 38px);
            letter-spacing: -1.2px;
        }

        .subtitle {
            color: #64748b;
            margin: 0;
        }

        .back {
            text-decoration: none;
            color: #334155;
            border: 1px solid #dce2eb;
            background: white;
            padding: 11px 16px;
            border-radius: 12px;
            font-weight: 600;
            white-space: nowrap;
        }

        .panel {
            border: 1px solid #e4e9f1;
            background: white;
            border-radius: 20px;
            overflow: hidden;
            box-shadow: 0 12px 36px rgba(30, 41, 59, .045);
        }

        .panel-head {
            padding: 20px 24px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid #edf0f5;
        }

        .panel-head h2 {
            font-size: 17px;
            margin: 0;
        }

        .count {
            font-size: 12px;
            color: #475569;
            background: #f1f5f9;
            border-radius: 20px;
            padding: 6px 10px;
        }

        #status {
            padding: 20px 24px;
            color: #64748b;
            font-size: 14px;
        }

        .conversation {
            display: flex;
            align-items: center;
            gap: 15px;
            padding: 19px 24px;
            text-decoration: none;
            color: inherit;
            border-bottom: 1px solid #f0f2f6;
            transition: background .18s ease;
        }

        .conversation:hover { background: #f8faff; }

        .avatar {
            width: 48px;
            height: 48px;
            flex: 0 0 48px;
            display: grid;
            place-items: center;
            border-radius: 15px;
            color: #4338ca;
            background: #eef2ff;
            font-size: 19px;
            font-weight: 800;
        }

        .details { flex: 1; min-width: 0; }

        .item-title {
            font-weight: 750;
            font-size: 15px;
            margin-bottom: 5px;
        }

        .participant {
            color: #64748b;
            font-size: 13px;
            overflow-wrap: anywhere;
        }

        .meta {
            display: flex;
            flex-direction: column;
            align-items: flex-end;
            gap: 8px;
        }

        .time {
            font-size: 11px;
            color: #94a3b8;
            white-space: nowrap;
        }

        .open-label {
            font-size: 12px;
            color: #4f46e5;
            font-weight: 700;
        }

        .empty {
            text-align: center;
            padding: 55px 24px;
        }

        .empty-icon {
            width: 60px;
            height: 60px;
            margin: 0 auto 16px;
            display: grid;
            place-items: center;
            border-radius: 20px;
            background: #f1f5f9;
            font-size: 26px;
        }

        .empty h3 { margin: 0 0 8px; }
        .empty p {
            margin: 0 auto 20px;
            max-width: 340px;
            color: #64748b;
            line-height: 1.6;
            font-size: 14px;
        }

        .browse {
            display: inline-block;
            padding: 12px 17px;
            border-radius: 12px;
            background: #4f46e5;
            color: white;
            text-decoration: none;
            font-weight: 700;
        }

        @media (max-width: 600px) {
            .page { margin: 24px auto; padding: 0 12px; }
            .top { align-items: flex-start; }
            .back { padding: 9px 11px; font-size: 13px; }
            .panel { border-radius: 16px; }
            .panel-head { padding: 17px; }
            .conversation { padding: 16px; gap: 11px; }
            .avatar {
                width: 42px;
                height: 42px;
                flex-basis: 42px;
                border-radius: 13px;
            }
            .time { display: none; }
            .item-title { font-size: 14px; }
        }

       :root[data-theme="dark"] {
    --inbox-bg: #0f172a;
    --inbox-panel: #172033;
    --inbox-text: #e5e7eb;
    --inbox-muted: #94a3b8;
    --inbox-line: #334155;
}

:root:not([data-theme="dark"]) {
    --inbox-bg: #f5f7fb;
    --inbox-panel: #ffffff;
    --inbox-text: #172033;
    --inbox-muted: #64748b;
    --inbox-line: #e4e9f1;
}

body {
    background: var(--inbox-bg);
    color: var(--inbox-text);
}

.eyebrow, .subtitle, .participant, .empty p, #status {
    color: var(--inbox-muted);
}

.panel {
    background: var(--inbox-panel);
    border-color: var(--inbox-line);
}

.panel-head, .conversation {
    border-color: var(--inbox-line);
}

.count, .empty-icon, .avatar {
    background: var(--inbox-bg);
    color: var(--inbox-text);
}

.conversation:hover {
    background: var(--inbox-bg);
}

.back, .theme-toggle {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 10px 14px;
    border: 1px solid var(--inbox-line);
    border-radius: 12px;
    background: var(--inbox-panel);
    color: var(--inbox-text);
    text-decoration: none;
    font: inherit;
    font-weight: 600;
    cursor: pointer;
}

.inbox-actions {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
}

@media (max-width: 600px) {
    .top { flex-wrap: wrap; }
}

        :root { --inbox-bg:#f6faf7; --inbox-panel:#ffffff; --inbox-text:#14221b; --inbox-muted:#65766c; --inbox-line:#dce9e0; }
        :root[data-theme="dark"] { --inbox-bg:#070b09; --inbox-panel:#101713; --inbox-text:#edf7f0; --inbox-muted:#9aada1; --inbox-line:#26372d; color-scheme:dark; }
        body { background:var(--inbox-bg); color:var(--inbox-text); }
        .panel { background:var(--inbox-panel); border-color:var(--inbox-line); }
        .panel-head,.conversation { border-color:var(--inbox-line); }
        .conversation { background:var(--inbox-panel); }
        .conversation:hover { background:color-mix(in srgb, #00d6a0 8%, var(--inbox-panel)); }
        .item-title,h1,h2,h3 { color:var(--inbox-text); }
        .avatar { background:#00d6a0; color:#04110b; font-weight:800; }
        .back,.theme-toggle { border-color:var(--inbox-line); background:var(--inbox-panel); color:var(--inbox-text); }
        .browse { border:1px solid #00b889; background:#00b889; color:#04110b; }
        .browse:hover { background:#009b73; color:#04110b; }
        .count,.empty-icon { background:color-mix(in srgb, #00d6a0 12%, var(--inbox-panel)); color:var(--inbox-text); }

        /* CampusFind neon identity — light mode only */
        :root:not([data-theme="dark"]) {
            --inbox-bg: #f4fbf7;
            --inbox-panel: #ffffff;
            --inbox-text: #14221b;
            --inbox-muted: #62756a;
            --inbox-line: #d5eadf;
            color-scheme: light;
        }
        :root:not([data-theme="dark"]) body {
            background: var(--inbox-bg) !important;
            color: var(--inbox-text) !important;
        }
        :root:not([data-theme="dark"]) .panel {
            background: #ffffff !important;
            border-color: #cfe8db !important;
            box-shadow: 0 12px 36px rgba(0, 159, 120, .07) !important;
        }
        :root:not([data-theme="dark"]) .panel-head,
        :root:not([data-theme="dark"]) .conversation {
            border-color: #d5eadf !important;
        }
        :root:not([data-theme="dark"]) .conversation {
            background: #ffffff !important;
        }
        :root:not([data-theme="dark"]) .conversation:hover {
            background: #effbf5 !important;
        }
        :root:not([data-theme="dark"]) .item-title {
            color: #008f6b !important;
            font-weight: 800;
        }
        :root:not([data-theme="dark"]) .avatar {
            background: #00d6a0 !important;
            color: #042016 !important;
            box-shadow: 0 0 0 4px rgba(0, 214, 160, .12);
        }
        :root:not([data-theme="dark"]) .open-label {
            color: #008f6b !important;
        }
        :root:not([data-theme="dark"]) .count,
        :root:not([data-theme="dark"]) .empty-icon {
            background: #e4f9ef !important;
            color: #007b5c !important;
            border: 1px solid #bdebd5;
        }
        :root:not([data-theme="dark"]) .back,
        :root:not([data-theme="dark"]) .theme-toggle {
            border-color: #bdebd5 !important;
            background: #ffffff !important;
            color: #087653 !important;
        }
        :root:not([data-theme="dark"]) .eyebrow {
            color: #008f6b !important;
        }
        :root:not([data-theme="dark"]) .browse {
            border: 1px solid #00b889 !important;
            background: #00d6a0 !important;
            color: #042016 !important;
        }
        :root:not([data-theme="dark"]) .browse:hover {
            background: #00bd8d !important;
        }


        /* Inbox conversation content: preserve the existing row layout. */
        .person-name {
            color: var(--inbox-text);
            font-size: 15px;
            font-weight: 800;
            line-height: 1.3;
            margin-bottom: 4px;
        }
        .latest-message {
            color: var(--inbox-muted);
            font-size: 13px;
            line-height: 1.45;
            margin-bottom: 7px;
            overflow-wrap: anywhere;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }
        .item-badge {
            display: inline-flex;
            align-items: center;
            max-width: 100%;
            padding: 4px 9px;
            border: 1px solid rgba(0, 214, 160, .55);
            border-radius: 999px;
            background: rgba(0, 214, 160, .12);
            color: #00a77d;
            font-size: 11px;
            font-weight: 750;
            line-height: 1.35;
            overflow-wrap: anywhere;
        }
        :root[data-theme="dark"] .item-badge {
            color: #00e6ad;
            background: rgba(0, 214, 160, .10);
            border-color: rgba(0, 214, 160, .42);
        }
        @media (max-width: 600px) {
            .person-name { font-size: 14px; }
            .latest-message { font-size: 12px; }
        }
    </style>
</head>

<body>
<main class="page">
    <header class="top">
        <div>
            <div class="eyebrow">CampusFind · Inbox</div>
            <h1>Your messages</h1>
            <p class="subtitle">Conversations about lost and found items.</p>
        </div>
        <div class="inbox-actions">
    <a class="back" href="<%= request.getContextPath() %>/items">← Back to CampusFind</a>
    <button type="button" class="theme-toggle" id="theme-toggle"
            aria-label="Switch theme" title="Switch theme">
        <span id="theme-icon" aria-hidden="true">◐</span>
    </button>
</div>
    </header>

    <section class="panel">
        <div class="panel-head">
            <h2>Conversations</h2>
            <span class="count" id="conversationCount">Loading…</span>
        </div>

        <div id="status" role="status" aria-live="polite">
            Loading your conversations…
        </div>

        <div id="conversations"></div>
    </section>
</main>

<script>
(() => {
    const context = '<%= request.getContextPath() %>';
    const list = document.getElementById('conversations');
    const status = document.getElementById('status');
    const count = document.getElementById('conversationCount');

    async function loadInbox() {
        try {
            const response = await fetch(context + '/chat/inbox-data', {
                credentials: 'same-origin',
                headers: { 'Accept': 'application/json' }
            });

            if (!response.ok) throw new Error('Inbox could not be loaded.');

            const data = await response.json();
            const conversations = data.conversations || [];

            list.replaceChildren();
            count.textContent = conversations.length +
                (conversations.length === 1 ? ' conversation' : ' conversations');

            if (conversations.length === 0) {
                status.hidden = true;

                const empty = document.createElement('div');
                empty.className = 'empty';

                const icon = document.createElement('div');
                icon.className = 'empty-icon';
                icon.textContent = '✉';

                const heading = document.createElement('h3');
                heading.textContent = 'No conversations yet';

                const description = document.createElement('p');
                description.textContent =
                    'When you contact a reporter about an item, your conversation will appear here.';

                const link = document.createElement('a');
                link.className = 'browse';
                link.href = context + '/items';
                link.textContent = 'Explore listings';

                empty.append(icon, heading, description, link);
                list.appendChild(empty);
                return;
            }

            status.hidden = true;

            for (const conversation of conversations) {
                const link = document.createElement('a');
                link.className = 'conversation';
                link.href = context + '/chat/room?conversationId=' +
                    encodeURIComponent(conversation.id);

                const avatar = document.createElement('div');
                avatar.className = 'avatar';
                avatar.textContent = (conversation.participantName || 'C').trim().charAt(0).toUpperCase();

                const details = document.createElement('div');
                details.className = 'details';

                const title = document.createElement('div');
                title.className = 'person-name';
                title.textContent = conversation.participantName || 'Campus user';

                const latest = document.createElement('div');
                latest.className = 'latest-message';
                latest.textContent = conversation.latestMessage || 'No messages yet';

                const item = document.createElement('span');
                item.className = 'item-badge';
                item.textContent = 'Item: ' +
                    (conversation.itemTitle || ('Item ' + conversation.itemId));

                details.append(title, latest, item);

                const meta = document.createElement('div');
                meta.className = 'meta';

                const time = document.createElement('div');
                time.className = 'time';

                if (conversation.lastMessageAt) {
                    const date = new Date(conversation.lastMessageAt);
                    time.textContent = Number.isNaN(date.getTime())
                        ? '' : date.toLocaleDateString();
                }

                const open = document.createElement('div');
                open.className = 'open-label';
                open.textContent = 'Open →';

                meta.append(time, open);
                link.append(avatar, details, meta);
                list.appendChild(link);
            }
        } catch (error) {
            status.hidden = false;
            status.textContent =
                'Could not load conversations. Please refresh and try again.';
            count.textContent = 'Unavailable';
        }
    }

    loadInbox();
})();
</script>
<script>
(function () {
    const button = document.getElementById("theme-toggle");
    const icon = document.getElementById("theme-icon");
    if (!button || !icon) return;

    function updateIcon() {
        const dark = document.documentElement.getAttribute("data-theme") === "dark";
        icon.textContent = dark ? "☀" : "☾";
        button.setAttribute("aria-label", dark ? "Switch to light mode" : "Switch to dark mode");
        button.title = dark ? "Switch to light mode" : "Switch to dark mode";
    }

    updateIcon();

    button.addEventListener("click", function () {
        const dark = document.documentElement.getAttribute("data-theme") === "dark";
        if (dark) {
            document.documentElement.removeAttribute("data-theme");
            localStorage.setItem("campusfind-theme", "light");
        } else {
            document.documentElement.setAttribute("data-theme", "dark");
            localStorage.setItem("campusfind-theme", "dark");
        }
        updateIcon();
    });
})();
</script>
</body>
</html>

<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.campus.lostfound.util.WebUtil" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%
    String csrfToken = (String) session.getAttribute("csrfToken");
    if (csrfToken == null) {
        response.sendError(403, "Missing security token. Refresh and try again.");
        return;
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<script>
try {
    if (localStorage.getItem('campusfind-theme') === 'dark') {
        document.documentElement.setAttribute('data-theme', 'dark');
    }
} catch (e) {}
</script>
<title>Chat | CampusFind</title>
<style>
*{box-sizing:border-box}
:root{
 --chat-bg:#f4f6fb;--chat-panel:#fff;--chat-text:#172033;
 --chat-muted:#64748b;--chat-line:#e4e9f1;--chat-bubble:#f0f2f7;
 --chat-bubble-text:#202b40;--chat-input:#fff;--chat-own:#4f46e5;
}
:root[data-theme="dark"]{
 --chat-bg:#0b1220;--chat-panel:#111b2e;--chat-text:#e5edf9;
 --chat-muted:#9aabc5;--chat-line:#293750;--chat-bubble:#1c2940;
 --chat-bubble-text:#e5edf9;--chat-input:#0f172a;--chat-own:#4f46e5;
 color-scheme:dark;
}
body{margin:0;font-family:Inter,system-ui,sans-serif;background:var(--chat-bg);color:var(--chat-text);transition:background .15s,color .15s}
.app{max-width:900px;height:100dvh;max-height:950px;margin:auto;padding:18px;display:flex;flex-direction:column}
.top{display:flex;align-items:center;gap:14px;padding:12px 4px 18px}
.back,.theme-toggle{display:grid;place-items:center;width:42px;height:42px;border-radius:13px;background:var(--chat-panel);border:1px solid var(--chat-line);color:var(--chat-text);text-decoration:none;font-size:21px;cursor:pointer}
.heading{min-width:0;flex:1}
.heading h1{font-size:19px;margin:0 0 4px;color:var(--chat-text)}
.heading p{font-size:13px;color:var(--chat-muted);margin:0}
.badge{font-size:11px;font-weight:700;color:#047857;background:#d1fae5;border-radius:20px;padding:6px 9px}
:root[data-theme="dark"] .badge{color:#6ee7b7;background:#12382e}
.panel{flex:1;min-height:0;display:flex;flex-direction:column;background:var(--chat-panel);border:1px solid var(--chat-line);border-radius:22px;overflow:hidden;box-shadow:0 12px 40px #1e293b08}
.banner{padding:14px 20px;background:var(--chat-bg);border-bottom:1px solid var(--chat-line);color:var(--chat-muted);font-size:12px}
#messages{flex:1;min-height:0;overflow-y:auto;padding:24px;display:flex;flex-direction:column;gap:14px}
.state{margin:auto;text-align:center;color:var(--chat-muted);font-size:14px;line-height:1.6}
.message{max-width:min(78%,520px);display:flex;flex-direction:column;gap:5px}
.message.mine{align-self:flex-end;align-items:flex-end}
.message.theirs{align-self:flex-start;align-items:flex-start}
.bubble{padding:12px 15px;border-radius:17px;background:var(--chat-bubble);color:var(--chat-bubble-text);line-height:1.5;overflow-wrap:anywhere;white-space:pre-wrap}
.mine .bubble{background:var(--chat-own);color:#fff;border-bottom-right-radius:5px}
.theirs .bubble{border-bottom-left-radius:5px}
.stamp{font-size:10px;color:var(--chat-muted);padding:0 4px}
.composer{display:flex;align-items:flex-end;gap:10px;padding:15px;border-top:1px solid var(--chat-line);background:var(--chat-panel)}
#message{flex:1;min-width:0;max-height:130px;resize:vertical;border:1px solid var(--chat-line);border-radius:14px;padding:13px 14px;font:inherit;font-size:14px;outline:none;background:var(--chat-input);color:var(--chat-text)}
#message::placeholder{color:var(--chat-muted)}
#message:focus{border-color:#818cf8;box-shadow:0 0 0 3px #4f46e51a}
#send{border:0;border-radius:13px;padding:13px 18px;background:var(--chat-own);color:#fff;font-weight:750;cursor:pointer;transition:filter .15s,transform .15s}
#send:hover:not(:disabled){filter:brightness(1.12);transform:translateY(-1px)}
#send:disabled,#message:disabled{opacity:.5;cursor:not-allowed}
.note{font-size:11px;color:var(--chat-muted);text-align:center;padding:0 10px 12px}
@media(max-width:600px){.app{padding:8px;height:100dvh}.top{padding:8px 4px 12px}.panel{border-radius:17px}.badge{display:none}#messages{padding:16px 12px}.message{max-width:88%}.composer{padding:10px;gap:8px}#send{padding:13px 14px}}
</style>
</head>
<body>
<main class="app">
<header class="top">
<a class="back" href="<%= request.getContextPath() %>/chat/inbox-page" aria-label="Back to inbox">←</a>
<div class="heading">
<h1>CampusFind Messages</h1>
<p id="subtitle">Private conversation about a listing</p>
</div>
<button class="theme-toggle" id="theme-toggle" type="button"
        aria-label="Switch theme" title="Switch theme"><span id="theme-icon" aria-hidden="true">☾</span></button>
<span class="badge">PRIVATE CHAT</span>
</header>

<section class="panel">
<div class="banner">Keep messages respectful. Never share passwords or verification codes.</div>
<div id="messages" aria-live="polite">
<div class="state">Loading conversation…</div>
</div>
<form class="composer" id="composer">
<textarea id="message" rows="1" maxlength="2000" placeholder="Write a message…" aria-label="Message" required></textarea>
<button id="send" type="submit">Send ↗</button>
</form>
<div class="note">Messages refresh automatically while this page is open.</div>
</section>
</main>

<script>
(() => {
    const context = '<%= request.getContextPath() %>';
    const themeButton = document.getElementById('theme-toggle');
    const themeIcon = document.getElementById('theme-icon');

    function syncThemeButton() {
        const dark = document.documentElement.getAttribute('data-theme') === 'dark';
        themeIcon.textContent = dark ? '☀' : '☾';
        themeButton.setAttribute('aria-label', dark ? 'Switch to light mode' : 'Switch to dark mode');
        themeButton.title = dark ? 'Switch to light mode' : 'Switch to dark mode';
    }

    syncThemeButton();
    themeButton.addEventListener('click', () => {
        const dark = document.documentElement.getAttribute('data-theme') === 'dark';
        if (dark) {
            document.documentElement.removeAttribute('data-theme');
            localStorage.setItem('campusfind-theme', 'light');
        } else {
            document.documentElement.setAttribute('data-theme', 'dark');
            localStorage.setItem('campusfind-theme', 'dark');
        }
        syncThemeButton();
    });


    const params = new URLSearchParams(window.location.search);
    const conversationId = params.get('conversationId');
    const messagesEl = document.getElementById('messages');
    const form = document.getElementById('composer');
    const input = document.getElementById('message');
    const send = document.getElementById('send');
    let lastRendered = '';

    function showState(text) {
        messagesEl.replaceChildren();
        const state = document.createElement('div');
        state.className = 'state';
        state.textContent = text;
        messagesEl.appendChild(state);
    }

    function addMessage(message, currentUserId) {
        const row = document.createElement('div');
        row.className = 'message ' +
            (message.senderId === currentUserId ? 'mine' : 'theirs');

        const bubble = document.createElement('div');
        bubble.className = 'bubble';
        bubble.textContent = message.text;

        const stamp = document.createElement('div');
        stamp.className = 'stamp';
        const date = new Date(message.sentAt);
        stamp.textContent = Number.isNaN(date.getTime()) ? '' :
            date.toLocaleString([], {hour:'2-digit', minute:'2-digit'});

        row.append(bubble, stamp);
        messagesEl.appendChild(row);
    }

    async function loadMessages() {
        if (!conversationId) {
            showState('No conversation was selected. Return to your inbox.');
            return;
        }

        try {
            const response = await fetch(
                context + '/chat/messages?conversationId=' +
                encodeURIComponent(conversationId),
                {credentials:'same-origin', headers:{'Accept':'application/json'}}
            );

            if (!response.ok) {
                if (response.status === 403 || response.status === 404 ||
                    response.status === 400) {
                    showState('This conversation is unavailable or you do not have access.');
                    input.disabled = true;
                    send.disabled = true;
                    return;
                }
                throw new Error('Could not load messages.');
            }

            const data = await response.json();
            const serialized = JSON.stringify(data.messages || []);
            if (serialized === lastRendered) return;

            const nearBottom = messagesEl.scrollHeight -
                messagesEl.scrollTop - messagesEl.clientHeight < 100;

            lastRendered = serialized;
            messagesEl.replaceChildren();

            if (!data.messages || data.messages.length === 0) {
                showState('No messages yet. Say hello to start the conversation.');
            } else {
                data.messages.forEach(message => addMessage(message, data.currentUserId));
            }

            if (nearBottom) messagesEl.scrollTop = messagesEl.scrollHeight;
        } catch (error) {
            if (!lastRendered) showState('Unable to load messages. Please refresh and try again.');
        }
    }

    input.addEventListener('keydown', event => {
        if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
            event.preventDefault();
            if (input.value.trim() && !send.disabled) {
                form.requestSubmit();
            }
        }
    });

    form.addEventListener('submit', async event => {
        event.preventDefault();
        const text = input.value.trim();
        if (!text || !conversationId || send.disabled) return;

        send.disabled = true;
        input.disabled = true;

        try {
           const body = new URLSearchParams({
    conversationId,
    message: text,
    csrfToken: '<%= csrfToken %>'
});

            const response = await fetch(context + '/chat/send', {
                method:'POST',
                credentials:'same-origin',
                headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'},
                body
            });

            if (!response.ok) {
                throw new Error(response.status === 403
                    ? 'You cannot send messages in this conversation.'
                    : 'Message could not be sent.');
            }

            input.value = '';
            lastRendered = '';
            await loadMessages();
        } catch (error) {
            alert(error.message || 'Message could not be sent.');
        } finally {
            send.disabled = false;
            input.disabled = false;
            input.focus();
        }
    });

    if (!conversationId) {
        showState('No conversation was selected. Return to your inbox.');
        input.disabled = true;
        send.disabled = true;
    } else {
        loadMessages();
        setInterval(loadMessages, 4000);
    }
})();
</script>
</body>
</html>

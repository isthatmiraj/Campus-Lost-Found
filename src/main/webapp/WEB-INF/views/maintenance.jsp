<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.*,com.campus.lostfound.util.WebUtil" %>
<%
    List<Map<String,Object>> settings =
        (List<Map<String,Object>>) request.getAttribute("maintenanceSettings");
    Map<String,Object> maintenanceConfig =
        (Map<String,Object>) request.getAttribute("maintenanceConfig");
    List<Map<String,Object>> history =
        (List<Map<String,Object>>) request.getAttribute("maintenanceHistory");

    String csrf = (String) session.getAttribute("csrfToken");
    Boolean autoEnabled = (Boolean) maintenanceConfig.get("enabled");
    Integer intervalHours = (Integer) maintenanceConfig.get("hours");

    String previewCategory = (String) request.getAttribute("previewCategory");
    Integer previewDays = (Integer) request.getAttribute("previewDays");
    Long previewCount = (Long) request.getAttribute("previewCount");
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Database Maintenance | CampusFind</title>
<link rel="stylesheet" href="<%=request.getContextPath()%>/css/style.css">
<style>
.maintenance-wrap{max-width:1050px;margin:35px auto;padding:0 18px 50px}
.maintenance-panel{background:var(--card-bg, var(--surface,#fff));color:var(--text-color,inherit);
 border:1px solid var(--border-color,#ddd);border-radius:14px;padding:22px;margin:18px 0}
.maintenance-panel h2{margin-top:0}
.maintenance-muted{opacity:.72;font-size:.9rem}
.maintenance-row{display:flex;align-items:center;justify-content:space-between;gap:16px;
 padding:14px 0;border-bottom:1px solid var(--border-color,#ddd);flex-wrap:wrap}
.maintenance-row:last-child{border-bottom:0}
.maintenance-row input[type=number],.maintenance-row select{
 padding:9px;border:1px solid var(--border-color,#aaa);border-radius:8px;
 background:var(--input-bg,var(--card-bg,#fff));color:inherit;max-width:130px}
.maintenance-button{border:0;border-radius:9px;padding:10px 16px;cursor:pointer;
 background:var(--primary-color,#2563eb);color:white;font-weight:600}
.maintenance-button.secondary{background:#64748b}
.maintenance-button.danger{background:#b91c1c}
.maintenance-button:disabled{opacity:.45;cursor:not-allowed}
.maintenance-alert{padding:13px 15px;border-radius:9px;margin:14px 0;
 background:rgba(34,197,94,.12);border:1px solid rgba(34,197,94,.35)}
.maintenance-error{background:rgba(239,68,68,.10);border-color:rgba(239,68,68,.4)}
.maintenance-table{width:100%;border-collapse:collapse;font-size:.9rem}
.maintenance-table th,.maintenance-table td{text-align:left;padding:11px 8px;
 border-bottom:1px solid var(--border-color,#ddd)}
.maintenance-table-wrap{overflow-x:auto}
.maintenance-check{display:flex;align-items:center;gap:9px}
@media(max-width:600px){.maintenance-panel{padding:16px}.maintenance-row{align-items:flex-start}}
</style>
</head>
<body>
<%@ include file="nav.jspf" %>

<main class="maintenance-wrap">
    <h1>Database Maintenance</h1>
    <p class="maintenance-muted">
        Manage old messages and temporary records without touching core CampusFind data.
    </p>

    <a href="<%=request.getContextPath()%>/admin">← Back to Admin Dashboard</a>

    <% if (request.getAttribute("maintenanceFlash") != null) { %>
        <div class="maintenance-alert"><%=WebUtil.h(String.valueOf(request.getAttribute("maintenanceFlash")))%></div>
    <% } %>
    <% if (request.getAttribute("maintenanceError") != null) { %>
        <div class="maintenance-alert maintenance-error">
            <%=WebUtil.h(String.valueOf(request.getAttribute("maintenanceError")))%>
        </div>
    <% } %>

    <section class="maintenance-panel">
        <h2>Automatic cleanup</h2>
        <p class="maintenance-muted">
            Automatic cleanup is disabled by default. When enabled, the server checks
            every 15 minutes whether the configured interval has elapsed.
        </p>

        <form method="post" action="<%=request.getContextPath()%>/admin/maintenance">
            <input type="hidden" name="csrfToken" value="<%=WebUtil.h(csrf)%>">
            <input type="hidden" name="action" value="save">

            <div class="maintenance-row">
                <div>
                    <strong>Enable automatic cleanup</strong>
                    <div class="maintenance-muted">Only selected categories will be cleaned.</div>
                </div>
                <label class="maintenance-check">
                    <input type="checkbox" name="auto_enabled"
                        <%=Boolean.TRUE.equals(autoEnabled)?"checked":""%>>
                    Enabled
                </label>
            </div>

            <div class="maintenance-row">
                <div>
                    <strong>Cleanup frequency</strong>
                    <div class="maintenance-muted">How often automatic cleanup may run.</div>
                </div>
                <select name="interval_hours">
                    <option value="1" <%=intervalHours==1?"selected":""%>>Every hour</option>
                    <option value="6" <%=intervalHours==6?"selected":""%>>Every 6 hours</option>
                    <option value="12" <%=intervalHours==12?"selected":""%>>Every 12 hours</option>
                    <option value="24" <%=intervalHours==24?"selected":""%>>Every 24 hours</option>
                    <option value="168" <%=intervalHours==168?"selected":""%>>Every 7 days</option>
                </select>
            </div>

            <h3>Retention rules</h3>
            <% for (Map<String,Object> item : settings) {
                String key = String.valueOf(item.get("category"));
                int days = (Integer)item.get("days");
                boolean enabled = (Boolean)item.get("enabled");
            %>
                <div class="maintenance-row">
                    <div>
                        <strong><%=WebUtil.h(String.valueOf(item.get("label")))%></strong>
                        <div class="maintenance-muted">
                            <%=key.equals("CHAT_MESSAGES")
                                ? "Only messages in conversations inactive for the same period."
                                : key.equals("READ_NOTIFICATIONS")
                                ? "Unread notifications are never deleted."
                                : key.equals("AUDIT_LOGS")
                                ? "Minimum retention is 30 days."
                                : key.equals("EXPIRED_TOKENS")
                                ? "Only already-expired remembered-device tokens."
                                : key.equals("STALE_SESSIONS")
                                ? "Only old session records; not user accounts."
                                : "Old presence records."%>
                        </div>
                    </div>
                    <div class="maintenance-check">
                        <label>
                            <input type="checkbox" name="enabled_<%=key%>"
                                <%=enabled?"checked":""%>>
                            Auto
                        </label>
                        <input type="number" name="days_<%=key%>" min="<%=key.equals("AUDIT_LOGS")?30:1%>"
                            max="3650" required value="<%=days%>" aria-label="Retention days">
                        <span>days</span>
                    </div>
                </div>
            <% } %>

            <p class="maintenance-muted">
                Saving these settings does not immediately delete anything.
            </p>
            <button class="maintenance-button" type="submit">Save settings</button>
        </form>
    </section>

    <section class="maintenance-panel">
        <h2>Manual cleanup</h2>
        <p class="maintenance-muted">
            Preview the matching record count first. Nothing is deleted until you confirm.
        </p>

        <form method="post" action="<%=request.getContextPath()%>/admin/maintenance">
            <input type="hidden" name="csrfToken" value="<%=WebUtil.h(csrf)%>">
            <input type="hidden" name="action" value="preview">

            <div class="maintenance-row">
                <label for="cleanup-category"><strong>Category</strong></label>
                <select id="cleanup-category" name="category">
                    <% for (Map<String,Object> item : settings) { %>
                        <option value="<%=item.get("category")%>">
                            <%=WebUtil.h(String.valueOf(item.get("label")))%>
                        </option>
                    <% } %>
                </select>
            </div>
            <div class="maintenance-row">
                <label for="cleanup-days"><strong>Retention period</strong></label>
                <input id="cleanup-days" type="number" name="days" min="1" max="3650"
                    value="90" required>
            </div>
            <button class="maintenance-button secondary" type="submit">Preview cleanup</button>
        </form>

        <% if (previewCount != null && previewCategory != null) { %>
            <div class="maintenance-alert">
                <strong>Preview result:</strong>
                <%=previewCount%> matching records in
                <%=WebUtil.h(com.campus.lostfound.util.MaintenanceStore.label(previewCategory))%>
                for a retention period of <%=previewDays%> days.
            </div>

            <% if (previewCount > 0) { %>
                <form method="post" action="<%=request.getContextPath()%>/admin/maintenance"
                    onsubmit="return confirm('Permanently delete these matching records? This cannot be undone.');">
                    <input type="hidden" name="csrfToken" value="<%=WebUtil.h(csrf)%>">
                    <input type="hidden" name="action" value="confirm">
                    <input type="hidden" name="category" value="<%=WebUtil.h(previewCategory)%>">
                    <input type="hidden" name="days" value="<%=previewDays%>">
                    <button class="maintenance-button danger" type="submit">
                        Confirm and delete <%=previewCount%> records
                    </button>
                </form>
            <% } %>
        <% } %>
    </section>

    <section class="maintenance-panel">
        <h2>Recent cleanup history</h2>
        <% if (history == null || history.isEmpty()) { %>
            <p class="maintenance-muted">No cleanup operations have been recorded yet.</p>
        <% } else { %>
            <div class="maintenance-table-wrap">
                <table class="maintenance-table">
                    <thead>
                        <tr><th>Category</th><th>Deleted</th><th>Type</th>
                            <th>Actor</th><th>Time</th></tr>
                    </thead>
                    <tbody>
                    <% for (Map<String,Object> row : history) { %>
                        <tr>
                            <td><%=WebUtil.h(String.valueOf(row.get("category")))%></td>
                            <td><%=row.get("deleted")%></td>
                            <td><%=WebUtil.h(String.valueOf(row.get("type")))%></td>
                            <td><%=WebUtil.h(String.valueOf(row.get("actor") == null ? "System" : row.get("actor")))%></td>
                            <td><%=WebUtil.h(String.valueOf(row.get("created")))%></td>
                        </tr>
                    <% } %>
                    </tbody>
                </table>
            </div>
        <% } %>
    </section>


    <section class="maintenance-panel" id="chat-manager">
        <h2>Chat Management</h2>
        <p class="maintenance-muted">
            Browse every conversation and inspect its messages. Viewing messages here
            does not mark them as read.
        </p>

        <form method="get" action="<%=request.getContextPath()%>/admin/maintenance#chat-manager">
            <div class="maintenance-row">
                <input type="search" name="chatSearch"
                    value="<%=WebUtil.h(String.valueOf(request.getAttribute("chatSearch")))%>"
                    placeholder="Search names, item titles, IDs or messages"
                    style="flex:1;min-width:220px;padding:10px;border:1px solid var(--border-color,#aaa);border-radius:8px;background:var(--input-bg,var(--card-bg,#fff));color:inherit">
                <button class="maintenance-button secondary" type="submit">Search</button>
                <a href="<%=request.getContextPath()%>/admin/maintenance#chat-manager">Show all</a>
            </div>
        </form>

        <form method="post" action="<%=request.getContextPath()%>/admin/maintenance#chat-manager"
            onsubmit="return confirmBulkConversationDelete(this);">
            <input type="hidden" name="csrfToken" value="<%=WebUtil.h(csrf)%>">
            <input type="hidden" name="action" value="bulkDeleteConversations">
            <input type="hidden" name="chatSearch"
                value="<%=WebUtil.h(String.valueOf(request.getAttribute("chatSearch")))%>">

        <div class="maintenance-table-wrap">
            <table class="maintenance-table">
                <thead>
                    <tr>
                        <th><label class="maintenance-check">
                            <input type="checkbox" id="select-all-conversations"
                                aria-label="Select all listed conversations"
                                onchange="toggleAllConversations(this)"> All
                        </label></th>
                        <th>Listing</th><th>Participants</th><th>Messages</th>
                        <th>Last activity</th><th></th>
                    </tr>
                </thead>
                <tbody>
                <%
                    List<com.campus.lostfound.util.ChatStore.AdminConversation> adminConversations =
                        (List<com.campus.lostfound.util.ChatStore.AdminConversation>)
                            request.getAttribute("adminConversations");
                    String selectedChat = (String) request.getAttribute("selectedConversationId");
                    if (adminConversations == null || adminConversations.isEmpty()) {
                %>
                    <tr><td colspan="6">No conversations found.</td></tr>
                <% } else {
                    for (com.campus.lostfound.util.ChatStore.AdminConversation conversation : adminConversations) {
                %>
                    <tr>
                        <td>
                            <input type="checkbox" class="conversation-select"
                                name="conversationIds"
                                value="<%=WebUtil.h(conversation.id.toString())%>"
                                aria-label="Select conversation <%=WebUtil.h(conversation.id.toString())%>"
                                onchange="updateConversationSelection()">
                        </td>
                        <td>
                            <strong><%=WebUtil.h(conversation.itemTitle)%></strong>
                            <div class="maintenance-muted">Item: <%=WebUtil.h(String.valueOf(conversation.itemId))%></div>
                        </td>
                        <td>
                            <%=WebUtil.h(conversation.reporterName)%>
                            <div class="maintenance-muted">↔ <%=WebUtil.h(conversation.contactName)%></div>
                        </td>
                        <td><%=conversation.messageCount%></td>
                        <td><%=WebUtil.h(String.valueOf(conversation.lastMessageAt == null ? "—" : conversation.lastMessageAt))%></td>
                        <td>
                            <a class="maintenance-button secondary"
                               style="display:inline-block;text-decoration:none"
                               href="<%=request.getContextPath()%>/admin/maintenance?conversationId=<%=conversation.id%>&chatSearch=<%=java.net.URLEncoder.encode(String.valueOf(request.getAttribute("chatSearch")), "UTF-8")%>#chat-manager">
                                View
                            </a>
                        </td>
                    </tr>
                <% } } %>
                </tbody>
            </table>
        </div>

        <div class="maintenance-row">
            <span id="conversation-selection-count" class="maintenance-muted">
                0 conversations selected
            </span>
            <button class="maintenance-button danger" type="submit"
                id="bulk-delete-conversations" disabled>
                Delete selected conversations
            </button>
        </div>
        </form>

        <% if (selectedChat != null) {
            List<com.campus.lostfound.util.ChatStore.Message> adminMessages =
                (List<com.campus.lostfound.util.ChatStore.Message>)
                    request.getAttribute("adminChatMessages");
        %>
            <div class="maintenance-row">
                <div>
                    <h3 style="margin-bottom:4px">Conversation messages</h3>
                    <div class="maintenance-muted">Conversation ID: <%=WebUtil.h(selectedChat)%></div>
                    <div class="maintenance-muted">
                        <%=adminMessages == null ? 0 : adminMessages.size()%> messages loaded
                    </div>
                </div>
                <form method="post" action="<%=request.getContextPath()%>/admin/maintenance"
                    onsubmit="return confirm('Permanently delete EVERY message in this conversation? The conversation itself and its participants will remain. This cannot be undone.');">
                    <input type="hidden" name="csrfToken" value="<%=WebUtil.h(csrf)%>">
                    <input type="hidden" name="action" value="clearChatConversation">
                    <input type="hidden" name="conversationId" value="<%=WebUtil.h(selectedChat)%>">
                    <button class="maintenance-button danger" type="submit">Delete all messages</button>
                </form>
            </div>

            <div style="display:flex;flex-direction:column;gap:12px;margin-top:14px">
                <% if (adminMessages == null || adminMessages.isEmpty()) { %>
                    <p class="maintenance-muted">This conversation has no messages.</p>
                <% } else {
                    for (com.campus.lostfound.util.ChatStore.Message message : adminMessages) {
                        String senderName = message.senderId;
                        for (com.campus.lostfound.util.ChatStore.AdminConversation c : adminConversations) {
                            if (c.id.toString().equals(selectedChat)) {
                                if (message.senderId.equals(c.reporterId)) senderName = c.reporterName;
                                else if (message.senderId.equals(c.contactId)) senderName = c.contactName;
                                break;
                            }
                        }
                %>
                    <div style="border:1px solid var(--border-color,#ddd);border-radius:10px;padding:13px">
                        <div style="display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap">
                            <strong><%=WebUtil.h(senderName)%></strong>
                            <span class="maintenance-muted">
                                <%=WebUtil.h(String.valueOf(message.sentAt))%>
                                · <%=message.read ? "Read" : "Unread"%>
                            </span>
                        </div>
                        <p style="white-space:pre-wrap;overflow-wrap:anywhere;margin:10px 0"><%=WebUtil.h(message.messageText)%></p>
                        <form method="post" action="<%=request.getContextPath()%>/admin/maintenance"
                            onsubmit="return confirm('Permanently delete this message?');">
                            <input type="hidden" name="csrfToken" value="<%=WebUtil.h(csrf)%>">
                            <input type="hidden" name="action" value="deleteChatMessage">
                            <input type="hidden" name="conversationId" value="<%=WebUtil.h(selectedChat)%>">
                            <input type="hidden" name="messageId" value="<%=message.id%>">
                            <button class="maintenance-button danger" type="submit">Delete message</button>
                        </form>
                    </div>
                <% } } %>
            </div>
        <% } %>
    </section>

    <section class="maintenance-panel">
        <h2>Protected data</h2>
        <p class="maintenance-muted">
            This tool does not delete user accounts, listings, claims, handovers,
            pending listing edits, saved items, flags, or chat conversations.
            Unread notifications are also excluded.
        </p>
    </section>
</main>

<script>
function conversationCheckboxes() {
    return Array.from(document.querySelectorAll('.conversation-select'));
}

function toggleAllConversations(master) {
    conversationCheckboxes().forEach(function(box) {
        box.checked = master.checked;
    });
    updateConversationSelection();
}

function updateConversationSelection() {
    var boxes = conversationCheckboxes();
    var selected = boxes.filter(function(box) {
        return box.checked;
    }).length;

    var count = document.getElementById('conversation-selection-count');
    var button = document.getElementById('bulk-delete-conversations');
    var master = document.getElementById('select-all-conversations');

    if (count) count.textContent = selected + ' conversation(s) selected';
    if (button) button.disabled = selected === 0;

    if (master) {
        master.checked = boxes.length > 0 && selected === boxes.length;
        master.indeterminate = selected > 0 && selected < boxes.length;
    }
}

function confirmBulkConversationDelete(form) {
    var selected = form.querySelectorAll(
        'input[name="conversationIds"]:checked'
    ).length;

    if (selected === 0) {
        alert('Select at least one conversation first.');
        return false;
    }

    return confirm(
        'PERMANENTLY DELETE ' + selected +
        ' complete conversation(s), including every message?\n\n' +
        'This cannot be undone.'
    );
}

document.addEventListener('DOMContentLoaded', updateConversationSelection);
</script>
</body>
</html>

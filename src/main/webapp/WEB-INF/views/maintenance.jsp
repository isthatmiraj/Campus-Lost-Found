<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.*,com.campus.lostfound.util.WebUtil" %>
<%
String csrf = (String) session.getAttribute("csrfToken");
String flash = (String) request.getAttribute("maintenanceFlash");
String error = (String) request.getAttribute("maintenanceError");
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Chat Management | CampusFind</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/css/style.css">
</head>
<body>
<%@ include file="partials.jspf" %>
<%@ include file="nav.jspf" %>
<main class="container page">
    <p class="eyebrow">ADMINISTRATION</p>
    <h1>Chat Management</h1>
    <p>Manage conversations and messages reported or stored on CampusFind.</p>
    <a href="<%=request.getContextPath()%>/admin">← Back to Admin Dashboard</a>

    <% if (flash != null) { %>
        <div class="alert success"><%=WebUtil.h(flash)%></div>
    <% } %>
    <% if (error != null) { %>
        <div class="alert error"><%=WebUtil.h(error)%></div>
    <% } %>

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

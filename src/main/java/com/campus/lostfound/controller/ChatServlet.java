package com.campus.lostfound.controller;

import com.campus.lostfound.util.ChatStore;
import com.campus.lostfound.util.DataStore;
import com.campus.lostfound.util.WebUtil;
import com.campus.lostfound.util.WorkflowStore;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@WebServlet("/chat/*")
public class ChatServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        if (!WebUtil.loggedIn(req)) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        String userId = WebUtil.userId(req);
        String path = req.getPathInfo();

        try {
            if (path == null || "/".equals(path)
                    || "/inbox".equals(path)
                    || "/inbox-page".equals(path)) {
                req.getRequestDispatcher("/WEB-INF/views/chat-inbox.jsp")
                        .forward(req, resp);
                return;
            }

            if ("/room".equals(path)) {
                UUID conversationId =
                        parseUuid(req.getParameter("conversationId"));

                // Also verifies that this user belongs to the conversation.
                ChatStore.getMessages(conversationId, userId);

                req.setAttribute("currentUserId", userId);
                req.getRequestDispatcher("/WEB-INF/views/chat-room.jsp")
                        .forward(req, resp);
                return;
            }

            if ("/unread".equals(path)) {
                sendJson(resp, "{\"unread\":"
                        + ChatStore.getUnreadCount(userId) + "}");
                return;
            }

            // Combined unread counts for chat messages and app notifications.
            if ("/updates".equals(path)) {
                int unreadMessages = ChatStore.getUnreadCount(userId);
                long unreadNotifications =
                        WorkflowStore.get().unreadCount(userId);

                sendJson(resp, "{\"unreadMessages\":" + unreadMessages
                        + ",\"unreadNotifications\":" + unreadNotifications
                        + ",\"totalUnread\":"
                        + (unreadMessages + unreadNotifications) + "}");
                return;
            }

            // Only the other participant's typing state is exposed.
            if ("/live-messages".equals(path)) {
                String afterParam = req.getParameter("after");

                // Establish a baseline without replaying historical messages.
                if (afterParam == null || afterParam.isBlank()) {
                    long baseline =
                            ChatStore.latestMessageIdForUser(userId);

                    sendJson(resp, "{\"cursor\":" + baseline
                            + ",\"messages\":[]}");
                    return;
                }

                long afterId;
                try {
                    afterId = Long.parseLong(afterParam);
                    if (afterId < 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException invalid) {
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
                            "Invalid message cursor.");
                    return;
                }

                var events = ChatStore.getMessagesAfter(userId, afterId);

                long cursor = events.isEmpty()
                        ? afterId
                        : events.get(events.size() - 1).id;

                StringBuilder json = new StringBuilder();
                json.append("{\"cursor\":").append(cursor)
                        .append(",\"messages\":[");

                for (int i = 0; i < events.size(); i++) {
                    ChatStore.LiveMessage event = events.get(i);

                    if (i > 0) {
                        json.append(",");
                    }

                    String senderName = "Campus user";
                    var sender =
                            DataStore.get().findUserById(event.senderId);

                    if (sender != null && sender.getName() != null
                            && !sender.getName().isBlank()) {
                        senderName = sender.getName();
                    }

                    String itemTitle = "Campus listing";
                    var item = DataStore.get().findItem(event.itemId);

                    if (item != null && item.getTitle() != null
                            && !item.getTitle().isBlank()) {
                        itemTitle = item.getTitle();
                    }

                    json.append("{")
                            .append("\"id\":").append(event.id).append(",")
                            .append("\"conversationId\":\"")
                            .append(escape(event.conversationId)).append("\",")
                            .append("\"senderId\":\"")
                            .append(escape(event.senderId)).append("\",")
                            .append("\"senderName\":\"")
                            .append(escape(senderName)).append("\",")
                            .append("\"itemTitle\":\"")
                            .append(escape(itemTitle)).append("\",")
                            .append("\"text\":\"")
                            .append(escape(event.messageText)).append("\"")
                            .append("}");
                }

                json.append("]}");
                sendJson(resp, json.toString());
                return;
            }

            if ("/typing-status".equals(path)) {
                UUID conversationId =
                        parseUuid(req.getParameter("conversationId"));

                boolean typing = ChatStore.isOtherParticipantTyping(
                        conversationId, userId);

                sendJson(resp, "{\"typing\":" + typing + "}");
                return;
            }

            if ("/messages".equals(path)) {
                UUID conversationId =
                        parseUuid(req.getParameter("conversationId"));

                var messages = ChatStore.getMessages(conversationId, userId);

                String roomParticipantName = "Campus user";
                String roomItemTitle = "Campus listing";

                for (ChatStore.Conversation conversation : ChatStore.getInbox(userId)) {
                    if (conversation.id.equals(conversationId)) {
                        if (conversation.otherParticipantName != null && !conversation.otherParticipantName.isBlank()) {
                            roomParticipantName = conversation.otherParticipantName;
                        }
                        if (conversation.itemTitle != null && !conversation.itemTitle.isBlank()) {
                            roomItemTitle = conversation.itemTitle;
                        }
                        break;
                    }
                }

                StringBuilder json = new StringBuilder();
                json.append("{\"currentUserId\":\"")
                        .append(escape(userId))
                .append("\",\"participantName\":\"")
                .append(escape(roomParticipantName))
                .append("\",\"itemTitle\":\"")
                .append(escape(roomItemTitle))
                .append("\",\"messages\":[");

                for (int i = 0; i < messages.size(); i++) {
                    ChatStore.Message m = messages.get(i);
                    if (i > 0) json.append(",");

                    json.append("{")
                            .append("\"id\":").append(m.id).append(",")
                            .append("\"senderId\":\"")
                            .append(escape(m.senderId)).append("\",")
                            .append("\"text\":\"")
                            .append(escape(m.messageText)).append("\",")
                            .append("\"sentAt\":\"")
                            .append(m.sentAt == null ? ""
                                    : escape(m.sentAt.toInstant().toString()))
                            .append("\",")
                            .append("\"read\":").append(m.read)
                            .append("}");
                }

                json.append("]}");
                sendJson(resp, json.toString());
                return;
            }

            if ("/inbox-data".equals(path)) {
                var conversations = ChatStore.getInbox(userId);

                StringBuilder json =
                        new StringBuilder("{\"conversations\":[");

                for (int i = 0; i < conversations.size(); i++) {
                    ChatStore.Conversation c = conversations.get(i);
                    if (i > 0) json.append(",");

                    json.append("{")
                            .append("\"id\":\"").append(c.id).append("\",")
                            .append("\"itemId\":\"")
                            .append(escape(c.itemId)).append("\",")
                            .append("\"itemTitle\":\"")
                            .append(escape(c.itemTitle)).append("\",")
                            .append("\"participantName\":\"")
                            .append(escape(c.otherParticipantName)).append("\",")
                            .append("\"latestMessage\":\"")
                            .append(escape(c.latestMessage)).append("\",")
                            .append("\"reporterId\":\"")
                            .append(escape(c.reporterId)).append("\",")
                            .append("\"contactId\":\"")
                            .append(escape(c.contactId)).append("\",")
                            .append("\"lastMessageAt\":\"")
                            .append(c.lastMessageAt == null ? ""
                                    : escape(c.lastMessageAt.toInstant().toString()))
                            .append("\",")
                            .append("\"unreadCount\":")
                            .append(c.unreadCount)
                            .append("}");
                }

                json.append("]}");
                sendJson(resp, json.toString());
                return;
            }

            resp.sendError(HttpServletResponse.SC_NOT_FOUND);

        } catch (SecurityException e) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            getServletContext().log("Chat GET request failed", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to process chat request.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        if (!WebUtil.loggedIn(req)) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Please sign in to use chat.");
            return;
        }

        req.setCharacterEncoding("UTF-8");

        String userId = WebUtil.userId(req);
        String path = req.getPathInfo();

        try {
            if (!validCsrf(req)) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Invalid or missing CSRF token. Refresh the page and try again.");
                return;
            }

            if ("/start".equals(path)) {
                String itemId = req.getParameter("itemId");

                if (itemId == null || itemId.isBlank()) {
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
                            "An item ID is required.");
                    return;
                }

                ChatStore.Conversation c =
                        ChatStore.startConversation(itemId, userId);

                sendJson(resp, "{\"conversationId\":\"" + c.id + "\"}");
                return;
            }

            if ("/typing".equals(path)) {
                UUID conversationId =
                        parseUuid(req.getParameter("conversationId"));

                ChatStore.setTyping(
                        conversationId,
                        userId,
                        Boolean.parseBoolean(req.getParameter("typing")));

                sendJson(resp, "{\"success\":true}");
                return;
            }

            if ("/send".equals(path)) {
                UUID conversationId =
                        parseUuid(req.getParameter("conversationId"));

                ChatStore.sendMessage(
                        conversationId,
                        userId,
                        req.getParameter("message"));

                sendJson(resp, "{\"success\":true}");
                return;
            }

            resp.sendError(HttpServletResponse.SC_NOT_FOUND);

        } catch (SecurityException e) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            getServletContext().log("Chat POST request failed", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to process chat request.");
        }
    }

    private boolean validCsrf(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) return false;

        String expected = (String) session.getAttribute("csrfToken");
        String supplied = req.getParameter("csrfToken");

        return expected != null && supplied != null
                && java.security.MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8));
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Conversation ID is required.");
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid conversation ID.");
        }
    }

    private void sendJson(HttpServletResponse resp, String json)
            throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(json);
    }

    private String escape(String value) {
        if (value == null) return "";

        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

package com.campus.lostfound.controller;

import com.campus.lostfound.model.Item;
import com.campus.lostfound.util.ChatStore;
import com.campus.lostfound.util.DataStore;
import com.campus.lostfound.util.WebUtil;

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
                UUID conversationId = parseUuid(
                        req.getParameter("conversationId"));

                // This also verifies that the current user is a participant.
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

            if ("/messages".equals(path)) {
                UUID conversationId = parseUuid(
                        req.getParameter("conversationId"));

                var messages = ChatStore.getMessages(conversationId, userId);
                StringBuilder json = new StringBuilder();
                json.append("{\"currentUserId\":\"")
                    .append(escape(userId))
                    .append("\",\"messages\":[");

                for (int i = 0; i < messages.size(); i++) {
                    ChatStore.Message m = messages.get(i);
                    if (i > 0) json.append(",");

                    json.append("{")
                        .append("\"id\":").append(m.id).append(",")
                        .append("\"senderId\":\"").append(escape(m.senderId)).append("\",")
                        .append("\"text\":\"").append(escape(m.messageText)).append("\",")
                        .append("\"sentAt\":\"").append(escape(m.sentAt.toInstant().toString())).append("\"")
                        .append("}");
                }

                json.append("]}");
                sendJson(resp, json.toString());
                return;
            }

            if ("/inbox-data".equals(path)) {
                var conversations = ChatStore.getInbox(userId);
                StringBuilder json = new StringBuilder("{\"conversations\":[");

                for (int i = 0; i < conversations.size(); i++) {
                    ChatStore.Conversation c = conversations.get(i);
                    if (i > 0) json.append(",");

                    json.append("{")
                        .append("\"id\":\"").append(c.id).append("\",")
                        .append("\"itemId\":\"").append(escape(c.itemId)).append("\",")
                        .append("\"itemTitle\":\"").append(escape(c.itemTitle)).append("\",")
                        .append("\"participantName\":\"").append(escape(c.otherParticipantName)).append("\",")
                        .append("\"latestMessage\":\"").append(escape(c.latestMessage)).append("\",")
                        .append("\"reporterId\":\"").append(escape(c.reporterId)).append("\",")
                        .append("\"contactId\":\"").append(escape(c.contactId)).append("\",")
                        .append("\"lastMessageAt\":\"")
                        .append(escape(c.lastMessageAt.toInstant().toString()))
                        .append("\"}");
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

            if ("/send".equals(path)) {
                UUID conversationId = parseUuid(
                        req.getParameter("conversationId"));

                ChatStore.sendMessage(conversationId, userId,
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
    getServletContext().log("Chat POST request failed: "
            + e.getClass().getSimpleName() + ": " + e.getMessage(), e);

    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    resp.setContentType("text/plain;charset=UTF-8");
    resp.getWriter().write(
            "Chat error: " + e.getClass().getSimpleName()
            + " — " + (e.getMessage() == null
                    ? "No details available" : e.getMessage()));
}    }

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
            throw new IllegalArgumentException("Conversation ID is required.");
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid conversation ID.");
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

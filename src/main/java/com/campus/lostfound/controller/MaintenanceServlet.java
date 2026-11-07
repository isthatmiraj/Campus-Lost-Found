package com.campus.lostfound.controller;

import com.campus.lostfound.util.ChatStore;
import com.campus.lostfound.util.WebUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.*;

@WebServlet(urlPatterns = "/admin/maintenance")
public class MaintenanceServlet extends HttpServlet {

    private boolean validCsrf(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) return false;

        String expected = (String) session.getAttribute("csrfToken");
        String supplied = req.getParameter("csrfToken");
        if (expected == null || supplied == null) return false;

        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            supplied.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!WebUtil.admin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        render(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!WebUtil.admin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (!validCsrf(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                "Invalid or missing security token. Refresh the page and try again.");
            return;
        }

        req.setCharacterEncoding("UTF-8");
        String action = req.getParameter("action");

        try {
            if ("bulkDeleteConversations".equals(action)) {
                String[] selected = req.getParameterValues("conversationIds");
                if (selected == null || selected.length == 0) {
                    throw new IllegalArgumentException(
                        "Select at least one conversation to delete.");
                }

                List<UUID> ids = new ArrayList<>();
                for (String value : selected) {
                    if (value != null && !value.isBlank()) {
                        ids.add(UUID.fromString(value));
                    }
                }
                if (ids.isEmpty()) {
                    throw new IllegalArgumentException(
                        "Select at least one valid conversation to delete.");
                }

                int deleted = ChatStore.adminDeleteConversations(ids);
                req.getSession().setAttribute("maintenanceFlash",
                    deleted + " complete conversation(s) and all their messages permanently deleted.");

                String redirect = req.getContextPath() + "/admin/maintenance";
                String search = req.getParameter("chatSearch");
                if (search != null && !search.isBlank()) {
                    redirect += "?chatSearch=" +
                        java.net.URLEncoder.encode(search, StandardCharsets.UTF_8);
                }
                resp.sendRedirect(redirect + "#chat-manager");
                return;
            }

            if ("deleteChatMessage".equals(action)) {
                UUID conversationId = UUID.fromString(req.getParameter("conversationId"));
                long messageId = Long.parseLong(req.getParameter("messageId"));

                boolean deleted = ChatStore.adminDeleteMessage(conversationId, messageId);
                req.getSession().setAttribute("maintenanceFlash",
                    deleted ? "Message permanently deleted." : "Message was not found.");

                resp.sendRedirect(req.getContextPath()
                    + "/admin/maintenance?conversationId=" + conversationId + "#chat-manager");
                return;
            }

            if ("clearChatConversation".equals(action)) {
                UUID conversationId = UUID.fromString(req.getParameter("conversationId"));
                int deleted = ChatStore.adminClearConversation(conversationId);

                req.getSession().setAttribute("maintenanceFlash",
                    deleted + " messages permanently deleted. The conversation record remains.");

                resp.sendRedirect(req.getContextPath()
                    + "/admin/maintenance?conversationId=" + conversationId + "#chat-manager");
                return;
            }

            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        } catch (IllegalArgumentException e) {
            req.setAttribute("maintenanceError", e.getMessage());
            render(req, resp);
        } catch (SQLException e) {
            getServletContext().log("Chat maintenance action failed.", e);
            req.setAttribute("maintenanceError",
                "The database operation failed. Check the server logs for details.");
            render(req, resp);
        }
    }

    private void render(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            String chatSearch = req.getParameter("chatSearch");
            req.setAttribute("chatSearch", chatSearch == null ? "" : chatSearch);
            req.setAttribute("adminConversations",
                ChatStore.getAdminConversations(chatSearch));

            String selectedId = req.getParameter("conversationId");
            if (selectedId != null && !selectedId.isBlank()) {
                UUID conversationId = UUID.fromString(selectedId);
                req.setAttribute("selectedConversationId", conversationId.toString());
                req.setAttribute("adminChatMessages",
                    ChatStore.getAdminMessages(conversationId));
            }

            HttpSession session = req.getSession(false);
            if (session != null) {
                Object flash = session.getAttribute("maintenanceFlash");
                if (flash != null) {
                    req.setAttribute("maintenanceFlash", flash);
                    session.removeAttribute("maintenanceFlash");
                }
            }

            req.getRequestDispatcher("/WEB-INF/views/maintenance.jsp")
                .forward(req, resp);
        } catch (Exception e) {
            throw new ServletException("Could not load chat management dashboard.", e);
        }
    }
}

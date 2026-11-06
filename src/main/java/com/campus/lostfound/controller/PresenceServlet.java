package com.campus.lostfound.controller;

import com.campus.lostfound.util.PresenceStore;
import com.campus.lostfound.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@WebServlet(urlPatterns = {"/presence/heartbeat", "/presence/admin-status"})
public class PresenceServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!"/presence/heartbeat".equals(req.getServletPath())) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String expected = (String) session.getAttribute("csrfToken");
        String supplied = req.getHeader("X-CSRF-Token");
        if (expected == null || supplied == null ||
                !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        try {
            PresenceStore.heartbeat((String) session.getAttribute("userId"), session.getId());
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            resp.setHeader("Cache-Control", "no-store");
        } catch (IOException e) {
            throw new ServletException("Unable to update online status", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!"/presence/admin-status".equals(req.getServletPath())) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!WebUtil.admin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin access required.");
            return;
        }

        try {
            Set<String> onlineIds = PresenceStore.onlineUserIds();
            StringBuilder json = new StringBuilder("{\"onlineUserIds\":[");
            boolean first = true;
            for (String id : onlineIds) {
                if (!first) json.append(',');
                first = false;
                json.append('"').append(jsonEscape(id)).append('"');
            }
            json.append("]}");
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            resp.getWriter().write(json.toString());
        } catch (IOException e) {
            throw new ServletException("Unable to load online status", e);
        }
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}

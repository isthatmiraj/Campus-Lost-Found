package com.campus.lostfound.controller;

import com.campus.lostfound.model.User;
import com.campus.lostfound.util.DataStore;
import com.campus.lostfound.util.PasswordUtil;
import com.campus.lostfound.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

@WebServlet(urlPatterns = {"/login", "/register", "/logout"})
public class AuthServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getServletPath();
        if ("/logout".equals(path)) { resp.sendRedirect(req.getContextPath() + "/"); return; }
        req.getRequestDispatcher("/WEB-INF/views/" + ("/login".equals(path) ? "login.jsp" : "register.jsp"))
                .forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String path = req.getServletPath();
        if ("/logout".equals(path)) {
            HttpSession s = req.getSession(false);
            String expectedToken = s == null ? null : (String) s.getAttribute("csrfToken");
            String suppliedToken = req.getParameter("csrfToken");

            if (expectedToken == null || suppliedToken == null ||
                    !MessageDigest.isEqual(
                            expectedToken.getBytes(StandardCharsets.UTF_8),
                            suppliedToken.getBytes(StandardCharsets.UTF_8))) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            String rawCookieToken = com.campus.lostfound.util.RememberMeUtil.getRememberMeToken(req);
            if (rawCookieToken != null && !rawCookieToken.isBlank()) {
                try {
                    DataStore.get().deleteUserTokenByHash(com.campus.lostfound.util.RememberMeUtil.hashToken(rawCookieToken));
                } catch (Exception ignored) {}
                com.campus.lostfound.util.RememberMeUtil.clearRememberMeCookie(req, resp);
            }
            if (s.getAttribute("userId") != null) {
                String logoutUserId = (String) s.getAttribute("userId");
                try {
                    com.campus.lostfound.util.PresenceStore.removeSession(
                            logoutUserId, s.getId());
                    com.campus.lostfound.util.ActiveSessionStore.clear(
                            logoutUserId, s.getId());
                } catch (IOException ignored) {
                    // Expired presence and active-session records cannot authenticate anyone.
                }
            }
            s.invalidate();
            resp.sendRedirect(req.getContextPath() + "/");
            return;
        }
        String email = value(req.getParameter("email")).toLowerCase(Locale.ROOT);
        String password = value(req.getParameter("password"));
        try {
            if ("/register".equals(path)) {
                String name = value(req.getParameter("name"));
                if (name.length() < 2 || name.length() > 100 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ||
                        password.length() < 10 || password.length() > 200) {
                    req.setAttribute("error", "Enter a valid name and email. Password must be at least 10 characters.");
                    req.getRequestDispatcher("/WEB-INF/views/register.jsp").forward(req, resp);
                    return;
                }
                User user = DataStore.get().createUser(name, email, PasswordUtil.hash(password), "STUDENT");
                establishSession(req, user);
                boolean rememberMe = "true".equalsIgnoreCase(req.getParameter("rememberMe")) || "on".equalsIgnoreCase(req.getParameter("rememberMe"));
                if (rememberMe) {
                    String token = com.campus.lostfound.util.RememberMeUtil.generateToken();
                    String tokenHash = com.campus.lostfound.util.RememberMeUtil.hashToken(token);
                    java.time.LocalDateTime expiresAt = java.time.LocalDateTime.now().plusDays(30);
                    DataStore.get().saveUserToken(
                            user.getId(), tokenHash, expiresAt,
                            java.time.LocalDateTime.now(),
                            com.campus.lostfound.util.DeviceInfoUtil.label(req.getHeader("User-Agent")),
                            req.getHeader("User-Agent"));
                    com.campus.lostfound.util.RememberMeUtil.setRememberMeCookie(req, resp, token);
                }
                resp.sendRedirect(req.getContextPath() + "/items");
            } else {
                User user = DataStore.get().findUserByEmail(email);
                if (user == null || !PasswordUtil.verify(password, user.getPasswordHash())) {
                    req.setAttribute("error", "Email or password is incorrect.");
                    req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
                    return;
                }
                establishSession(req, user);
                boolean rememberMe = "true".equalsIgnoreCase(req.getParameter("rememberMe")) || "on".equalsIgnoreCase(req.getParameter("rememberMe"));
                if (rememberMe) {
                    String token = com.campus.lostfound.util.RememberMeUtil.generateToken();
                    String tokenHash = com.campus.lostfound.util.RememberMeUtil.hashToken(token);
                    java.time.LocalDateTime expiresAt = java.time.LocalDateTime.now().plusDays(30);
                    DataStore.get().saveUserToken(
                            user.getId(), tokenHash, expiresAt,
                            java.time.LocalDateTime.now(),
                            com.campus.lostfound.util.DeviceInfoUtil.label(req.getHeader("User-Agent")),
                            req.getHeader("User-Agent"));
                    com.campus.lostfound.util.RememberMeUtil.setRememberMeCookie(req, resp, token);
                }
                resp.sendRedirect(req.getContextPath() + "/items");
            }
        } catch (IllegalArgumentException e) {
            req.setAttribute("error", e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/register.jsp").forward(req, resp);
        } catch (IOException e) {
            throw new ServletException("Unable to read or write user records", e);
        }
    }

    private void establishSession(HttpServletRequest req, User user) throws IOException {
        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate();

        HttpSession session = req.getSession(true);
        session.setAttribute("userId", user.getId());
        session.setAttribute("name", user.getName());
        session.setAttribute("email", user.getEmail());
        session.setAttribute("role", user.getRole());
        session.setMaxInactiveInterval(30 * 60);

        // A fresh credential login replaces the account's previous active session.
        com.campus.lostfound.util.ActiveSessionStore.claim(user.getId(), session.getId());
    }
    private String value(String s) { return s == null ? "" : s.trim(); }
}

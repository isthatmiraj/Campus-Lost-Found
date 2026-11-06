package com.campus.lostfound.controller;

import com.campus.lostfound.model.User;
import com.campus.lostfound.model.UserToken;
import com.campus.lostfound.util.ActiveSessionStore;
import com.campus.lostfound.util.DataStore;
import com.campus.lostfound.util.PresenceStore;
import com.campus.lostfound.util.RememberMeUtil;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@WebFilter("/*")
public class SessionValidationFilter implements Filter {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        HttpSession session = req.getSession(false);

        // 1. Attempt Remember Me only when there is no authenticated session.
        if (session == null || session.getAttribute("userId") == null) {
            String rawToken = RememberMeUtil.getRememberMeToken(req);

            if (rawToken != null && !rawToken.isBlank()) {
                String tokenHash = RememberMeUtil.hashToken(rawToken);

                try {
                    UserToken userToken = DataStore.get().findUserTokenByHash(tokenHash);

                    if (userToken != null) {
                        if (userToken.getExpiresAt() != null &&
                                userToken.getExpiresAt().isAfter(LocalDateTime.now())) {

                            User user = DataStore.get().findUserById(userToken.getUserId());

                            if (user != null) {
                                HttpSession candidate = req.getSession(true);

                                // Crucial: a remembered device cannot replace a
                                // different session that is currently active.
                                boolean claimed = ActiveSessionStore.tryClaimIfInactive(
                                        user.getId(), candidate.getId());

                                if (claimed) {
                                    session = candidate;
                                    session.setAttribute("userId", user.getId());
                                    session.setAttribute("name", user.getName());
                                    session.setAttribute("email", user.getEmail());
                                    session.setAttribute("role", user.getRole());
                                    session.setMaxInactiveInterval(30 * 60);

                                    // Rotate only after this device successfully claims the session.
                                    DataStore.get().deleteUserTokenByHash(tokenHash);
                                    String newToken = RememberMeUtil.generateToken();
                                    String newTokenHash = RememberMeUtil.hashToken(newToken);
                                    DataStore.get().saveUserToken(
                                            user.getId(), newTokenHash,
                                            LocalDateTime.now().plusDays(30),
                                            userToken.getLoginStartedAt(),
                                            com.campus.lostfound.util.DeviceInfoUtil.label(req.getHeader("User-Agent")),
                                            req.getHeader("User-Agent"));
                                    RememberMeUtil.setRememberMeCookie(req, resp, newToken);
                                } else {
                                    // Keep the cookie: this device remains remembered,
                                    // but it must not silently take over another active session.
                                    session = null;
                                }
                            } else {
                                DataStore.get().deleteUserTokenByHash(tokenHash);
                                RememberMeUtil.clearRememberMeCookie(req, resp);
                            }
                        } else {
                            DataStore.get().deleteUserTokenByHash(tokenHash);
                            RememberMeUtil.clearRememberMeCookie(req, resp);
                        }
                    } else {
                        RememberMeUtil.clearRememberMeCookie(req, resp);
                    }
                } catch (IOException e) {
                    throw new ServletException("Unable to validate persistent login", e);
                }
            }
        }

        // 2. Enforce the one-active-session rule on every authenticated request.
        if (session != null && session.getAttribute("userId") != null) {
            String userId = (String) session.getAttribute("userId");

            try {
                if (!ActiveSessionStore.isCurrentAndTouch(userId, session.getId())) {
                    String oldSessionId = session.getId();

                    try {
                        PresenceStore.removeSession(userId, oldSessionId);
                    } catch (IOException ignored) {
                        // Stale presence is cleaned up by the normal presence expiry.
                    }

                    session.invalidate();

                    String path = req.getServletPath();
                    if (path.startsWith("/presence/")) {
                        resp.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                                "This session was replaced by another login.");
                    } else {
                        resp.sendRedirect(req.getContextPath() + "/login?sessionReplaced=1");
                    }
                    return;
                }

                User user = DataStore.get().findUserById(userId);
                if (user == null) {
                    ActiveSessionStore.clear(userId, session.getId());
                    session.invalidate();
                    RememberMeUtil.clearRememberMeCookie(req, resp);
                    resp.sendRedirect(req.getContextPath() + "/login?deleted=1");
                    return;
                }
            } catch (IOException e) {
                throw new ServletException("Unable to validate the current user session", e);
            }

            // Create a CSRF token for authenticated sessions if missing.
            if (session.getAttribute("csrfToken") == null) {
                byte[] bytes = new byte[32];
                SECURE_RANDOM.nextBytes(bytes);
                String token = Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(bytes);
                session.setAttribute("csrfToken", token);
            }
        }

        chain.doFilter(request, response);
    }
}

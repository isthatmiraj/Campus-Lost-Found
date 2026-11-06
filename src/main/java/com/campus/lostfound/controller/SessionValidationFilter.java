package com.campus.lostfound.controller;

import com.campus.lostfound.model.User;
import com.campus.lostfound.model.UserToken;
import com.campus.lostfound.util.DataStore;
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

        // 1. Attempt persistent login via Remember Me cookie if no active session exists
        if (session == null || session.getAttribute("userId") == null) {
            String rawToken = RememberMeUtil.getRememberMeToken(req);
            if (rawToken != null && !rawToken.isBlank()) {
                String tokenHash = RememberMeUtil.hashToken(rawToken);
                try {
                    UserToken userToken = DataStore.get().findUserTokenByHash(tokenHash);
                    if (userToken != null) {
                        if (userToken.getExpiresAt().isAfter(LocalDateTime.now())) {
                            User user = DataStore.get().findUserById(userToken.getUserId());
                            if (user != null) {
                                // Authenticate session
                                session = req.getSession(true);
                                session.setAttribute("userId", user.getId());
                                session.setAttribute("name", user.getName());
                                session.setAttribute("email", user.getEmail());
                                session.setAttribute("role", user.getRole());
                                session.setMaxInactiveInterval(30 * 60);

                                // Token Rotation: invalidate used token and issue new token
                                DataStore.get().deleteUserTokenByHash(tokenHash);
                                String newToken = RememberMeUtil.generateToken();
                                String newTokenHash = RememberMeUtil.hashToken(newToken);
                                DataStore.get().saveUserToken(user.getId(), newTokenHash, LocalDateTime.now().plusDays(30));
                                RememberMeUtil.setRememberMeCookie(req, resp, newToken);
                            } else {
                                DataStore.get().deleteUserTokenByHash(tokenHash);
                                RememberMeUtil.clearRememberMeCookie(req, resp);
                            }
                        } else {
                            // Expired token
                            DataStore.get().deleteUserTokenByHash(tokenHash);
                            RememberMeUtil.clearRememberMeCookie(req, resp);
                        }
                    } else {
                        // Tampered or unknown token
                        RememberMeUtil.clearRememberMeCookie(req, resp);
                    }
                } catch (IOException e) {
                    throw new ServletException("Unable to validate persistent login token", e);
                }
            }
        }

        // 2. Validate session user existence, manage CSRF token, and record activity heartbeat
        if (session != null && session.getAttribute("userId") != null) {
            String userId = (String) session.getAttribute("userId");

            try {
                User user = DataStore.get().findUserById(userId);

                if (user == null) {
                    session.invalidate();
                    RememberMeUtil.clearRememberMeCookie(req, resp);
                    resp.sendRedirect(req.getContextPath() + "/login?deleted=1");
                    return;
                }
            } catch (IOException e) {
                throw new ServletException(
                        "Unable to validate the current user session.", e);
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

            // 3. Heartbeat / Activity throttle: update at most once every 60 seconds
            Long lastActivity = (Long) session.getAttribute("lastActivityRecorded");
            long now = System.currentTimeMillis();
            if (lastActivity == null || (now - lastActivity) >= 60_000L) {
                try {
                    DataStore.get().updateSessionActivity(session.getId(), userId);
                    session.setAttribute("lastActivityRecorded", now);
                } catch (IOException ignored) {}
            }
        }

        chain.doFilter(request, response);
    }
}

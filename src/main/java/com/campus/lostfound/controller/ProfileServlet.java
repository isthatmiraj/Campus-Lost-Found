package com.campus.lostfound.controller;

import com.campus.lostfound.model.User;
import com.campus.lostfound.util.DataStore;
import com.campus.lostfound.util.PasswordUtil;
import com.campus.lostfound.util.LogoPreferenceStore;
import com.campus.lostfound.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

@WebServlet("/profile")
public class ProfileServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!WebUtil.loggedIn(req)) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        loadProfile(req);
        req.getRequestDispatcher("/WEB-INF/views/profile.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        if (!WebUtil.loggedIn(req)) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        HttpSession session = req.getSession(false);
        String expected = (String) session.getAttribute("csrfToken");
        String supplied = req.getParameter("csrfToken");

        if (expected == null || supplied == null ||
                !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Your session security token is invalid. Refresh the page and try again.");
            return;
        }

        String userId = WebUtil.userId(req);
        String action = value(req.getParameter("action"));

        try {
            if ("saveLogoPreference".equals(action)) {
                String preference = value(req.getParameter("logoPreference"));
                LogoPreferenceStore.set(userId, preference);
                resp.sendRedirect(req.getContextPath() + "/profile?logoUpdated=1");
                return;

            } else if ("updateProfile".equals(action)) {
                String name = value(req.getParameter("name"));
                String email = value(req.getParameter("email"))
                        .toLowerCase(Locale.ROOT);

                if (name.length() < 2 || name.length() > 100 ||
                        !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ||
                        email.length() > 320) {
                    throw new IllegalArgumentException(
                            "Enter a valid name and email address.");
                }

                DataStore.get().updateProfile(userId, name, email);

                session.setAttribute("name", name);
                session.setAttribute("email", email);
                resp.sendRedirect(req.getContextPath() + "/profile?updated=1");
                return;

            } else if ("changePassword".equals(action)) {
                String currentPassword = value(req.getParameter("currentPassword"));
                String newPassword = value(req.getParameter("newPassword"));
                String confirmPassword = value(req.getParameter("confirmPassword"));

                User user = DataStore.get().findUserById(userId);
                if (user == null) {
                    session.invalidate();
                    resp.sendRedirect(req.getContextPath() + "/login?deleted=1");
                    return;
                }

                if (!PasswordUtil.verify(currentPassword, user.getPasswordHash())) {
                    throw new IllegalArgumentException("Your current password is incorrect.");
                }

                if (newPassword.length() < 10 || newPassword.length() > 200) {
                    throw new IllegalArgumentException(
                            "Your new password must be between 10 and 200 characters.");
                }

                if (!newPassword.equals(confirmPassword)) {
                    throw new IllegalArgumentException("The new passwords do not match.");
                }

                if (PasswordUtil.verify(newPassword, user.getPasswordHash())) {
                    throw new IllegalArgumentException(
                            "Your new password must be different from your current password.");
                }

                DataStore.get().updatePasswordHash(userId, PasswordUtil.hash(newPassword));
                req.changeSessionId();
                resp.sendRedirect(req.getContextPath() + "/profile?passwordUpdated=1");
                return;
            }

            throw new IllegalArgumentException("Invalid profile action.");

        } catch (IllegalArgumentException e) {
            req.setAttribute("error", e.getMessage());
            loadProfile(req);
            req.getRequestDispatcher("/WEB-INF/views/profile.jsp").forward(req, resp);
        } catch (IOException e) {
            throw new ServletException("Unable to update your profile.", e);
        }
    }

    private void loadProfile(HttpServletRequest req) throws IOException {
        User user = DataStore.get().findUserById(WebUtil.userId(req));
        if (user == null) {
            req.getSession(false).invalidate();
            return;
        }
        req.setAttribute("profileUser", user);
    }

    private String value(String s) {
        return s == null ? "" : s.trim();
    }
}

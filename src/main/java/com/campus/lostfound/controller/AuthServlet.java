package com.campus.lostfound.controller;

import com.campus.lostfound.model.User;
import com.campus.lostfound.util.DataStore;
import com.campus.lostfound.util.PasswordUtil;
import com.campus.lostfound.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
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
            if (s != null) s.invalidate();
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
                resp.sendRedirect(req.getContextPath() + "/items");
            } else {
                User user = DataStore.get().findUserByEmail(email);
                if (user == null || !PasswordUtil.verify(password, user.getPasswordHash())) {
                    req.setAttribute("error", "Email or password is incorrect.");
                    req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
                    return;
                }
                establishSession(req, user);
                resp.sendRedirect(req.getContextPath() + "/items");
            }
        } catch (IllegalArgumentException e) {
            req.setAttribute("error", e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/register.jsp").forward(req, resp);
        } catch (IOException e) {
            throw new ServletException("Unable to read or write user records", e);
        }
    }

    private void establishSession(HttpServletRequest req, User user) {
        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate();
        HttpSession session = req.getSession(true);
        session.setAttribute("userId", user.getId());
        session.setAttribute("name", user.getName());
        session.setAttribute("email", user.getEmail());
        session.setAttribute("role", user.getRole());
        session.setMaxInactiveInterval(30 * 60);
    }
    private String value(String s) { return s == null ? "" : s.trim(); }
}

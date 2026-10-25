package com.campus.lostfound.controller;

import com.campus.lostfound.util.DataStore;
import com.campus.lostfound.util.PasswordUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;

import java.io.IOException;

@WebServlet(urlPatterns = "/bootstrap", loadOnStartup = 1)
public class BootstrapServlet extends HttpServlet {
    @Override
    public void init() throws ServletException {
        String email = System.getenv("CAMPUS_ADMIN_EMAIL");
        String password = System.getenv("CAMPUS_ADMIN_PASSWORD");
        if (email != null && !email.isBlank() && password != null && password.length() >= 12) {
            try {
                DataStore.get().ensureAdmin(email.trim(), PasswordUtil.hash(password));
            } catch (IOException e) {
                throw new ServletException("Could not initialize admin account", e);
            }
        } else {
            // Initialize data files even when no admin is configured.
            DataStore.get();
            getServletContext().log("Admin not seeded. Set CAMPUS_ADMIN_EMAIL and a password of at least 12 characters before startup.");
        }
    }
}

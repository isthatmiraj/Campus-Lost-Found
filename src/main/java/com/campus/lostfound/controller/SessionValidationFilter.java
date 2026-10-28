package com.campus.lostfound.controller;

import com.campus.lostfound.model.User;
import com.campus.lostfound.util.DataStore;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.security.SecureRandom;
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
        String path = req.getServletPath();

        if (session != null && session.getAttribute("userId") != null) {
            String userId = (String) session.getAttribute("userId");

            try {
                User user = DataStore.get().findUserById(userId);

                if (user == null) {
                    session.invalidate();
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
        }

        chain.doFilter(request, response);
    }
}

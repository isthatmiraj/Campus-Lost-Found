package com.campus.lostfound.controller;

import com.campus.lostfound.model.User;
import com.campus.lostfound.util.DataStore;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;

import java.io.IOException;

@WebFilter("/*")
public class SessionValidationFilter implements Filter {

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
                throw new ServletException("Unable to validate the current user session.", e);
            }
        }

        chain.doFilter(request, response);
    }
}


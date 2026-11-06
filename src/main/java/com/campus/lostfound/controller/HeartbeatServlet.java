package com.campus.lostfound.controller;

import com.campus.lostfound.util.DataStore;
import com.campus.lostfound.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

@WebServlet("/api/heartbeat")
public class HeartbeatServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!WebUtil.loggedIn(req)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        HttpSession session = req.getSession(false);
        String userId = WebUtil.userId(req);
        if (session != null && userId != null) {
            DataStore.get().updateSessionActivity(session.getId(), userId);
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}

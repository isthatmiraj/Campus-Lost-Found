package com.campus.lostfound.controller;

import com.campus.lostfound.model.User;
import com.campus.lostfound.util.DataStore;
import com.campus.lostfound.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@WebServlet("/admin/users/data")
public class AdminUsersApiServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!WebUtil.admin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin access required.");
            return;
        }

        String q = req.getParameter("q");
        List<User> users = DataStore.get().searchUsers(q);
        Set<String> onlineIds = DataStore.get().getOnlineUserIds();

        resp.setContentType("application/json;charset=UTF-8");
        StringBuilder sb = new StringBuilder();
        sb.append("{\"users\":[");
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            boolean isOnline = onlineIds.contains(u.getId());
            if (i > 0) sb.append(",");
            sb.append("{")
              .append("\"id\":\"" ).append(escape(u.getId())).append("\",")
              .append("\"name\":\"" ).append(escape(u.getName())).append("\",")
              .append("\"email\":\"" ).append(escape(u.getEmail())).append("\",")
              .append("\"role\":\"" ).append(escape(u.getRole())).append("\",")
              .append("\"online\":").append(isOnline)
              .append("}");
        }
        sb.append("]}");
        resp.getWriter().write(sb.toString());
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}

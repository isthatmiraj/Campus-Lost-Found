package com.campus.lostfound.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

public final class WebUtil {
    private WebUtil() {}
    public static String h(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&#x27;");
    }
    public static boolean loggedIn(HttpServletRequest req) {
        return req.getSession(false) != null && req.getSession(false).getAttribute("userId") != null;
    }
    public static String userId(HttpServletRequest req) {
        return (String) req.getSession(false).getAttribute("userId");
    }
    public static boolean admin(HttpServletRequest req) {
        return loggedIn(req) && "ADMIN".equals(req.getSession(false).getAttribute("role"));
    }
    public static void requireLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!loggedIn(req)) resp.sendRedirect(req.getContextPath() + "/login");
    }
}

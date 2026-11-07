package com.campus.lostfound.controller;

import com.campus.lostfound.util.MaintenanceStore;
import com.campus.lostfound.util.WebUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

@WebServlet(urlPatterns = "/admin/maintenance", loadOnStartup = 2)
public class MaintenanceServlet extends HttpServlet {
    private ScheduledExecutorService scheduler;

    @Override
    public void init() throws ServletException {
        try {
            MaintenanceStore.initialize();
        } catch (SQLException e) {
            throw new ServletException("Could not initialize maintenance tables.", e);
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CampusFind-Maintenance");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                MaintenanceStore.automaticCleanup();
            } catch (Exception e) {
                getServletContext().log("Automatic maintenance failed.", e);
            }
        }, 5, 15, TimeUnit.MINUTES);
    }

    @Override
    public void destroy() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    private boolean admin(HttpServletRequest req) {
        return WebUtil.admin(req);
    }

    private boolean validCsrf(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) return false;

        String expected = (String) session.getAttribute("csrfToken");
        String supplied = req.getParameter("csrfToken");

        if (expected == null || supplied == null) return false;

        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            supplied.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!admin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        render(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!admin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        if (!validCsrf(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                "Invalid or missing security token. Refresh the page and try again.");
            return;
        }

        req.setCharacterEncoding("UTF-8");
        String action = req.getParameter("action");

        try {
            if ("save".equals(action)) {
                Map<String, Integer> days = new HashMap<>();
                Set<String> enabled = new HashSet<>();

                for (String[] category : MaintenanceStore.CATEGORIES) {
                    String key = category[0];
                    days.put(key, Integer.parseInt(req.getParameter("days_" + key)));
                    if ("on".equals(req.getParameter("enabled_" + key))) {
                        enabled.add(key);
                    }
                }

                boolean auto = "on".equals(req.getParameter("auto_enabled"));
                int interval = Integer.parseInt(req.getParameter("interval_hours"));

                MaintenanceStore.saveSettings(days, enabled, auto, interval);
                req.getSession().setAttribute("maintenanceFlash",
                    "Maintenance settings saved.");
                resp.sendRedirect(req.getContextPath() + "/admin/maintenance");
                return;
            }

            if ("preview".equals(action)) {
                String category = req.getParameter("category");
                int days = Integer.parseInt(req.getParameter("days"));
                long count = MaintenanceStore.preview(category, days);

                req.setAttribute("previewCategory", category);
                req.setAttribute("previewDays", days);
                req.setAttribute("previewCount", count);
                render(req, resp);
                return;
            }

            if ("confirm".equals(action)) {
                String category = req.getParameter("category");
                int days = Integer.parseInt(req.getParameter("days"));

                long deleted = MaintenanceStore.cleanup(
                    category, days, String.valueOf(WebUtil.userId(req)), "MANUAL");

                req.getSession().setAttribute("maintenanceFlash",
                    deleted + " records deleted from " +
                    MaintenanceStore.label(category) + ".");
                resp.sendRedirect(req.getContextPath() + "/admin/maintenance");
                return;
            }

            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        } catch (IllegalArgumentException e) {
            req.setAttribute("maintenanceError", e.getMessage());
            render(req, resp);
        } catch (SQLException e) {
            getServletContext().log("Maintenance action failed.", e);
            req.setAttribute("maintenanceError",
                "The database operation failed. Check the server logs for details.");
            render(req, resp);
        }
    }

    private void render(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            req.setAttribute("maintenanceSettings", MaintenanceStore.settings());
            req.setAttribute("maintenanceConfig", MaintenanceStore.config());
            req.setAttribute("maintenanceHistory", MaintenanceStore.history());

            HttpSession session = req.getSession(false);
            if (session != null) {
                Object flash = session.getAttribute("maintenanceFlash");
                if (flash != null) {
                    req.setAttribute("maintenanceFlash", flash);
                    session.removeAttribute("maintenanceFlash");
                }
            }

            req.getRequestDispatcher("/WEB-INF/views/maintenance.jsp")
                .forward(req, resp);
        } catch (SQLException e) {
            throw new ServletException("Could not load maintenance dashboard.", e);
        }
    }
}

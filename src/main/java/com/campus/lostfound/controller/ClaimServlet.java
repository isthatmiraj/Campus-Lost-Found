package com.campus.lostfound.controller;

import com.campus.lostfound.model.Claim;
import com.campus.lostfound.model.Item;
import com.campus.lostfound.model.User;
import com.campus.lostfound.util.DataStore;
import com.campus.lostfound.util.WebUtil;
import com.campus.lostfound.util.WorkflowStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@WebServlet(urlPatterns = {"/claim", "/my-claims"})
public class ClaimServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!WebUtil.loggedIn(req)) { resp.sendRedirect(req.getContextPath() + "/login"); return; }
        List<Claim> claims = DataStore.get().claimsForUser(WebUtil.userId(req));
        List<Item> items = DataStore.get().allItems();
        req.setAttribute("claims", claims);
        req.setAttribute("items", items);
        req.getRequestDispatcher("/WEB-INF/views/my-claims.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (!WebUtil.loggedIn(req)) { resp.sendRedirect(req.getContextPath() + "/login"); return; }
        String itemId = req.getParameter("itemId");
        String proof = req.getParameter("proof") == null ? "" : req.getParameter("proof").trim();
        try {
            if (proof.length() < 5 || proof.length() > 1000) throw new IllegalArgumentException("Please provide a short ownership clue (5–1000 characters).");
            String claimantId = WebUtil.userId(req);
            User claimant = DataStore.get().findUserById(claimantId);
            String claimantName = claimant == null ? "A user" : claimant.getName();

            Item item = DataStore.get().createClaim(itemId, claimantId, proof) == null
                    ? null : DataStore.get().findItem(itemId);

            if (item != null) {
                String message = claimantName + " submitted an ownership claim for: " + item.getTitle();

                WorkflowStore.get().notifyUser(item.getReporterId(), message, "/dashboard");

                for (User user : DataStore.get().allUsers()) {
                    if (user.isAdmin()) {
                        WorkflowStore.get().notifyUser(user.getId(), message, "/admin");
                    }
                }
            }
            WorkflowStore.get().audit(WebUtil.userId(req), "CLAIM_SUBMITTED", "Item " + itemId);
            resp.sendRedirect(req.getContextPath() + "/my-claims?submitted=1");
        } catch (IllegalArgumentException e) {
            req.getSession().setAttribute("flashError", e.getMessage());
            resp.sendRedirect(req.getContextPath() + "/items");
        }
    }
}

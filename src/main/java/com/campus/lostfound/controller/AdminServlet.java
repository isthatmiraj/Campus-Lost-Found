package com.campus.lostfound.controller;

import com.campus.lostfound.model.Claim;
import com.campus.lostfound.model.Item;
import com.campus.lostfound.model.User;
import com.campus.lostfound.util.DataStore;
import com.campus.lostfound.util.CommunityStore;
import com.campus.lostfound.util.WebUtil;
import com.campus.lostfound.util.WorkflowStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@WebServlet(urlPatterns = {"/admin", "/admin/review"})
public class AdminServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!WebUtil.admin(req)) { resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin access required."); return; }
        List<Claim> claims = DataStore.get().allClaims();
        List<Item> items = DataStore.get().allItems();
        List<User> users = DataStore.get().allUsers();
        req.setAttribute("claims", claims);
        req.setAttribute("items", items);
        req.setAttribute("allUsers", users);
        req.setAttribute("auditEntries", WorkflowStore.get().recentAudit(30));
        req.setAttribute("listingFlags", CommunityStore.get().flags());
        req.setAttribute("returnedCount", items.stream().filter(i -> "RETURNED".equals(i.getStatus())).count());
        req.setAttribute("lostCount", items.stream().filter(i -> "LOST".equals(i.getType())).count());
        req.setAttribute("foundCount", items.stream().filter(i -> "FOUND".equals(i.getType())).count());
        req.getRequestDispatcher("/WEB-INF/views/admin.jsp").forward(req, resp);
    }
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!WebUtil.admin(req)) { resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin access required."); return; }
        String action = req.getParameter("action");
if ("resetUserPassword".equals(action)) {
    String targetUserId = req.getParameter("userId");
    String newPassword = req.getParameter("newPassword");
    String confirmPassword = req.getParameter("confirmPassword");

    if (targetUserId == null || targetUserId.isBlank()) {
        throw new IllegalArgumentException("Choose a user.");
    }

    if (newPassword == null || newPassword.length() < 10 || newPassword.length() > 200) {
        throw new IllegalArgumentException("Password must be between 10 and 200 characters.");
    }

    if (!newPassword.equals(confirmPassword)) {
        throw new IllegalArgumentException("Passwords do not match.");
    }

    User target = DataStore.get().findUserById(targetUserId);
    if (target == null) {
        throw new IllegalArgumentException("User not found.");
    }

    if (target.isAdmin() && targetUserId.equals(WebUtil.userId(req))) {
        throw new IllegalArgumentException("You cannot reset your own password from this page.");
    }

    DataStore.get().updatePasswordHash(
        targetUserId,
        com.campus.lostfound.util.PasswordUtil.hash(newPassword)
    );

    WorkflowStore.get().audit(
        WebUtil.userId(req),
        "USER_PASSWORD_RESET",
        "Password reset for user " + targetUserId
    );

    resp.sendRedirect(req.getContextPath() + "/admin?passwordReset=1");
    return;
}
        try {
            if ("resolveFlag".equals(action)) { String flagId=req.getParameter("flagId"); String flagStatus=req.getParameter("flagStatus"); CommunityStore.get().resolveFlag(flagId,flagStatus); WorkflowStore.get().audit(WebUtil.userId(req),"FLAG_"+flagStatus,"Flag "+flagId); resp.sendRedirect(req.getContextPath()+"/admin?flagUpdated=1"); return; }
            if ("deleteUser".equals(action)) {
                String targetUserId = req.getParameter("userId");
                if (targetUserId == null || targetUserId.isBlank()) {
                    throw new IllegalArgumentException("Choose a user to delete.");
                }
                if (targetUserId.equals(WebUtil.userId(req))) {
                    throw new IllegalArgumentException("You cannot delete your own administrator account while signed in.");
                }
                DataStore.get().deleteUser(targetUserId);
                WorkflowStore.get().audit(WebUtil.userId(req), "USER_DELETED", "User " + targetUserId);
                resp.sendRedirect(req.getContextPath() + "/admin?userDeleted=1");
                return;
            }

            String claimId = req.getParameter("claimId");
            String decision = req.getParameter("decision");
            Claim claim = null; for (Claim c : DataStore.get().allClaims()) if (c.getId().equals(claimId)) { claim = c; break; }
            if (claim == null) throw new IllegalArgumentException("Claim not found.");
            Item item = DataStore.get().findItem(claim.getItemId());
            User claimant = DataStore.get().findUserById(claim.getClaimantId());
            String claimantName = claimant == null ? "A user" : claimant.getName();

            DataStore.get().reviewClaim(claimId, decision);

            WorkflowStore.get().notifyUser(
                claim.getClaimantId(),
                "Your claim was " + decision.toLowerCase(Locale.ROOT)
                    + (item == null ? "." : " for: " + item.getTitle()),
                "/dashboard"
            );

            if (item != null) {
                WorkflowStore.get().notifyUser(
                    item.getReporterId(),
                    claimantName + "'s claim was " + decision.toLowerCase(Locale.ROOT)
                        + " for your item: " + item.getTitle(),
                    "/dashboard"
                );
            }
            if ("APPROVED".equals(decision) && item != null) WorkflowStore.get().createHandover(item.getId(), item.getReporterId(), claim.getClaimantId());
            WorkflowStore.get().audit(WebUtil.userId(req), "CLAIM_" + decision, "Claim " + claimId);
            resp.sendRedirect(req.getContextPath() + "/admin?updated=1");
        } catch (IllegalArgumentException e) {
            req.getSession().setAttribute("flashError", e.getMessage());
            resp.sendRedirect(req.getContextPath() + "/admin");
        }
    }
}

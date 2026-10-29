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
        req.setAttribute("pendingItemEdits", DataStore.get().pendingItemEditProposals());
        req.setAttribute("allUsers", users);
        req.setAttribute("auditEntries", WorkflowStore.get().recentAudit(30));
        req.setAttribute("listingFlags", CommunityStore.get().flags());
        req.setAttribute("returnedCount", items.stream().filter(i -> "RETURNED".equals(i.getStatus())).count());
        req.setAttribute("lostCount", items.stream().filter(i -> "LOST".equals(i.getType())).count());
        req.setAttribute("foundCount", items.stream().filter(i -> "FOUND".equals(i.getType())).count());
        req.getRequestDispatcher("/WEB-INF/views/admin.jsp").forward(req, resp);
    }
    private void deleteProposalPhoto(String photoName, String keepPhotoName) {
        if (photoName == null || photoName.isBlank() || photoName.equals(keepPhotoName)) return;
        if (!photoName.matches("[a-fA-F0-9-]+\\.(jpg|png)")) return;
        try {
            // Never delete a photo that is still referenced by any existing listing.
            boolean stillUsed = DataStore.get().allItems().stream()
                    .anyMatch(item -> photoName.equals(item.getPhotoName()));
            if (stillUsed) return;

            java.nio.file.Path uploads = DataStore.get().getDataDirectory().resolve("uploads").normalize();
            java.nio.file.Path photo = uploads.resolve(photoName).normalize();
            if (photo.getParent().equals(uploads)) java.nio.file.Files.deleteIfExists(photo);
        } catch (IOException ignored) {
            // Do not fail the review if cleanup fails.
        }
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
            if ("deleteItem".equals(action)) {
                String itemId = req.getParameter("itemId");
                if (itemId == null || itemId.isBlank()) {
                    throw new IllegalArgumentException("Choose a listing to delete.");
                }

                Item item = DataStore.get().findItem(itemId);
                if (item == null) {
                    throw new IllegalArgumentException("Listing not found.");
                }

                String title = item.getTitle() == null ? "Untitled listing" : item.getTitle();
                DataStore.get().deleteItem(itemId);
                WorkflowStore.get().audit(
                    WebUtil.userId(req),
                    "ITEM_DELETED",
                    "Listing " + itemId + ": " + title
                );

                resp.sendRedirect(req.getContextPath() + "/admin?itemDeleted=1");
                return;
            }

            if ("reviewItemEdit".equals(action)) {
                String proposalId = req.getParameter("proposalId");
                String decision = req.getParameter("decision");

                if (proposalId == null || proposalId.isBlank()) {
                    throw new IllegalArgumentException("Choose an edit proposal to review.");
                }
                if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
                    throw new IllegalArgumentException("Choose approve or reject.");
                }

                String[] proposal = DataStore.get().findItemEditProposal(proposalId);
                if (proposal == null) {
                    throw new IllegalArgumentException("This edit proposal has already been reviewed or removed.");
                }

                Item original = DataStore.get().findItem(proposal[1]);
                if (original == null) {
                    DataStore.get().removeItemEditProposal(proposalId);
                    deleteProposalPhoto(proposal[10], "");
                    throw new IllegalArgumentException("The original listing no longer exists; the proposal was discarded.");
                }
                if (!original.getReporterId().equals(proposal[2])) {
                    throw new IllegalArgumentException("The proposal owner does not match the listing owner.");
                }

                String oldPhoto = original.getPhotoName() == null ? "" : original.getPhotoName();
                String proposedPhoto = proposal[10] == null ? "" : proposal[10];
                String title = proposal[3] == null ? original.getTitle() : proposal[3];

                if ("APPROVED".equals(decision)) {
                    DataStore.get().applyItemEditProposal(proposalId);
                    if (!oldPhoto.equals(proposedPhoto)) {
                        deleteProposalPhoto(oldPhoto, "");
                    }
                } else {
                    DataStore.get().removeItemEditProposal(proposalId);
                    if (!oldPhoto.equals(proposedPhoto)) {
                        deleteProposalPhoto(proposedPhoto, oldPhoto);
                    }
                }

                WorkflowStore.get().notifyUser(
                    original.getReporterId(),
                    "Your proposed changes to '" + title + "' were "
                        + decision.toLowerCase(Locale.ROOT) + "."
                        + ("REJECTED".equals(decision) ? " Your existing listing remains unchanged." : ""),
                    "/item?id=" + original.getId()
                );

                WorkflowStore.get().audit(
                    WebUtil.userId(req),
                    "ITEM_EDIT_" + decision,
                    "Edit proposal " + proposalId + " for listing " + original.getId()
                );

                resp.sendRedirect(req.getContextPath() + "/admin?editReviewed=1");
                return;
            }

            if ("reviewItem".equals(action)) {
                String itemId = req.getParameter("itemId");
                String decision = req.getParameter("decision");

                if (itemId == null || itemId.isBlank()) {
                    throw new IllegalArgumentException("Choose an item to review.");
                }
                if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
                    throw new IllegalArgumentException("Choose approve or reject.");
                }

                Item item = DataStore.get().findItem(itemId);
                if (item == null) {
                    throw new IllegalArgumentException("Item not found.");
                }
                if (!"PENDING".equals(item.getApprovalStatus())) {
                    throw new IllegalArgumentException("This report has already been reviewed.");
                }

                DataStore.get().setItemApprovalStatus(itemId, decision);

                String title = item.getTitle() == null ? "your report" : item.getTitle();
                String message = "Your item report '" + title + "' was "
                        + decision.toLowerCase(Locale.ROOT) + ".";
                if ("REJECTED".equals(decision)) {
                    message += " It will not appear in public listings.";
                }

                WorkflowStore.get().notifyUser(
                    item.getReporterId(),
                    message,
                    "/dashboard"
                );

                WorkflowStore.get().audit(
                    WebUtil.userId(req),
                    "ITEM_" + decision,
                    "Item " + itemId + ": " + title
                );

                resp.sendRedirect(req.getContextPath() + "/admin?itemReviewed=1");
                return;
            }

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

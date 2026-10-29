<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %><%@ page import="java.util.*,com.campus.lostfound.model.*,com.campus.lostfound.util.WebUtil" %><%@ include file="partials.jspf" %>
<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Admin · CampusFind</title><link rel="stylesheet" href="<%=ctx%>/css/style.css"></head><body><%@ include file="nav.jspf" %><main class="container page"><p class="eyebrow">ADMINISTRATION</p><h1>Admin dashboard</h1><p class="muted">Overview of campus users, reports, and ownership claims.</p><% if(request.getParameter("updated")!=null){%><div class="success">Claim decision saved.</div><%} if(request.getParameter("itemReviewed")!=null){%><div class="success">Item moderation decision saved.</div><%} if(request.getParameter("editReviewed")!=null){%><div class="success">Listing edit review saved.</div><%} if(request.getParameter("userDeleted")!=null){%><div class="success">User account and its associated reports/claims were deleted.</div><%} if(session.getAttribute("flashError")!=null){%><div class="alert"><%=WebUtil.h((String)session.getAttribute("flashError"))%></div><%session.removeAttribute("flashError");}%>
<% List<User> allUsers=(List<User>)request.getAttribute("allUsers"); List<Claim> claims=(List<Claim>)request.getAttribute("claims"); List<Item> items=(List<Item>)request.getAttribute("items"); List<String[]> pendingItemEdits=(List<String[]>)request.getAttribute("pendingItemEdits"); String currentAdminId=WebUtil.userId(request); %>
<div class="admin-stats"><div class="admin-stat"><span>Users</span><strong><%=allUsers.size()%></strong></div><div class="admin-stat"><span>Item reports</span><strong><%=items.size()%></strong></div><div class="admin-stat"><span>Claims</span><strong><%=claims.size()%></strong></div><div class="admin-stat"><span>Pending claims</span><strong><%=claims.stream().filter(c -> "PENDING".equals(c.getStatus())).count()%></strong></div><div class="admin-stat"><span>Pending item reviews</span><strong><%=items.stream().filter(i -> "PENDING".equals(i.getApprovalStatus())).count()%></strong></div></div>
<div class="admin-stats"><div class="admin-stat"><span>Lost reports</span><strong><%=request.getAttribute("lostCount")%></strong></div><div class="admin-stat"><span>Found reports</span><strong><%=request.getAttribute("foundCount")%></strong></div><div class="admin-stat"><span>Returned items</span><strong><%=request.getAttribute("returnedCount")%></strong></div></div><h2>Pending listing edits</h2>
<p class="muted">Compare the owner's proposed changes with the currently approved listing. Changed values are highlighted.</p>
<% if(pendingItemEdits!=null && !pendingItemEdits.isEmpty()) { %>
<div class="edit-review-list">
<% for(String[] proposal:pendingItemEdits) {
    Item original=null;
    for(Item candidate:items) {
        if(candidate.getId().equals(proposal[1])) { original=candidate; break; }
    }
    if(original==null) { continue; }
    String[] labels={"Title","Description","Category","Type","Location","Date","Contact phone"};
    String[] currentValues={original.getTitle(),original.getDescription(),original.getCategory(),original.getType(),original.getLocation(),original.getDate(),original.getContactPhone()};
    String[] proposedValues={proposal[3],proposal[4],proposal[5],proposal[6],proposal[7],proposal[8],proposal[9]};
%>
<section class="edit-review-card">
    <h3><%=WebUtil.h(original.getTitle())%></h3>
    <p class="muted">Submitted: <%=WebUtil.h(proposal[11])%></p>
    <div class="edit-comparison">
    <% for(int n=0;n<labels.length;n++) {
        String currentValue=currentValues[n]==null?"":currentValues[n];
        String proposedValue=proposedValues[n]==null?"":proposedValues[n];
        boolean changed=!currentValue.equals(proposedValue);
    %>
        <div class="edit-diff-row">
            <strong><%=labels[n]%></strong>
            <div><small>Current:</small> <%=WebUtil.h(currentValue.isBlank()?"—":currentValue)%></div>
            <div><small>Proposed:</small>
                <% if(changed) { %><span class="edit-change"><%=WebUtil.h(proposedValue.isBlank()?"—":proposedValue)%></span>
                <% } else { %><%=WebUtil.h(proposedValue.isBlank()?"—":proposedValue)%><% } %>
            </div>
        </div>
    <% } %>
    </div>

    <% String oldPhoto=original.getPhotoName()==null?"":original.getPhotoName();
       String proposedPhoto=proposal[10]==null?"":proposal[10]; %>
    <div class="edit-photo-comparison">
        <div><strong>Current photo</strong><br>
        <% if(!oldPhoto.isBlank()) { %>
            <img class="admin-thumb" src="<%=ctx%>/photo/<%=WebUtil.h(oldPhoto)%>" alt="Current photo">
        <% } else { %><span class="muted">No photo</span><% } %>
        </div>
        <div><strong>Proposed photo</strong><br>
        <% if(!proposedPhoto.isBlank()) { %>
            <img class="admin-thumb <%=oldPhoto.equals(proposedPhoto)?"":"edit-change-photo"%>" src="<%=ctx%>/photo/<%=WebUtil.h(proposedPhoto)%>" alt="Proposed photo">
            <% if(!oldPhoto.equals(proposedPhoto)) { %><span class="edit-change">Photo changed</span><% } %>
        <% } else { %><span class="muted">No photo</span><% } %>
        </div>
    </div>

    <form class="decision" method="post" action="<%=ctx%>/admin/review">
        <input type="hidden" name="action" value="reviewItemEdit">
        <input type="hidden" name="proposalId" value="<%=WebUtil.h(proposal[0])%>">
        <button class="btn btn-small" name="decision" value="APPROVED"
                onclick="return confirm('Approve these changes and update the public listing?');">Approve changes</button>
        <button class="btn btn-small btn-danger" name="decision" value="REJECTED"
                onclick="return confirm('Reject these changes? The existing listing will remain unchanged.');">Reject changes</button>
    </form>
</section>
<% } %>
</div>
<% } else { %><p class="muted">No listing edits awaiting review.</p><% } %>

<h2>All item reports</h2><div class="table-wrap"><table><thead><tr><th>Item</th><th>Type / status</th><th>Moderation</th><th>Location / date</th><th>Reporter</th><th>Contact phone</th><th>Review action</th></tr></thead><tbody><% for(Item i:items){User owner=null;for(User u:allUsers){if(u.getId().equals(i.getReporterId())){owner=u;break;}}%><tr><td><%if(i.getPhotoName()!=null&&!i.getPhotoName().isBlank()){%><img class="admin-thumb" src="<%=ctx%>/photo/<%=WebUtil.h(i.getPhotoName())%>" alt="Item photo"><br><%}%><strong><%=WebUtil.h(i.getTitle())%></strong><br><small><%=WebUtil.h(i.getCategory())%></small><br><%=WebUtil.h(i.getDescription())%></td><td><%=WebUtil.h(i.getType())%><br><span class="status"><%=WebUtil.h(i.getStatus().replace('_',' '))%></span></td><td><span class="status"><%=WebUtil.h(i.getApprovalStatus())%></span></td><td><%=WebUtil.h(i.getLocation())%><br><small><%=WebUtil.h(i.getDate())%></small></td><td><%=owner==null?"Unknown":WebUtil.h(owner.getName())%><br><small><%=owner==null?"":WebUtil.h(owner.getEmail())%></small></td><td><%=i.getContactPhone().isBlank()?"—":WebUtil.h(i.getContactPhone())%></td><td><%if("PENDING".equals(i.getApprovalStatus())){%><form class="decision" method="post" action="<%=ctx%>/admin/review"><input type="hidden" name="action" value="reviewItem"><input type="hidden" name="itemId" value="<%=WebUtil.h(i.getId())%>"><button class="btn btn-small" name="decision" value="APPROVED">Approve</button><button class="btn btn-small btn-danger" name="decision" value="REJECTED" onclick="return confirm('Reject this item report? It will remain hidden from public listings.');">Reject</button></form><%}else{%>Reviewed<%}%></td></tr><%}if(items.isEmpty()){%><tr><td colspan="7">No reports yet.</td></tr><%}%></tbody></table></div>
<h2>Ownership claims</h2><div class="table-wrap"><table><thead><tr><th>Item</th><th>Claimant</th><th>Ownership clue</th><th>Status</th><th>Decision</th></tr></thead><tbody><% for(Claim c:claims){Item item=null;for(Item i:items){if(i.getId().equals(c.getItemId())){item=i;break;}}User claimant=null;for(User u:allUsers){if(u.getId().equals(c.getClaimantId())){claimant=u;break;}}%><tr><td><%=item==null?"Unknown item":WebUtil.h(item.getTitle())%><br><small><%=WebUtil.h(c.getCreatedAt())%></small></td><td><%=claimant==null?"Unknown":WebUtil.h(claimant.getName())%><br><small><%=claimant==null?"":WebUtil.h(claimant.getEmail())%></small></td><td><%=WebUtil.h(c.getProof())%></td><td><span class="status"><%=WebUtil.h(c.getStatus())%></span></td><td><%if("PENDING".equals(c.getStatus())){%><form class="decision" method="post" action="<%=ctx%>/admin/review"><input type="hidden" name="claimId" value="<%=WebUtil.h(c.getId())%>"><button class="btn btn-small" name="decision" value="APPROVED">Approve</button><button class="btn btn-small btn-danger" name="decision" value="REJECTED">Reject</button></form><%}else{%>Reviewed<%}%></td></tr><%}if(claims.isEmpty()){%><tr><td colspan="5">No claims yet.</td></tr><%}%></tbody></table></div>
<h2>Registered users</h2><p class="muted">Deleting an account also deletes the items reported by that user and claims linked to that user or those items. You cannot delete your own signed-in account, and the last administrator is protected.</p><div class="table-wrap"><table><thead><tr><th>Name</th><th>Email</th><th>Role</th><th>User ID</th><th>Account actions</th></tr></thead><tbody><%for(User u:allUsers){%><tr><td><%=WebUtil.h(u.getName())%><%if(u.getId().equals(currentAdminId)){%><br><small>Signed-in account</small><%}%></td><td><%=WebUtil.h(u.getEmail())%></td><td><span class="status"><%=WebUtil.h(u.getRole())%></span></td><td><small><%=WebUtil.h(u.getId())%></small></td><td>

<button class="btn btn-small" type="button"
        onclick="const f=this.nextElementSibling; const open=f.style.display==='none'; f.hidden=!open; f.style.display=open?'block':'none'; this.textContent=open?'Cancel':'Reset Password';">
    Reset Password
</button>
<form hidden style="display:none" method="post"
      action="<%=ctx%>/admin"
      onsubmit="return confirm('Reset this user password?');">
    <input type="hidden" name="action" value="resetUserPassword">
    <input type="hidden" name="userId" value="<%=WebUtil.h(u.getId())%>">

    <label>New password</label>
    <input type="password" name="newPassword" minlength="10"
           maxlength="200" required autocomplete="new-password">

    <label>Confirm password</label>
    <input type="password" name="confirmPassword" minlength="10"
           maxlength="200" required autocomplete="new-password">

    <button class="btn btn-small" type="submit">Save New Password</button>
</form>

<%if(u.getId().equals(currentAdminId)){%><span class="muted">Cannot delete yourself</span><%}else{%><form method="post" action="<%=ctx%>/admin" onsubmit="return confirm('Delete this account? Their reports and related claims will also be removed. This cannot be undone.');"><input type="hidden" name="action" value="deleteUser"><input type="hidden" name="userId" value="<%=WebUtil.h(u.getId())%>"><button class="btn btn-small btn-danger" type="submit">Delete account</button></form><%}%></td></tr><%}if(allUsers.isEmpty()){%><tr><td colspan="5">No users yet.</td></tr><%}%></tbody></table></div>
<h2>Listing reports</h2><p class="muted">Review flags submitted by users.</p><div class="table-wrap"><table><thead><tr><th>Reporter ID</th><th>Item ID</th><th>Reason</th><th>Status</th><th>Action</th></tr></thead><tbody><% List<String[]> listingFlags=(List<String[]>)request.getAttribute("listingFlags"); if(listingFlags!=null)for(String[] f:listingFlags){%><tr><td><%=WebUtil.h(f[1])%></td><td><a href="<%=ctx%>/item?id=<%=java.net.URLEncoder.encode(f[2],java.nio.charset.StandardCharsets.UTF_8)%>"><%=WebUtil.h(f[2])%></a></td><td><%=WebUtil.h(f[3])%></td><td><%=WebUtil.h(f[4])%></td><td><%if("OPEN".equals(f[4])){%><form method="post" action="<%=ctx%>/admin"><input type="hidden" name="action" value="resolveFlag"><input type="hidden" name="flagId" value="<%=WebUtil.h(f[0])%>"><button class="btn btn-small" name="flagStatus" value="REVIEWED">Mark reviewed</button><button class="btn btn-small btn-light" name="flagStatus" value="DISMISSED">Dismiss</button></form><%}else{%>—<%}%></td></tr><%}if(listingFlags==null||listingFlags.isEmpty()){%><tr><td colspan="5">No listing flags.</td></tr><%}%></tbody></table></div>
<h2>Recent audit log</h2><p class="muted">Recent system actions for basic accountability. This log is stored separately from the core records.</p><div class="table-wrap"><table><thead><tr><th>Time</th><th>Actor ID</th><th>Action</th><th>Details</th></tr></thead><tbody><% List<String[]> auditEntries=(List<String[]>)request.getAttribute("auditEntries"); if(auditEntries!=null)for(String[] a:auditEntries){%><tr><td><%=WebUtil.h(a[0])%></td><td><small><%=WebUtil.h(a[1])%></small></td><td><%=WebUtil.h(a[2])%></td><td><%=WebUtil.h(a[3])%></td></tr><%}if(auditEntries==null||auditEntries.isEmpty()){%><tr><td colspan="4">No audit entries yet.</td></tr><%}%></tbody></table></div></main></body></html>

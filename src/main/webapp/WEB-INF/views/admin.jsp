<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.*, com.campus.lostfound.model.*, com.campus.lostfound.util.*" %>
<%
    String ctx = request.getContextPath();
    List<User> allUsers = (List<User>) request.getAttribute("allUsers");
    if (allUsers == null) allUsers = Collections.emptyList();
    List<Item> items = (List<Item>) request.getAttribute("items");
    if (items == null) items = Collections.emptyList();
    List<Claim> claims = (List<Claim>) request.getAttribute("claims");
    if (claims == null) claims = Collections.emptyList();
    List<String[]> pendingEdits = (List<String[]>) request.getAttribute("pendingItemEdits");
    if (pendingEdits == null) pendingEdits = Collections.emptyList();
    List<String[]> auditEntries = (List<String[]>) request.getAttribute("auditEntries");
    if (auditEntries == null) auditEntries = Collections.emptyList();
    List<String[]> listingFlags = (List<String[]>) request.getAttribute("listingFlags");
    if (listingFlags == null) listingFlags = Collections.emptyList();

    long returnedCount = (Long) request.getAttribute("returnedCount");
    long lostCount = (Long) request.getAttribute("lostCount");
    long foundCount = (Long) request.getAttribute("foundCount");

    String currentAdminId = WebUtil.userId(request);
    Set<String> onlineUserIds = (Set<String>) request.getAttribute("onlineUserIds");
    if (onlineUserIds == null) onlineUserIds = Collections.emptySet();

    String flashError = (String) session.getAttribute("flashError");
    if (flashError != null) session.removeAttribute("flashError");
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Admin Dashboard — CampusFind</title>
    <link rel="stylesheet" href="<%=ctx%>/css/style.css">
    <link rel="icon" type="image/png" href="<%=ctx%>/favicon.png">
</head>
<body>
<%@ include file="nav.jspf" %>
<main class="container page">
    <h1>Admin Control Center</h1>
    <p class="muted">Monitor platform activity, review reports, manage users, and moderate listings.</p>

    <% if (flashError != null && !flashError.isBlank()) { %>
        <div class="alert"><%=WebUtil.h(flashError)%></div>
    <% } %>
    <% if (request.getParameter("updated") != null) { %>
        <div class="success">Claim decision saved successfully.</div>
    <% } %>
    <% if (request.getParameter("itemReviewed") != null) { %>
        <div class="success">Listing report review saved successfully.</div>
    <% } %>
    <% if (request.getParameter("editReviewed") != null) { %>
        <div class="success">Listing edit proposal review saved successfully.</div>
    <% } %>
    <% if (request.getParameter("itemDeleted") != null) { %>
        <div class="success">Listing deleted successfully.</div>
    <% } %>
    <% if (request.getParameter("userDeleted") != null) { %>
        <div class="success">User account deleted successfully.</div>
    <% } %>
    <% if (request.getParameter("passwordReset") != null) { %>
        <div class="success">User password reset successfully.</div>
    <% } %>
    <% if (request.getParameter("flagUpdated") != null) { %>
        <div class="success">Listing flag resolved successfully.</div>
    <% } %>

    <div class="admin-stats">
        <div class="admin-stat">
            <span>Total Users</span>
            <strong><%=allUsers.size()%></strong>
        </div>
        <div class="admin-stat">
            <span>Total Listings</span>
            <strong><%=items.size()%></strong>
        </div>
        <div class="admin-stat">
            <span>Lost / Found / Returned</span>
            <strong><%=lostCount%> / <%=foundCount%> / <%=returnedCount%></strong>
        </div>
        <div class="admin-stat">
            <span>Pending Claims</span>
            <strong><%=claims.stream().filter(c -> "PENDING".equals(c.getStatus())).count()%></strong>
        </div>
    </div>

    <h2>Registered users</h2>
    <p class="muted">Deleting an account also deletes the items reported by that user and claims linked to that user or those items. You cannot delete your own signed-in account, and the last administrator is protected.</p>

    <div class="user-search-box">
      <input type="text" id="adminUserSearch" class="user-search-input" placeholder="Search users by name or email..." autocomplete="off">
    </div>

    <div class="table-wrap">
      <table id="registeredUsersTable">
        <thead>
          <tr>
            <th>Name</th>
            <th>Email</th>
            <th>Role</th>
            <th>User ID</th>
            <th>Account actions</th>
          </tr>
        </thead>
        <tbody id="registeredUsersTbody">
        <%
        for (User u : allUsers) {
            boolean isOnline = onlineUserIds.contains(u.getId());
        %>
        <tr data-user-id="<%=WebUtil.h(u.getId())%>" data-name="<%=WebUtil.h(u.getName().toLowerCase(java.util.Locale.ROOT))%>" data-email="<%=WebUtil.h(u.getEmail().toLowerCase(java.util.Locale.ROOT))%>">
        <td>
          <strong><%=WebUtil.h(u.getName())%></strong>
          <span class="status-indicator <%=isOnline ? "status-online" : "status-offline"%>">
            <span class="status-dot"></span>
            <span class="status-text"><%=isOnline ? "Online" : "Offline"%></span>
          </span>
          <%if(u.getId().equals(currentAdminId)){%><br><small>Signed-in account</small><%}%>
        </td>
        <td><%=WebUtil.h(u.getEmail())%></td>
        <td><span class="status"><%=WebUtil.h(u.getRole())%></span></td>
        <td><small><%=WebUtil.h(u.getId())%></small></td>
        <td>
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

        <%if(u.getId().equals(currentAdminId)){%><span class="muted">Cannot delete yourself</span><%}else{%><form method="post" action="<%=ctx%>/admin" onsubmit="return confirm('Delete this account? Their reports and related claims will also be removed. This cannot be undone.');"><input type="hidden" name="action" value="deleteUser"><input type="hidden" name="userId" value="<%=WebUtil.h(u.getId())%>"><button class="btn btn-small btn-danger" type="submit">Delete account</button></form><%}%>
        </td></tr>
        <%}%>
        <%if(allUsers.isEmpty()){%>
        <tr class="empty-user-row"><td colspan="5">No users yet.</td></tr>
        <%}%>
        </tbody>
      </table>
    </div>

    <script>
    (function() {
      const searchInput = document.getElementById('adminUserSearch');
      const tbody = document.getElementById('registeredUsersTbody');
      const contextPath = '<%=ctx%>';
      let pollTimer = null;

      function filterLocalRows() {
        const term = searchInput.value.trim().toLowerCase();
        const rows = tbody.querySelectorAll('tr:not(.empty-user-row):not(.no-match-row)');
        let matches = 0;
        rows.forEach(r => {
          const name = r.getAttribute('data-name') || '';
          const email = r.getAttribute('data-email') || '';
          if (!term || name.includes(term) || email.includes(term)) {
            r.style.display = '';
            matches++;
          } else {
            r.style.display = 'none';
          }
        });
        let emptyMsg = tbody.querySelector('.no-match-row');
        if (matches === 0) {
          if (!emptyMsg) {
            emptyMsg = document.createElement('tr');
            emptyMsg.className = 'no-match-row';
            emptyMsg.innerHTML = '<td colspan="5" class="muted" style="text-align:center;padding:20px;">No users match your search criteria.</td>';
            tbody.appendChild(emptyMsg);
          }
        } else if (emptyMsg) {
          emptyMsg.remove();
        }
      }

      if (searchInput) {
        searchInput.addEventListener('input', filterLocalRows);
      }

      async function refreshStatuses() {
        if (document.hidden) return;
        try {
          const q = searchInput ? encodeURIComponent(searchInput.value.trim()) : '';
          const res = await fetch(contextPath + '/admin/users/data?q=' + q);
          if (!res.ok) return;
          const data = await res.json();
          data.users.forEach(u => {
            const row = tbody.querySelector('tr[data-user-id="' + u.id + '"]');
            if (row) {
              const ind = row.querySelector('.status-indicator');
              const txt = row.querySelector('.status-text');
              if (ind && txt) {
                ind.className = 'status-indicator ' + (u.online ? 'status-online' : 'status-offline');
                txt.textContent = u.online ? 'Online' : 'Offline';
              }
            }
          });
        } catch (e) {}
      }

      function startPolling() {
        if (!pollTimer) {
          pollTimer = setInterval(refreshStatuses, 60000);
        }
      }

      function stopPolling() {
        if (pollTimer) {
          clearInterval(pollTimer);
          pollTimer = null;
        }
      }

      document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
          stopPolling();
        } else {
          refreshStatuses();
          startPolling();
        }
      });

      startPolling();
    })();
    </script>

    <!-- Rest of admin dashboard sections (Listing reports, claims, etc.) -->
    <h2>Listing reports</h2>
    <!-- (existing listings table...) -->
</main>
<footer class="footer container">CampusFind Admin Portal</footer>
</body>
</html>

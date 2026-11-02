<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %><%@ page import="java.util.*,com.campus.lostfound.model.*,com.campus.lostfound.util.WebUtil" %><%@ include file="partials.jspf" %>
<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>My claims · CampusFind</title><link rel="icon" type="image/png" href="<%=ctx%>/favicon.png"><link rel="stylesheet" href="<%=ctx%>/css/style.css"></head><body><%@ include file="nav.jspf" %><main class="container page"><p class="eyebrow">YOUR ACTIVITY</p><h1>My claims</h1><p class="muted">Track the status of ownership requests you have submitted.</p><% if(request.getParameter("submitted")!=null){%><div class="success">Claim submitted for review.</div><%}%>
<div class="table-wrap"><table><thead><tr><th>Item ID</th><th>Ownership clue</th><th>Submitted</th><th>Status</th></tr></thead><tbody><% List<Claim> claims=(List<Claim>)request.getAttribute("claims"); for(Claim c:claims){%><tr><td><%=WebUtil.h(c.getItemId())%></td><td><%=WebUtil.h(c.getProof())%></td><td><%=WebUtil.h(c.getCreatedAt())%></td><td><span class="status"><%=WebUtil.h(c.getStatus())%></span></td></tr><%} if(claims.isEmpty()){%><tr><td colspan="4">You haven't submitted any claims yet.</td></tr><%}%></tbody></table></div></main><div id="cf-loading-overlay" role="status" aria-label="Loading" aria-hidden="true">
  <div class="cf-loader-content">
    <div class="cf-logo-wrap">
      <img class="cf-logo" src="<%=ctx%>/images/campusfind-loader.png" alt="">
    </div>
  </div>
</div>
<script src="<%=ctx%>/js/campusfind-loader.js" defer></script>
</body></html>

<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="partials.jspf" %>
<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Register · CampusFind</title><link rel="icon" type="image/png" href="<%=ctx%>/favicon.png"><link rel="stylesheet" href="<%=ctx%>/css/style.css"></head><body>
<%@ include file="nav.jspf" %><main class="narrow panel"><img class="auth-brand-logo theme-aware-logo" data-logo-preference="auto" data-context-path="<%=ctx%>" src="<%=ctx%>/images/campusfind-logo-black.png" alt="CampusFind CF logo"><p class="eyebrow">JOIN YOUR CAMPUS</p><h1>Create account</h1><p class="muted">Use your name and email to get started.</p>
<% if (request.getAttribute("error") != null) { %><div class="alert"><%=WebUtil.h((String)request.getAttribute("error"))%></div><% } %>
<form method="post" action="<%=ctx%>/register"><label>Full name<input name="name" required minlength="2" maxlength="100" autocomplete="name"></label><label>Email<input type="email" name="email" required maxlength="150" autocomplete="email"></label><label>Password<input type="password" name="password" required minlength="10" maxlength="200" autocomplete="new-password"><small>Use at least 10 characters.</small></label><button class="btn" type="submit">Create account</button></form><p>Already registered? <a href="<%=ctx%>/login">Log in</a></p></main><script src="<%=ctx%>/js/password-visibility.js" defer></script><div id="cf-loading-overlay" hidden style="display:none" role="status" aria-label="Loading" aria-hidden="true">
  <div class="cf-loader-content">
    <div class="cf-logo-wrap">
      <img class="cf-logo" src="<%=request.getContextPath()%>/images/campusfind-loader.png" alt="">
    </div>
  </div>
</div>
<script src="<%=request.getContextPath()%>/js/campusfind-loader.js?v=3" defer></script>
</body></html>

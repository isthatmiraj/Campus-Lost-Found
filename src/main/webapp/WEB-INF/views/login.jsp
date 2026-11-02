<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="partials.jspf" %>
<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Login · CampusFind</title><link rel="icon" type="image/png" href="<%=ctx%>/favicon.png"><link rel="stylesheet" href="<%=ctx%>/css/style.css"></head><body>
<%@ include file="nav.jspf" %><main class="narrow panel"><img class="auth-brand-logo theme-aware-logo" data-logo-preference="auto" data-context-path="<%=ctx%>" src="<%=ctx%>/images/campusfind-logo-black.png" alt="CampusFind CF logo"><p class="eyebrow">WELCOME BACK</p><h1>Log in</h1><p class="muted">Sign in to report items and track claims.</p>
<% if (request.getAttribute("error") != null) { %><div class="alert"><%=WebUtil.h((String)request.getAttribute("error"))%></div><% } %>
<form method="post" action="<%=ctx%>/login"><label>Email<input type="email" name="email" required maxlength="150" autocomplete="email"></label><label>Password<input type="password" name="password" required autocomplete="current-password"></label><button class="btn" type="submit">Log in</button></form><p>New here? <a href="<%=ctx%>/register">Create an account</a></p></main><script src="<%=ctx%>/js/password-visibility.js" defer></script><div id="cf-loading-overlay" hidden style="display:none" role="status" aria-label="Loading" aria-hidden="true">
  <div class="cf-loader-content">
    <div class="cf-logo-wrap">
      <img class="cf-logo" src="<%=request.getContextPath()%>/images/campusfind-loader.png" alt="">
    </div>
  </div>
</div>
<script src="<%=request.getContextPath()%>/js/campusfind-loader.js?v=2" defer></script>
</body></html>

<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="partials.jspf" %>
<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Login · CampusFind</title><link rel="stylesheet" href="<%=ctx%>/css/style.css"></head><body>
<%@ include file="nav.jspf" %><main class="narrow panel"><p class="eyebrow">WELCOME BACK</p><h1>Log in</h1><p class="muted">Sign in to report items and track claims.</p>
<% if (request.getAttribute("error") != null) { %><div class="alert"><%=WebUtil.h((String)request.getAttribute("error"))%></div><% } %>
<form method="post" action="<%=ctx%>/login"><label>Email<input type="email" name="email" required maxlength="150" autocomplete="email"></label><label>Password<input type="password" name="password" required autocomplete="current-password"></label><button class="btn" type="submit">Log in</button></form><p>New here? <a href="<%=ctx%>/register">Create an account</a></p></main></body></html>

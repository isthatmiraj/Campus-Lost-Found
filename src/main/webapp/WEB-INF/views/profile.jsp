<%@ page import="com.campus.lostfound.model.User" %>
<%@ page import="com.campus.lostfound.util.WebUtil" %>
<%
    String ctx = request.getContextPath();
    User profileUser = (User) request.getAttribute("profileUser");
    String csrfToken = (String) session.getAttribute("csrfToken");
    boolean rememberMeActive = Boolean.TRUE.equals(request.getAttribute("rememberMeActive"));
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>My Profile · CampusFind</title>
    <link rel="icon" type="image/png" href="<%=ctx%>/favicon.png"><link rel="stylesheet" href="<%=ctx%>/css/style.css">
    <style>
        .profile-page { max-width: 920px; margin: 34px auto; }
        .profile-heading { margin-bottom: 26px; }
        .profile-heading h1 { margin-bottom: 7px; }
        .profile-layout {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 20px;
            align-items: start;
        }
        .profile-card {
            background: var(--surface);
            border: 1px solid var(--line);
            border-radius: 16px;
            padding: 25px;
            box-shadow: 0 5px 20px rgba(0,0,0,.035);
        }
        .profile-card h2 { margin: 0 0 7px; font-size: 19px; }
        .profile-card > p { margin: 0 0 23px; }
        .profile-avatar {
            width: 76px;
            height: 76px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            background: var(--accent);
            color: #071b10;
            font-size: 27px;
            font-weight: 800;
            margin-bottom: 15px;
        }
        .profile-name { font-size: 21px; font-weight: 750; margin-bottom: 4px; }
        .profile-email { color: var(--muted); overflow-wrap: anywhere; }
        .profile-meta {
            display: flex;
            justify-content: space-between;
            gap: 15px;
            padding: 13px 0;
            border-bottom: 1px solid var(--line);
            font-size: 13px;
        }
        .profile-meta:last-child { border-bottom: 0; }
        .profile-meta span:first-child { color: var(--muted); }
        .profile-meta span:last-child { font-weight: 650; text-align: right; }
        .profile-message {
            padding: 13px 16px;
            border-radius: 9px;
            margin-bottom: 18px;
            font-size: 13px;
        }
        .profile-success { background: #e8f8ed; color: #176b35; }
        .profile-error { background: #fff0ef; color: #a52c26; }
        :root[data-theme="dark"] .profile-success { background: #153d27; color: #a6edbb; }
        :root[data-theme="dark"] .profile-error { background: #48211f; color: #ffb5ae; }
        .profile-card .btn { width: 100%; margin-top: 5px; }
        .profile-forget-btn {
            background: rgba(220, 38, 38, .09) !important;
            color: #b91c1c !important;
            border: 1px solid rgba(220, 38, 38, .24) !important;
        }
        .profile-forget-btn:hover { background: rgba(220, 38, 38, .15) !important; }
        :root[data-theme="dark"] .profile-forget-btn {
            background: rgba(248, 113, 113, .10) !important;
            color: #fca5a5 !important;
            border-color: rgba(248, 113, 113, .24) !important;
        }
        :root[data-theme="dark"] .profile-forget-btn:hover { background: rgba(248, 113, 113, .17) !important; }
        .profile-back { display: inline-block; margin-top: 22px; color: var(--muted); }
        @media(max-width: 680px) {
            .profile-page { margin: 23px auto; }
            .profile-layout { grid-template-columns: 1fr; }
            .profile-card { padding: 20px; }
        }
    </style>
</head>
<body>
<%@ include file="nav.jspf" %>

<main class="container profile-page">
    <div class="profile-heading">
        <p class="eyebrow">ACCOUNT SETTINGS</p>
        <h1>My Profile</h1>
        <p class="muted">Manage your personal information and account security.</p>
    </div>

    <% if ("1".equals(request.getParameter("updated"))) { %>
        <div class="profile-message profile-success">Your profile has been updated successfully.</div>
    <% } %>
    <% if ("1".equals(request.getParameter("passwordUpdated"))) { %>
        <div class="profile-message profile-success">Your password has been changed successfully.</div>
    <% } %>
    <% if ("1".equals(request.getParameter("rememberMeRemoved"))) { %>
        <div class="profile-message profile-success">Remember Me has been turned off for this device. Your current session is still active.</div>
    <% } %>
    <% if (request.getAttribute("error") != null) { %>
        <div class="profile-message profile-error"><%=WebUtil.h((String)request.getAttribute("error"))%></div>
    <% } %>

    <div class="profile-layout">
        <section class="profile-card">
            <div class="profile-avatar">
                <%=WebUtil.h(profileUser.getName().substring(0, 1).toUpperCase())%>
            </div>
            <div class="profile-name"><%=WebUtil.h(profileUser.getName())%></div>
            <div class="profile-email"><%=WebUtil.h(profileUser.getEmail())%></div>

            <div style="height:22px"></div>
            <div class="profile-meta">
                <span>Account type</span>
                <span><%=WebUtil.h(profileUser.getRole())%></span>
            </div>
            <div class="profile-meta">
                <span>Account status</span>
                <span>Active</span>
            </div>
            <div class="profile-meta">
                <span>Account ID</span>
                <span><%=WebUtil.h(profileUser.getId())%></span>
            </div>
        </section>

        <section class="profile-card">
            <h2>Personal information</h2>
            <p class="muted">Update the name and email associated with your account.</p>

            <form method="post" action="<%=ctx%>/profile">
                <input type="hidden" name="csrfToken" value="<%=WebUtil.h(csrfToken)%>">
                <input type="hidden" name="action" value="updateProfile">

                <label>
                    Full name
                    <input type="text" name="name"
                           value="<%=WebUtil.h(profileUser.getName())%>"
                           minlength="2" maxlength="100" required autocomplete="name">
                </label>

                <label>
                    Email address
                    <input type="email" name="email"
                           value="<%=WebUtil.h(profileUser.getEmail())%>"
                           maxlength="320" required autocomplete="email">
                </label>

                <button class="btn" type="submit">Save changes</button>
            </form>
        </section>

        <section class="profile-card">
            <h2>Password &amp; security</h2>
            <p class="muted">Choose a strong password you haven't used before.</p>

            <form method="post" action="<%=ctx%>/profile">
                <input type="hidden" name="csrfToken" value="<%=WebUtil.h(csrfToken)%>">
                <input type="hidden" name="action" value="changePassword">

                <label>
                    Current password
                    <input type="password" name="currentPassword"
                           required autocomplete="current-password">
                </label>

                <label>
                    New password
                    <input type="password" name="newPassword"
                           minlength="10" maxlength="200"
                           required autocomplete="new-password">
                    <small>At least 10 characters.</small>
                </label>

                <label>
                    Confirm new password
                    <input type="password" name="confirmPassword"
                           minlength="10" maxlength="200"
                           required autocomplete="new-password">
                </label>

                <button class="btn" type="submit">Change password</button>
            </form>
        </section>

        <section class="profile-card profile-remember-card">
            <h2>Remember this device</h2>
            <% if (rememberMeActive) { %>
                <p class="muted">This browser has a valid Remember Me login. You can stop automatic sign-in on this device without logging out of your current session.</p>
                <form method="post" action="<%=ctx%>/profile"
                      onsubmit="return confirm('Forget this device? You will stay signed in here, but automatic sign-in will be disabled after you log out or your session expires.');">
                    <input type="hidden" name="csrfToken" value="<%=WebUtil.h(csrfToken)%>">
                    <input type="hidden" name="action" value="forgetThisDevice">
                    <button class="btn profile-forget-btn" type="submit">Forget this device</button>
                </form>
            <% } else { %>
                <p class="muted">Remember Me is not currently enabled on this browser. Select “Remember me for 30 days” the next time you log in to enable it.</p>
            <% } %>
        </section>

        <section class="profile-card">
            <h2>Account information</h2>
            <p class="muted">Your CampusFind account details.</p>

            <div class="profile-meta">
                <span>Registered email</span>
                <span><%=WebUtil.h(profileUser.getEmail())%></span>
            </div>
            <div class="profile-meta">
                <span>Role</span>
                <span><%=WebUtil.h(profileUser.getRole())%></span>
            </div>
            <div class="profile-meta">
                <span>Account ID</span>
                <span><%=WebUtil.h(profileUser.getId())%></span>
            </div>
        </section>
    </div>

    <a class="profile-back" href="<%=ctx%>/items">← Back to CampusFind</a>
</main>
<script src="<%=ctx%>/js/password-visibility.js" defer></script><div id="cf-loading-overlay" hidden style="display:none" role="status" aria-label="Loading" aria-hidden="true">
  <div class="cf-loader-content">
    <div class="cf-logo-wrap">
      <img class="cf-logo" src="<%=request.getContextPath()%>/images/campusfind-loader.png" alt="">
    </div>
  </div>
</div>
<script src="<%=request.getContextPath()%>/js/campusfind-loader.js?v=2" defer></script>

<%@ include file="profile-devices.jspf" %>
</body>
</html>

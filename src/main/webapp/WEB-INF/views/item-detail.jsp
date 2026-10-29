<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %><%@ page import="com.campus.lostfound.model.Item,com.campus.lostfound.model.User,com.campus.lostfound.util.WebUtil" %><%@ include file="partials.jspf" %><% Item item=(Item)request.getAttribute("item");User reporter=(User)request.getAttribute("reporter");boolean saved=Boolean.TRUE.equals(request.getAttribute("saved"));String csrfToken=(String)session.getAttribute("csrfToken");if(csrfToken==null){csrfToken=java.util.UUID.randomUUID().toString();session.setAttribute("csrfToken",csrfToken);} %>
<!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title><%=WebUtil.h(item.getTitle())%> · CampusFind</title><link rel="stylesheet" href="<%=ctx%>/css/style.css"></head><body><%@ include file="nav.jspf" %><main class="container page"><p><a href="<%=ctx%>/items">← Back to listings</a></p><article class="panel narrow" style="max-width:760px"><div class="item-top"><span class="type-pill <%= "FOUND".equals(item.getType())?"found":"lost" %>"><%=WebUtil.h(item.getType())%></span><span class="status"><%=WebUtil.h(item.getStatus().replace('_',' '))%></span></div><%if(item.getPhotoName()!=null&&!item.getPhotoName().isBlank()){%><img class="item-detail-photo" src="<%=WebUtil.h(com.campus.lostfound.util.PhotoUtil.imageSrc(item.getPhotoName(),ctx))%>" alt="Photo of <%=WebUtil.h(item.getTitle())%>"><%}%><h1><%=WebUtil.h(item.getTitle())%></h1><p><%=WebUtil.h(item.getDescription())%></p><div class="item-meta"><span>Category: <%=WebUtil.h(item.getCategory())%></span><span>Location: <%=WebUtil.h(item.getLocation())%></span><span>Date: <%=WebUtil.h(item.getDate())%></span></div><%if("FOUND".equals(item.getType())){%><div class="contact-box"><strong>Reporter contact</strong><span><%=reporter==null?"Campus user":WebUtil.h(reporter.getName())%></span><%if(reporter!=null){%><a href="mailto:<%=WebUtil.h(reporter.getEmail())%>"><%=WebUtil.h(reporter.getEmail())%></a><%}%><%if(!item.getContactPhone().isBlank()){%><a href="tel:<%=WebUtil.h(item.getContactPhone())%>"><%=WebUtil.h(item.getContactPhone())%></a><%}%></div><%}%><div class="item-actions"><%if(WebUtil.loggedIn(request)){%><form method="post" action="<%=ctx%>/item"><input type="hidden" name="itemId" value="<%=WebUtil.h(item.getId())%>"><button class="btn btn-light" type="submit"><%=saved?"★ Saved — remove bookmark":"☆ Save item"%></button></form><%if(WebUtil.userId(request).equals(item.getReporterId()) && "APPROVED".equals(item.getApprovalStatus())){%><a class="btn btn-primary" href="<%=ctx%>/edit-item?id=<%=java.net.URLEncoder.encode(item.getId(),java.nio.charset.StandardCharsets.UTF_8)%>">Edit listing</a><%}%><%if(!WebUtil.userId(request).equals(item.getReporterId())){%><button class="btn btn-primary" type="button" id="startChatButton">Start Chat</button><form method="post" action="<%=ctx%>/flag"><input type="hidden" name="itemId" value="<%=WebUtil.h(item.getId())%>"><select name="reason" required><option value="">Report listing…</option><option>Spam</option><option>Incorrect information</option><option>Inappropriate content</option><option>Already returned</option><option>Other</option></select><button class="btn btn-light" type="submit">Flag</button></form><%}%><%}else{%><a class="btn btn-light" href="<%=ctx%>/login">Log in to save or flag</a><%}%><%if(WebUtil.admin(request)){%><form method="post" action="<%=ctx%>/admin" onsubmit="return confirm('Permanently delete this listing, its related claims, and its uploaded photo? This cannot be undone.');"><input type="hidden" name="action" value="deleteItem"><input type="hidden" name="itemId" value="<%=WebUtil.h(item.getId())%>"><button class="btn btn-danger" type="submit">Delete listing</button></form><%}%></div><%if(session.getAttribute("flashMessage")!=null){%><div class="success"><%=WebUtil.h((String)session.getAttribute("flashMessage"))%></div><%session.removeAttribute("flashMessage");}%><%if(session.getAttribute("flashError")!=null){%><div class="alert"><%=WebUtil.h((String)session.getAttribute("flashError"))%></div><%session.removeAttribute("flashError");}%><p class="muted">Listing ID: <%=WebUtil.h(item.getId())%></p></article></main>
<script>
(() => {
    const button = document.getElementById('startChatButton');
    if (!button) return;

    button.addEventListener('click', async () => {
        button.disabled = true;
        const originalText = button.textContent;
        button.textContent = 'Opening chat…';

        try {
            const body = new URLSearchParams({
                itemId: '<%=WebUtil.h(item.getId())%>',
                csrfToken: '<%=WebUtil.h(csrfToken)%>'
            });

            const response = await fetch('<%=ctx%>/chat/start', {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
                },
                body
            });

if (!response.ok) {
    const errorText = await response.text();
    throw new Error(
        'HTTP ' + response.status + ': ' +
        (errorText || 'No error details returned by server')
    );
}
            const data = await response.json();
            if (!data.conversationId) throw new Error('No conversation ID was returned.');

            window.location.href = '<%=ctx%>/chat/room?conversationId='
                + encodeURIComponent(data.conversationId);
        } catch (error) {
            alert('Could not start chat: ' + error.message);
            button.disabled = false;
            button.textContent = originalText;
        }
    });
})();
</script>

</body></html>

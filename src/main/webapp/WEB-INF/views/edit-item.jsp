<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.campus.lostfound.model.Item,com.campus.lostfound.util.WebUtil" %><%@ include file="partials.jspf" %>
<%
Item item = (Item) request.getAttribute("item");
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Edit Listing | CampusFind</title>
    <link rel="stylesheet" href="<%=ctx%>/css/style.css">
</head>
<body>
<%@ include file="nav.jspf" %>
<main class="container">
    <section class="form-card">
        <h1>Edit your listing</h1>
        <p>Your current listing will remain visible while an administrator reviews your proposed changes.</p>

        <% if (request.getAttribute("error") != null) { %>
            <div class="alert alert-error" role="alert">
                <%=WebUtil.h(String.valueOf(request.getAttribute("error")))%>
            </div>
        <% } %>

        <form method="post" enctype="multipart/form-data" action="<%=ctx%>/edit-item">
            <input type="hidden" name="itemId" value="<%=WebUtil.h(item.getId())%>">

            <label for="type">Listing type</label>
            <select id="type" name="type" required>
                <option value="LOST" <%= "LOST".equals(item.getType()) ? "selected" : "" %>>Lost</option>
                <option value="FOUND" <%= "FOUND".equals(item.getType()) ? "selected" : "" %>>Found</option>
            </select>

            <label for="title">Title</label>
            <input id="title" name="title" type="text" minlength="2" maxlength="120"
                   value="<%=WebUtil.h(item.getTitle())%>" required>

            <label for="category">Category</label>
            <select id="category" name="category" required>
                <% String[] categories = {"Accessories", "Electronics", "Books & stationery", "Clothing", "Keys", "Cards & IDs", "Bags", "Other"}; %>
                <% for (String categoryOption : categories) { %>
                    <option value="<%=WebUtil.h(categoryOption)%>" <%=categoryOption.equals(item.getCategory()) ? "selected" : ""%>><%=WebUtil.h(categoryOption)%></option>
                <% } %>
            </select>

            <label for="location">Location</label>
            <input id="location" name="location" type="text" minlength="2" maxlength="150"
                   value="<%=WebUtil.h(item.getLocation())%>" required>

            <label for="date">Date lost or found</label>
            <input id="date" name="date" type="date"
                   value="<%=WebUtil.h(item.getDate())%>" required>

            <label for="description">Description</label>
            <textarea id="description" name="description" minlength="3" maxlength="2000"
                      rows="5" required><%=WebUtil.h(item.getDescription())%></textarea>

            <label for="contactPhone">Contact phone</label>
            <input id="contactPhone" name="contactPhone" type="tel" maxlength="30"
                   value="<%=WebUtil.h(item.getContactPhone())%>">

            <% if (item.getPhotoName() != null && !item.getPhotoName().isBlank()) { %>
                <p>Current photo:</p>
                <img src="<%=ctx%>/photo/<%=WebUtil.h(item.getPhotoName())%>"
                     alt="Current listing photo" style="max-width:220px;height:auto">
            <% } %>

            <label for="photo">Replace photo (optional)</label>
            <input id="photo" name="photo" type="file" accept="image/jpeg,image/png">
            <small>Leave this empty to keep the current photo. Maximum upload size: 5 MB.</small>

            <div class="form-actions">
                <button class="btn" type="submit">Submit changes for approval</button>
                <a class="btn btn-light" href="<%=ctx%>/item?id=<%=WebUtil.h(item.getId())%>">Cancel</a>
            </div>
        </form>
    </section>
</main>
</body>
</html>

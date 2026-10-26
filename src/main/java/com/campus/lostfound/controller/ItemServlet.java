package com.campus.lostfound.controller;

import com.campus.lostfound.model.Item;
import com.campus.lostfound.util.DataStore;
import com.campus.lostfound.util.WebUtil;
import com.campus.lostfound.util.WorkflowStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.Part;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@WebServlet(urlPatterns = {"/items", "/report-item"})
@MultipartConfig(fileSizeThreshold = 1024 * 1024, maxFileSize = 5 * 1024 * 1024, maxRequestSize = 7 * 1024 * 1024)
public class ItemServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getServletPath();
        if ("/report-item".equals(path)) {
            if (!WebUtil.loggedIn(req)) { resp.sendRedirect(req.getContextPath() + "/login"); return; }
            req.getRequestDispatcher("/WEB-INF/views/report-item.jsp").forward(req, resp);
            return;
        }
        List<Item> items = DataStore.get().allItems().stream()
                .filter(i -> "APPROVED".equals(i.getApprovalStatus()))
                .collect(Collectors.toList());
        String q = clean(req.getParameter("q")).toLowerCase(Locale.ROOT);
        String type = clean(req.getParameter("type")).toUpperCase(Locale.ROOT);
        String category = clean(req.getParameter("category"));
        String location = clean(req.getParameter("location")).toLowerCase(Locale.ROOT);
        String status = clean(req.getParameter("status")).toUpperCase(Locale.ROOT);
        String from = clean(req.getParameter("from")); String to = clean(req.getParameter("to"));
        items = items.stream().filter(i -> {
            boolean matchQ = q.isEmpty() || (i.getTitle()+" "+i.getDescription()+" "+i.getCategory()+" "+i.getLocation()).toLowerCase(Locale.ROOT).contains(q);
            boolean matchType = type.isEmpty() || "ALL".equals(type) || i.getType().equals(type);
            boolean matchCategory = category.isEmpty() || i.getCategory().equalsIgnoreCase(category);
            boolean matchLocation = location.isEmpty() || i.getLocation().toLowerCase(Locale.ROOT).contains(location);
            boolean matchStatus = status.isEmpty() || "ALL".equals(status) || i.getStatus().equals(status);
            boolean matchFrom = from.isEmpty() || i.getDate().compareTo(from) >= 0;
            boolean matchTo = to.isEmpty() || i.getDate().compareTo(to) <= 0;
            return matchQ && matchType && matchCategory && matchLocation && matchStatus && matchFrom && matchTo;
        }).collect(Collectors.toList());
        req.setAttribute("items", items);
        java.util.Map<String, com.campus.lostfound.model.User> reporters = new java.util.HashMap<>();
        for (Item item : items) {
            if (!reporters.containsKey(item.getReporterId())) {
                com.campus.lostfound.model.User reporter = DataStore.get().findUserById(item.getReporterId());
                if (reporter != null) reporters.put(item.getReporterId(), reporter);
            }
        }
        req.setAttribute("reporters", reporters);
        req.setAttribute("q", q);
        req.setAttribute("type", type.isEmpty() ? "ALL" : type);
        req.setAttribute("category", category); req.setAttribute("location", location);
        req.setAttribute("status", status.isEmpty()?"ALL":status); req.setAttribute("from", from); req.setAttribute("to", to);
        req.getRequestDispatcher("/WEB-INF/views/items.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (!WebUtil.loggedIn(req)) { resp.sendRedirect(req.getContextPath() + "/login"); return; }
        if (!"/report-item".equals(req.getServletPath())) { resp.sendError(405); return; }
        String title = clean(req.getParameter("title"));
        String description = clean(req.getParameter("description"));
        String category = clean(req.getParameter("category"));
        String type = clean(req.getParameter("type")).toUpperCase(Locale.ROOT);
        String location = clean(req.getParameter("location"));
        String date = clean(req.getParameter("date"));
        String contactPhone = clean(req.getParameter("contactPhone"));
        Part photoPart;
        try { photoPart = req.getPart("photo"); }
        catch (IllegalStateException tooLarge) {
            req.setAttribute("error", "Photo is too large. Please choose an image under 5 MB.");
            req.getRequestDispatcher("/WEB-INF/views/report-item.jsp").forward(req, resp);
            return;
        }
        if (contactPhone.length() > 30 || (!contactPhone.isEmpty() && !contactPhone.matches("[+0-9() .-]{7,30}"))) {
            req.setAttribute("error", "Enter a valid contact phone number, or leave it blank.");
            req.getRequestDispatcher("/WEB-INF/views/report-item.jsp").forward(req, resp);
            return;
        }
        if (title.length() < 2 || title.length() > 120 || description.length() < 3 || description.length() > 2000 ||
                category.length() < 2 || category.length() > 50 || location.length() < 2 || location.length() > 150 ||
                !(type.equals("LOST") || type.equals("FOUND"))) {
            req.setAttribute("error", "Please complete all fields with valid lengths.");
            req.getRequestDispatcher("/WEB-INF/views/report-item.jsp").forward(req, resp);
            return;
        }
        try {
            LocalDate parsed = LocalDate.parse(date);
            if (parsed.isAfter(LocalDate.now()) || parsed.isBefore(LocalDate.now().minusYears(2))) {
                req.setAttribute("error", "Choose a date within the last two years.");
                req.getRequestDispatcher("/WEB-INF/views/report-item.jsp").forward(req, resp);
                return;
            }
            String photoName = savePhoto(photoPart);
            DataStore.get().createItem(WebUtil.userId(req), title, description, category, type, location, date, contactPhone, photoName);
            WorkflowStore.get().audit(WebUtil.userId(req), "ITEM_REPORTED", title + " (" + type + ")");
            resp.sendRedirect(req.getContextPath() + "/items?submitted=1");
        } catch (IllegalArgumentException e) {
            req.setAttribute("error", e.getMessage() == null ? "Please check the submitted details." : e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/report-item.jsp").forward(req, resp);
        } catch (RuntimeException e) {
            req.setAttribute("error", "Please enter a valid date.");
            req.getRequestDispatcher("/WEB-INF/views/report-item.jsp").forward(req, resp);
        }
    }
    private String savePhoto(Part part) throws IOException, ServletException {
        if (part == null || part.getSize() == 0) return "";
        if (part.getSize() > 5L * 1024 * 1024) throw new IllegalArgumentException("Photo must be 5 MB or smaller.");
        String contentType = part.getContentType() == null ? "" : part.getContentType().toLowerCase(Locale.ROOT);
        String extension;
        if ("image/jpeg".equals(contentType) || "image/jpg".equals(contentType)) extension = ".jpg";
        else if ("image/png".equals(contentType)) extension = ".png";
        else throw new IllegalArgumentException("Please upload a JPG or PNG image.");

        Path uploadDir = DataStore.get().getDataDirectory().resolve("uploads");
        Files.createDirectories(uploadDir);
        String photoName = UUID.randomUUID().toString() + extension;
        Path destination = uploadDir.resolve(photoName).normalize();
        if (!destination.getParent().equals(uploadDir.normalize())) throw new IllegalArgumentException("Invalid photo file.");
        try (InputStream in = part.getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1 || image.getWidth() > 8000 || image.getHeight() > 8000)
                throw new IllegalArgumentException("The uploaded file is not a valid JPG or PNG image.");
        }
        try (InputStream in = part.getInputStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return photoName;
    }
    private String clean(String s) { return s == null ? "" : s.trim(); }
}

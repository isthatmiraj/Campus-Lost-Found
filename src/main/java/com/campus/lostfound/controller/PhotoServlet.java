package com.campus.lostfound.controller;

import com.campus.lostfound.util.DataStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@WebServlet(urlPatterns = "/photo/*")
public class PhotoServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String name = req.getPathInfo();
        if (name == null || !name.matches("/[a-fA-F0-9-]+\\.(jpg|png)")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND); return;
        }
        name = name.substring(1);
        Path dir = DataStore.get().getDataDirectory().resolve("uploads").normalize();
        Path file = dir.resolve(name).normalize();
        if (!file.getParent().equals(dir) || !Files.isRegularFile(file)) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND); return;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        resp.setContentType(lower.endsWith(".png") ? "image/png" : "image/jpeg");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("Cache-Control", "public, max-age=86400");
        resp.setContentLengthLong(Files.size(file));
        Files.copy(file, resp.getOutputStream());
    }
}

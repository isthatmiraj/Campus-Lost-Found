package com.campus.lostfound.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class RememberMeUtil {
    public static final String COOKIE_NAME = "campus_remember_me";
    public static final int THIRTY_DAYS_SECONDS = 30 * 24 * 60 * 60;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private RememberMeUtil() {}

    public static String generateToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public static String hashToken(String token) {
        if (token == null || token.isBlank()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }

    public static void setRememberMeCookie(HttpServletRequest req, HttpServletResponse resp, String token) {
        Cookie cookie = new Cookie(COOKIE_NAME, token);
        cookie.setMaxAge(THIRTY_DAYS_SECONDS);
        cookie.setHttpOnly(true);
        cookie.setSecure(isHttps(req));
        cookie.setPath(getEffectivePath(req));
        cookie.setAttribute("SameSite", "Lax");
        resp.addCookie(cookie);
    }

    public static void clearRememberMeCookie(HttpServletRequest req, HttpServletResponse resp) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        cookie.setSecure(isHttps(req));
        cookie.setPath(getEffectivePath(req));
        cookie.setAttribute("SameSite", "Lax");
        resp.addCookie(cookie);
    }

    public static String getRememberMeToken(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (COOKIE_NAME.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    private static boolean isHttps(HttpServletRequest req) {
        String proto = req.getHeader("X-Forwarded-Proto");
        return req.isSecure() || "https".equalsIgnoreCase(proto);
    }

    private static String getEffectivePath(HttpServletRequest req) {
        String ctx = req.getContextPath();
        return (ctx == null || ctx.isBlank()) ? "/" : ctx;
    }
}

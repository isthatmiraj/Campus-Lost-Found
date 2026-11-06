package com.campus.lostfound.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

public final class RememberMeUtil {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private RememberMeUtil() {}

    public static void issueToken(HttpServletResponse resp, String userId) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Cookie cookie = new Cookie("rememberToken", token);
        cookie.setMaxAge((int) Duration.ofDays(30).toSeconds());
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        resp.addCookie(cookie);
    }
}

package com.campus.lostfound.util;

import java.util.Locale;

public final class DeviceInfoUtil {
    private DeviceInfoUtil() {}

    public static String label(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown browser · Unknown device";
        }

        String ua = userAgent.toLowerCase(Locale.ROOT);
        String browser;

        if (ua.contains("edg/")) browser = "Edge";
        else if (ua.contains("opr/") || ua.contains("opera")) browser = "Opera";
        else if (ua.contains("firefox/")) browser = "Firefox";
        else if (ua.contains("chrome/") || ua.contains("crios/")) browser = "Chrome";
        else if (ua.contains("safari/")) browser = "Safari";
        else browser = "Unknown browser";

        String os;
        if (ua.contains("android")) os = "Android";
        else if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) os = "iOS";
        else if (ua.contains("windows")) os = "Windows";
        else if (ua.contains("mac os") || ua.contains("macintosh")) os = "macOS";
        else if (ua.contains("linux")) os = "Linux";
        else os = "Unknown device";

        return browser + " · " + os;
    }
}

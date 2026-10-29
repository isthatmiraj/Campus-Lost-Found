package com.campus.lostfound.util;

/** Builds safe image sources for legacy local photos and ImageKit-hosted photos. */
public final class PhotoUtil {
    private PhotoUtil() {}

    public static String imageSrc(String photoName, String contextPath) {
        if (photoName == null || photoName.isBlank()) return "";
        if (ImageKitStorage.isRemoteUrl(photoName)) {
            // Only allow HTTPS ImageKit URLs; never treat arbitrary user-controlled text as a URL.
            if (photoName.startsWith("https://ik.imagekit.io/")) return photoName;
            return "";
        }
        if (!photoName.matches("[a-fA-F0-9-]+\\.(jpg|png)")) return "";
        String ctx = contextPath == null ? "" : contextPath;
        return ctx + "/photo/" + photoName;
    }
}

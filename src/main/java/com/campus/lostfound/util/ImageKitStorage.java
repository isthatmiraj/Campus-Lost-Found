package com.campus.lostfound.util;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal ImageKit upload API client; credentials are read only from environment variables. */
public final class ImageKitStorage {
    private static final String UPLOAD_ENDPOINT = "https://upload.imagekit.io/api/v1/files/upload";
    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"(https://[^\"]+)\"");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    private ImageKitStorage() {}

    public static String upload(byte[] bytes, String fileName, String mimeType) throws IOException {
        String privateKey = System.getenv("IMAGEKIT_PRIVATE_KEY");
        String endpoint = System.getenv("IMAGEKIT_URL_ENDPOINT");
        if (privateKey == null || privateKey.isBlank() || endpoint == null || endpoint.isBlank()) {
            throw new IOException("Image uploads are not configured. Set IMAGEKIT_PRIVATE_KEY and IMAGEKIT_URL_ENDPOINT.");
        }
        if (!endpoint.startsWith("https://ik.imagekit.io/")) {
            throw new IOException("IMAGEKIT_URL_ENDPOINT must be an ImageKit HTTPS URL endpoint.");
        }
        String base64 = Base64.getEncoder().encodeToString(bytes);
        String form = "file=" + enc(base64)
                + "&fileName=" + enc(fileName)
                + "&useUniqueFileName=true"
                + "&folder=" + enc("/campusfind")
                + "&isPrivateFile=false";
        String auth = Base64.getEncoder().encodeToString((privateKey + ":").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(UPLOAD_ENDPOINT))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response;
        try {
            response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ImageKit upload was interrupted.", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // Do not expose credentials; the API response contains no request authorization header.
            throw new IOException("ImageKit upload failed (HTTP " + response.statusCode() + "). Check the ImageKit credentials and account limits.");
        }
        Matcher matcher = URL_PATTERN.matcher(response.body());
        if (!matcher.find()) throw new IOException("ImageKit upload returned no image URL.");
        String url = matcher.group(1).replace("\\/", "/");
        if (!url.startsWith("https://")) throw new IOException("ImageKit returned an invalid image URL.");
        return url;
    }

    public static boolean isRemoteUrl(String value) {
        return value != null && value.startsWith("https://");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

package com.example.webdavsync;

import okhttp3.*;
import okhttp3.Credentials;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebDAVClient {
    private final OkHttpClient client;
    private final String serverUrl;
    private final String username;
    private final String password;

    public WebDAVClient(String serverUrl, String username, String password) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.username = username;
        this.password = password;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private Request.Builder authRequest() {
        return new Request.Builder()
                .header("Authorization", Credentials.basic(username, password));
    }

    public boolean testConnection() {
        try {
            Request request = authRequest()
                    .url(serverUrl + "/")
                    .method("PROPFIND", null)
                    .header("Depth", "1")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<String> listDirectory(String path) {
        List<String> entries = new ArrayList<>();
        try {
            String cleanPath = path == null ? "" : path;
            if (cleanPath.startsWith("/")) {
                cleanPath = cleanPath.substring(1);
            }
            if (cleanPath.endsWith("/") && cleanPath.length() > 1) {
                cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
            }
            String url = serverUrl + (cleanPath.isEmpty() ? "" : "/" + cleanPath);

            Request request = authRequest()
                    .url(url)
                    .method("PROPFIND", null)
                    .header("Depth", "1")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    entries.add("错误: HTTP " + response.code());
                    return entries;
                }
                String body = response.body().string();
                Pattern pattern = Pattern.compile("<href>([^<]+)</href>");
                Matcher matcher = pattern.matcher(body);
                String baseUrl = url.endsWith("/") ? url : url + "/";

                while (matcher.find()) {
                    String href = matcher.group(1);
                    if (href.equals(baseUrl) || href.equals(url + "/") || href.equals(url)) {
                        continue;
                    }
                    String relative = href.replace(serverUrl + "/", "");
                    if (!relative.isEmpty()) {
                        if (href.endsWith("/") && !relative.endsWith("/")) {
                            relative = relative + "/";
                        }
                        entries.add(relative);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            entries.add("网络错误: " + e.getMessage());
        }
        return entries;
    }

    public boolean uploadFile(String remoteDir, File localFile) {
        try {
            String remotePath = (remoteDir == null || remoteDir.isEmpty()) ? localFile.getName()
                    : remoteDir + "/" + localFile.getName();
            remotePath = remotePath.replace("//", "/");
            String url = serverUrl + "/" + remotePath;

            MediaType mediaType = MediaType.parse("application/octet-stream");
            RequestBody body = RequestBody.create(mediaType, localFile);
            Request request = authRequest()
                    .url(url)
                    .put(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}

// 全局单例持有者
class WebDAVClientHolder {
    private static WebDAVClient client;

    public static void setClient(WebDAVClient c) {
        client = c;
    }

    public static WebDAVClient getClient() {
        return client;
    }
}

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
        // 确保 serverUrl 不以 / 结尾
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.username = username;
        this.password = password;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public String getServerUrl() {
        return serverUrl;
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

    /**
     * 列出指定路径下的文件和目录
     * @param path 相对路径，如 "" 或 "photos" 或 "photos/2024"
     * @return 文件名列表（目录以 / 结尾）
     */
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
                    // 跳过自身
                    if (href.equals(baseUrl) || href.equals(url + "/") || href.equals(url)) {
                        continue;
                    }
                    // 提取相对路径
                    String relative = href.replace(serverUrl + "/", "");
                    if (!relative.isEmpty()) {
                        // 确保目录以 / 结尾
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

    public boolean fileExists(String remotePath) {
        try {
            String url = serverUrl + "/" + remotePath.replace("//", "/");
            Request request = authRequest()
                    .url(url)
                    .head()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            return false;
        }
    }

    public boolean uploadFile(String remoteDir, File localFile) {
        try {
            String remotePath = remoteDir == null || remoteDir.isEmpty() ? localFile.getName()
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

    public boolean createDirectory(String remoteDir) {
        try {
            String url = serverUrl + "/" + remoteDir.replace("//", "/") + "/";
            Request request = authRequest()
                    .url(url)
                    .method("MKCOL", null)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful() || response.code() == 405; // 405 表示已存在
            }
        } catch (IOException e) {
            return false;
        }
    }
}

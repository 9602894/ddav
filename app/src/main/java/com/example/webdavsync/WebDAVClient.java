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
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length()-1) : serverUrl;
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

    // 列出指定路径下的文件和目录（返回相对路径名）
    public List<String> listDirectory(String path) {
        List<String> entries = new ArrayList<>();
        try {
            // 规范化路径
            String cleanPath = path.startsWith("/") ? path.substring(1) : path;
            cleanPath = cleanPath.endsWith("/") ? cleanPath.substring(0, cleanPath.length()-1) : cleanPath;
            String url = serverUrl + (cleanPath.isEmpty() ? "" : "/" + cleanPath);
            Request request = authRequest()
                    .url(url)
                    .method("PROPFIND", null)
                    .header("Depth", "1")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String msg = "HTTP " + response.code() + ": " + response.message();
                    entries.add("错误: " + msg);
                    return entries;
                }
                String body = response.body().string();
                // 提取 href 标签内容
                Pattern pattern = Pattern.compile("<href>([^<]+)</href>");
                Matcher matcher = pattern.matcher(body);
                String basePath = url.endsWith("/") ? url : url + "/";
                while (matcher.find()) {
                    String href = matcher.group(1);
                    // 跳过自身和父目录
                    if (href.equals(basePath) || href.equals(url + "/") || href.equals(url)) continue;
                    String relative = href.replace(serverUrl + "/", "");
                    if (!relative.isEmpty()) {
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
            String remotePath = remoteDir.endsWith("/") ? remoteDir + localFile.getName() : remoteDir + "/" + localFile.getName();
            String url = serverUrl + "/" + remotePath.replace("//", "/");
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
                return response.isSuccessful();
            }
        } catch (IOException e) {
            return false;
        }
    }
}

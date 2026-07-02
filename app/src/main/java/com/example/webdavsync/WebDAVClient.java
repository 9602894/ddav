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
        this.serverUrl = serverUrl;
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
                    .url(serverUrl)
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

    // 列出远程文件（返回文件名列表）
    public List<String> listFiles() {
        try {
            Request request = authRequest()
                    .url(serverUrl)
                    .method("PROPFIND", null)
                    .header("Depth", "1")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return null;
                String body = response.body().string();
                // 简单正则解析 href 标签，提取文件名（仅演示，可优化）
                List<String> files = new ArrayList<>();
                Pattern pattern = Pattern.compile("<href>.*?/([^/]+)</href>");
                Matcher matcher = pattern.matcher(body);
                while (matcher.find()) {
                    String name = matcher.group(1);
                    if (!name.isEmpty() && !name.equals("/")) {
                        files.add(name);
                    }
                }
                return files;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // 上传文件
    public boolean uploadFile(File localFile) {
        try {
            String remotePath = serverUrl + "/" + localFile.getName();
            MediaType mediaType = MediaType.parse("application/octet-stream");
            RequestBody body = RequestBody.create(mediaType, localFile);
            Request request = authRequest()
                    .url(remotePath)
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

package com.example.webdavsync;

import okhttp3.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    private static OkHttpClient okHttpClient;

    public WebDAVClient(String serverUrl, String username, String password) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        updateOkHttpClient(this.username, this.password);
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }

    private static OkHttpClient buildOkHttpClient(String user, String pass) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS);
        if (!user.isEmpty() && !pass.isEmpty()) {
            String credential = Credentials.basic(user, pass);
            builder.addInterceptor(chain -> {
                Request original = chain.request();
                Request request = original.newBuilder()
                        .header("Authorization", credential)
                        .build();
                return chain.proceed(request);
            });
        }
        return builder.build();
    }

    public static OkHttpClient getOkHttpClient() {
        if (okHttpClient == null) {
            okHttpClient = buildOkHttpClient("", "");
        }
        return okHttpClient;
    }

    public static void updateOkHttpClient(String username, String password) {
        okHttpClient = buildOkHttpClient(username, password);
    }

    public String getServerUrl() { return serverUrl; }

    private Request.Builder authRequest() {
        Request.Builder builder = new Request.Builder();
        if (!username.isEmpty() && !password.isEmpty()) {
            String credential = Credentials.basic(username, password);
            builder.header("Authorization", credential);
        }
        return builder;
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
            String cleanPath = (path == null) ? "" : path;
            if (cleanPath.startsWith("/")) cleanPath = cleanPath.substring(1);
            if (cleanPath.endsWith("/") && cleanPath.length() > 1)
                cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
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
                Pattern pattern = Pattern.compile("<[a-zA-Z0-9:]*href>([^<]+)</[a-zA-Z0-9:]*href>");
                Matcher matcher = pattern.matcher(body);
                boolean found = false;
                while (matcher.find()) {
                    String href = matcher.group(1);
                    if (href.equals(url + "/") || href.equals(url) || href.equals(serverUrl + "/")) continue;
                    String relative = href.replace(serverUrl + "/", "");
                    if (!relative.isEmpty()) {
                        if (href.endsWith("/") && !relative.endsWith("/")) relative += "/";
                        entries.add(relative);
                        found = true;
                    }
                }
                if (!found) {
                    Pattern pattern2 = Pattern.compile("<href>([^<]+)</href>");
                    Matcher matcher2 = pattern2.matcher(body);
                    while (matcher2.find()) {
                        String href = matcher2.group(1);
                        if (href.equals(url + "/") || href.equals(url) || href.equals(serverUrl + "/")) continue;
                        String relative = href.replace(serverUrl + "/", "");
                        if (!relative.isEmpty()) {
                            if (href.endsWith("/") && !relative.endsWith("/")) relative += "/";
                            entries.add(relative);
                            found = true;
                        }
                    }
                }
                if (!found) {
                    if (body.contains("empty") || body.contains("Empty"))
                        entries.add("(空目录)");
                    else
                        entries.add("警告: 无法解析");
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
            Request request = authRequest().url(url).put(body).build();
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean downloadFile(String remotePath, File destFile) {
        try {
            String url = serverUrl + "/" + remotePath.replace("//", "/");
            Request request = authRequest().url(url).get().build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return false;
                InputStream inputStream = response.body().byteStream();
                FileOutputStream fos = new FileOutputStream(destFile);
                byte[] buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) != -1) fos.write(buffer, 0, len);
                fos.close();
                inputStream.close();
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 删除远程文件
    public boolean deleteFile(String remotePath) {
        try {
            String url = serverUrl + "/" + remotePath.replace("//", "/");
            Request request = authRequest().url(url).delete().build();
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}

class WebDAVClientHolder {
    private static WebDAVClient client;
    public static void setClient(WebDAVClient c) { client = c; }
    public static WebDAVClient getClient() { return client; }
}

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
     * @return 文件名列表（目录以 / 结尾），若出错则返回包含错误信息的列表
     */
    public List<String> listDirectory(String path) {
        List<String> entries = new ArrayList<>();
        try {
            // 规范化路径
            String cleanPath = (path == null) ? "" : path;
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
                    entries.add("错误: HTTP " + response.code() + " " + response.message());
                    return entries;
                }

                String body = response.body().string();
                // 保存原始响应（用于调试，但这里不保留）
                // 尝试两种解析模式

                // 模式1：标准 <href> 标签（可能带命名空间）
                Pattern pattern = Pattern.compile("<[a-zA-Z0-9:]*href>([^<]+)</[a-zA-Z0-9:]*href>");
                Matcher matcher = pattern.matcher(body);
                boolean found = false;
                while (matcher.find()) {
                    String href = matcher.group(1);
                    // 对 href 进行解码（如果包含 % 编码，但多数情况不需要）
                    // 跳过当前目录本身
                    if (href.equals(url + "/") || href.equals(url) || href.equals(serverUrl + "/")) {
                        continue;
                    }
                    // 提取相对路径
                    String relative = href.replace(serverUrl + "/", "");
                    if (!relative.isEmpty()) {
                        // 如果 href 以 / 结尾，但 relative 没有，则补上
                        if (href.endsWith("/") && !relative.endsWith("/")) {
                            relative = relative + "/";
                        }
                        entries.add(relative);
                        found = true;
                    }
                }

                // 如果模式1没有匹配到任何条目，尝试模式2：直接匹配文件名（简单响应）
                if (!found) {
                    Pattern pattern2 = Pattern.compile("<href>([^<]+)</href>");
                    Matcher matcher2 = pattern2.matcher(body);
                    while (matcher2.find()) {
                        String href = matcher2.group(1);
                        if (href.equals(url + "/") || href.equals(url) || href.equals(serverUrl + "/")) {
                            continue;
                        }
                        String relative = href.replace(serverUrl + "/", "");
                        if (!relative.isEmpty()) {
                            if (href.endsWith("/") && !relative.endsWith("/")) {
                                relative = relative + "/";
                            }
                            entries.add(relative);
                            found = true;
                        }
                    }
                }

                if (!found) {
                    // 可能服务器返回了空目录或响应格式非常特殊
                    // 检查 body 是否包含 "empty" 或类似信息
                    if (body.contains("empty") || body.contains("Empty")) {
                        entries.add("(空目录)");
                    } else {
                        // 返回原始响应片段（供调试）
                        String snippet = body.length() > 200 ? body.substring(0, 200) + "..." : body;
                        entries.add("警告: 无法解析文件列表，服务器响应: " + snippet);
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

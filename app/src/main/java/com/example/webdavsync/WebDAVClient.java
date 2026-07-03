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
            // 提取 href
            Pattern pattern = Pattern.compile("<[a-zA-Z0-9:]*href>([^<]+)</[a-zA-Z0-9:]*href>");
            Matcher matcher = pattern.matcher(body);
            boolean found = false;
            while (matcher.find()) {
                String href = matcher.group(1);
                if (href.equals(url + "/") || href.equals(url) || href.equals(serverUrl + "/")) continue;
                String relative = href.replace(serverUrl + "/", "");
                if (!relative.isEmpty()) {
                    // ★ 关键修复：如果 href 以 / 结尾，标记为目录
                    if (href.endsWith("/") && !relative.endsWith("/")) {
                        relative += "/";
                    }
                    entries.add(relative);
                    found = true;
                }
            }
            if (!found) {
                // 备选解析
                Pattern pattern2 = Pattern.compile("<href>([^<]+)</href>");
                Matcher matcher2 = pattern2.matcher(body);
                while (matcher2.find()) {
                    String href = matcher2.group(1);
                    if (href.equals(url + "/") || href.equals(url) || href.equals(serverUrl + "/")) continue;
                    String relative = href.replace(serverUrl + "/", "");
                    if (!relative.isEmpty()) {
                        if (href.endsWith("/") && !relative.endsWith("/")) {
                            relative += "/";
                        }
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

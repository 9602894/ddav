package com.example.webdavsync;

public class WebDAVClientHolder {
    private static WebDAVClient client;
    private static String configName;

    public static void setClient(WebDAVClient c, String name) {
        client = c;
        configName = name;
    }

    public static WebDAVClient getClient() {
        return client;
    }

    public static String getConfigName() {
        return configName;
    }

    public static void clear() {
        client = null;
        configName = null;
    }
}

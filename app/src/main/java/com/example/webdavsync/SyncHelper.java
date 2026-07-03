package com.example.webdavsync;

import android.os.Environment;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SyncHelper {

    /**
     * 获取本地 Download 目录下的所有文件
     */
    public static List<File> getLocalFiles() {
        List<File> files = new ArrayList<>();
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir != null && downloadDir.exists() && downloadDir.isDirectory()) {
            File[] list = downloadDir.listFiles();
            if (list != null) {
                for (File f : list) {
                    if (f.isFile()) {
                        files.add(f);
                    }
                }
            }
        }
        return files;
    }

    /**
     * 获取本地 Download 目录下的所有文件（带路径）
     */
    public static List<String> getLocalFileNames() {
        List<String> names = new ArrayList<>();
        for (File f : getLocalFiles()) {
            names.add(f.getName());
        }
        return names;
    }

    /**
     * 同步所有本地文件到云端（增量）
     */
    public static SyncResult syncAll(WebDAVClient client, String remoteDir) {
        List<File> localFiles = getLocalFiles();
        List<String> remoteFiles = client.listDirectory(remoteDir);

        // 提取远程文件名
        List<String> remoteNames = new ArrayList<>();
        for (String item : remoteFiles) {
            if (!item.endsWith("/")) {
                // 提取文件名
                int lastSlash = item.lastIndexOf('/');
                String name = lastSlash > 0 ? item.substring(lastSlash + 1) : item;
                remoteNames.add(name);
            }
        }

        int uploaded = 0;
        int skipped = 0;
        int failed = 0;

        for (File f : localFiles) {
            if (remoteNames.contains(f.getName())) {
                skipped++;
            } else {
                boolean ok = client.uploadFile(remoteDir, f);
                if (ok) {
                    uploaded++;
                } else {
                    failed++;
                }
            }
        }

        return new SyncResult(uploaded, skipped, failed);
    }

    public static class SyncResult {
        public final int uploaded;
        public final int skipped;
        public final int failed;

        public SyncResult(int uploaded, int skipped, int failed) {
            this.uploaded = uploaded;
            this.skipped = skipped;
            this.failed = failed;
        }

        public String getMessage() {
            return "上传: " + uploaded + ", 跳过: " + skipped + ", 失败: " + failed;
        }
    }
}

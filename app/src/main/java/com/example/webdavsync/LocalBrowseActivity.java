package com.example.webdavsync;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LocalBrowseActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 100;

    private RecyclerView rvLocal;
    private TextView tvLocalPath, tvLocalStatus, tvSelectedCount;
    private Button btnUpload;

    private FileAdapter adapter;
    private WebDAVClient client;
    private Set<String> remoteFileNames = new HashSet<>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_browse);

        rvLocal = findViewById(R.id.rv_local_files);
        tvLocalPath = findViewById(R.id.tv_local_path);
        tvLocalStatus = findViewById(R.id.tv_local_status);
        tvSelectedCount = findViewById(R.id.tv_selected_count);
        btnUpload = findViewById(R.id.btn_local_upload);

        // 请求存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] perms = {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, perms, REQUEST_PERMISSIONS);
                return;
            }
        }

        client = WebDAVClientHolder.getClient();
        if (client == null) {
            Toast.makeText(this, "请先在主界面连接服务器", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 设置网格布局 - 3列
        rvLocal.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new FileAdapter(this, true);
        rvLocal.setAdapter(adapter);

        // 更新选中计数
        adapter.setOnItemClickListener((item, position) -> updateSelectedCount());

        // 加载相册
        loadLocalPhotos();

        btnUpload.setOnClickListener(v -> uploadSelected());
    }

    private void loadLocalPhotos() {
        tvLocalPath.setText("📷 相册");
        tvLocalStatus.setText("加载中...");

        new Thread(() -> {
            // 获取远程文件列表
            List<String> remoteItems = client.listDirectory("");
            remoteFileNames.clear();
            for (String item : remoteItems) {
                if (!item.endsWith("/")) {
                    remoteFileNames.add(item);
                }
            }

            // 扫描相册目录（DCIM/Camera 和 Pictures）
            List<File> photoDirs = new ArrayList<>();
            File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            if (dcimDir != null && dcimDir.exists()) {
                File cameraDir = new File(dcimDir, "Camera");
                if (cameraDir.exists() && cameraDir.isDirectory()) {
                    photoDirs.add(cameraDir);
                }
                photoDirs.add(dcimDir);
            }
            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            if (picturesDir != null && picturesDir.exists()) {
                photoDirs.add(picturesDir);
            }

            // 收集所有媒体文件
            List<FileAdapter.FileItem> items = new ArrayList<>();
            Set<String> addedNames = new HashSet<>();

            for (File dir : photoDirs) {
                if (dir == null || !dir.exists()) continue;
                File[] files = dir.listFiles();
                if (files == null) continue;
                for (File f : files) {
                    if (f.isFile() && isMediaFile(f.getName())) {
                        // 避免重复
                        if (addedNames.contains(f.getAbsolutePath())) continue;
                        addedNames.add(f.getAbsolutePath());

                        FileAdapter.FileItem fi = new FileAdapter.FileItem(f.getName());
                        fi.file = f;
                        fi.displayName = f.getName();
                        fi.fileSize = f.length();
                        fi.lastModified = f.lastModified();
                        fi.isVideo = isVideoFile(f.getName());
                        fi.isOnCloud = remoteFileNames.contains(f.getName());
                        items.add(fi);
                    }
                }
            }

            // 按修改时间排序（最新的在前）
            items.sort((a, b) -> Long.compare(b.lastModified, a.lastModified));

            final List<FileAdapter.FileItem> finalItems = items;
            mainHandler.post(() -> {
                tvLocalStatus.setText(finalItems.size() + " 项");
                adapter.setItems(finalItems);
                updateSelectedCount();
            });
        }).start();
    }

    private boolean isMediaFile(String name) {
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        return ext.matches("jpg|jpeg|png|gif|bmp|webp|mp4|3gp|avi|mkv|mov");
    }

    private boolean isVideoFile(String name) {
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        return ext.matches("mp4|3gp|avi|mkv|mov");
    }

    private void updateSelectedCount() {
        int count = 0;
        for (FileAdapter.FileItem fi : adapter.getItems()) {
            if (fi.isSelected) count++;
        }
        tvSelectedCount.setText("已选 " + count + " 项");
    }

    private void uploadSelected() {
        List<FileAdapter.FileItem> selected = new ArrayList<>();
        for (FileAdapter.FileItem fi : adapter.getItems()) {
            if (fi.isSelected && fi.file != null && fi.file.isFile()) {
                selected.add(fi);
            }
        }

        if (selected.isEmpty()) {
            Toast.makeText(this, "请先选中文件", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "开始上传 " + selected.size() + " 个文件", Toast.LENGTH_SHORT).show();
        btnUpload.setEnabled(false);

        new Thread(() -> {
            int success = 0, fail = 0;
            for (FileAdapter.FileItem fi : selected) {
                boolean ok = client.uploadFile("", fi.file);
                if (ok) success++; else fail++;
            }

            final int finalSuccess = success;
            final int finalFail = fail;
            mainHandler.post(() -> {
                btnUpload.setEnabled(true);
                tvLocalStatus.setText("上传完成: 成功 " + finalSuccess + ", 失败 " + finalFail);
                Toast.makeText(LocalBrowseActivity.this,
                        "上传完成: 成功 " + finalSuccess + ", 失败 " + finalFail,
                        Toast.LENGTH_LONG).show();
                // 刷新列表
                loadLocalPhotos();
            });
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadLocalPhotos();
            } else {
                Toast.makeText(this, "需要存储权限才能访问相册", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}

package com.example.webdavsync;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 100;

    private FrameLayout contentFrame;
    private RecyclerView rvPhotos;
    private TextView tvConnectionStatus, tvItemCount, tvSelectedCount;
    private Button btnUpload, btnDeleteLocal, btnSyncAll;
    private EditText etRemoteDir;
    private ImageView ivSettings;
    private BottomNavigationView bottomNav;

    private PhotoAdapter photoAdapter;
    private AllFilesAdapter allFilesAdapter;
    private boolean isPhotoView = true; // 当前为本地相册

    private WebDAVClient webdavClient;
    private SharedPreferences prefs;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Set<String> remoteFileNames = new HashSet<>();
    private String currentConnectionName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        prefs = getSharedPreferences("webdav_prefs", MODE_PRIVATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, perms, REQUEST_PERMISSIONS);
                return;
            }
        }

        setupRecyclerView();
        loadCurrentConnection();
        showLocalPhotos();
        setupListeners();
    }

    private void initViews() {
        contentFrame = findViewById(R.id.content_frame);
        rvPhotos = new RecyclerView(this);
        rvPhotos.setLayoutManager(new GridLayoutManager(this, 3));
        contentFrame.addView(rvPhotos);

        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        tvItemCount = findViewById(R.id.tv_item_count);
        tvSelectedCount = findViewById(R.id.tv_selected_count);
        etRemoteDir = findViewById(R.id.et_remote_dir);
        ivSettings = findViewById(R.id.iv_settings);
        bottomNav = findViewById(R.id.bottom_navigation);
        btnUpload = findViewById(R.id.btn_upload);
        btnDeleteLocal = findViewById(R.id.btn_delete_local);
        btnSyncAll = findViewById(R.id.btn_sync_all);
    }

    private void setupRecyclerView() {
        photoAdapter = new PhotoAdapter(this);
        photoAdapter.setShowCloudBadge(true);
        allFilesAdapter = new AllFilesAdapter(this);
        allFilesAdapter.setShowCloudBadge(true);
    }

    private void showLocalPhotos() {
        rvPhotos.setAdapter(photoAdapter);
        isPhotoView = true;
        loadLocalPhotos();
        updateSelectedCount();
    }

    private void showLocalAll() {
        rvPhotos.setAdapter(allFilesAdapter);
        isPhotoView = false;
        loadAllFiles();
        updateSelectedCount();
    }

    private void loadCurrentConnection() {
        currentConnectionName = prefs.getString("current_connection", "");
        if (!currentConnectionName.isEmpty()) {
            String server = prefs.getString("conn_" + currentConnectionName + "_server", "");
            String username = prefs.getString("conn_" + currentConnectionName + "_username", "");
            String password = prefs.getString("conn_" + currentConnectionName + "_password", "");
            if (!server.isEmpty()) {
                webdavClient = new WebDAVClient(server, username, password);
                WebDAVClientHolder.setClient(webdavClient);
                tvConnectionStatus.setText("🔗 " + server);
                new Thread(() -> {
                    boolean ok = webdavClient.testConnection();
                    mainHandler.post(() -> {
                        if (ok) {
                            tvConnectionStatus.setText("✅ 已连接: " + currentConnectionName);
                            WebDAVClient.updateOkHttpClient(username, password);
                            loadRemoteFileList();
                        } else {
                            tvConnectionStatus.setText("⚠️ 连接失败: " + currentConnectionName);
                        }
                    });
                }).start();
                return;
            }
        }
        tvConnectionStatus.setText("⚪ 未连接");
    }

    private void loadRemoteFileList() {
        if (webdavClient == null) return;
        new Thread(() -> {
            List<String> files = webdavClient.listDirectory("");
            remoteFileNames.clear();
            for (String f : files) {
                if (!f.endsWith("/")) {
                    remoteFileNames.add(f);
                }
            }
            mainHandler.post(() -> {
                for (PhotoAdapter.PhotoItem item : photoAdapter.getItems()) {
                    item.isOnCloud = remoteFileNames.contains(item.name);
                }
                photoAdapter.notifyDataSetChanged();

                for (AllFilesAdapter.FileItem item : allFilesAdapter.getItems()) {
                    item.isOnCloud = remoteFileNames.contains(item.name);
                }
                allFilesAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void loadLocalPhotos() {
        tvItemCount.setText("加载中...");
        new Thread(() -> {
            List<PhotoAdapter.PhotoItem> items = new ArrayList<>();
            String[] projection = {MediaStore.Images.Media.DATA, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_MODIFIED};
            Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection, null, null, MediaStore.Images.Media.DATE_MODIFIED + " DESC");
            if (cursor != null) {
                int dataIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                int nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                int dateIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED);
                while (cursor.moveToNext()) {
                    String path = cursor.getString(dataIdx);
                    String name = cursor.getString(nameIdx);
                    long date = cursor.getLong(dateIdx);
                    if (path != null) {
                        File file = new File(path);
                        if (file.exists()) {
                            PhotoAdapter.PhotoItem item = new PhotoAdapter.PhotoItem(name);
                            item.file = file;
                            item.dateModified = date;
                            item.isOnCloud = remoteFileNames.contains(name);
                            item.displayName = name;
                            String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
                            item.isVideo = ext.matches("mp4|3gp|avi|mkv|mov|webm");
                            items.add(item);
                        }
                    }
                }
                cursor.close();
            }
            final List<PhotoAdapter.PhotoItem> finalItems = items;
            mainHandler.post(() -> {
                photoAdapter.setItems(finalItems);
                tvItemCount.setText(finalItems.size() + " 张");
                updateSelectedCount();
            });
        }).start();
    }

    private void loadAllFiles() {
        tvItemCount.setText("加载中...");
        new Thread(() -> {
            List<AllFilesAdapter.FileItem> items = new ArrayList<>();
            String[] projection = {MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.DATE_MODIFIED};
            Cursor cursor = getContentResolver().query(MediaStore.Files.getContentUri("external"),
                    projection, null, null, MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC");
            if (cursor != null) {
                int dataIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA);
                int nameIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME);
                int dateIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED);
                while (cursor.moveToNext()) {
                    String path = cursor.getString(dataIdx);
                    String name = cursor.getString(nameIdx);
                    long date = cursor.getLong(dateIdx);
                    if (path != null) {
                        File file = new File(path);
                        if (file.exists()) {
                            AllFilesAdapter.FileItem item = new AllFilesAdapter.FileItem(name);
                            item.file = file;
                            item.dateModified = date;
                            item.isOnCloud = remoteFileNames.contains(name);
                            item.displayName = name;
                            items.add(item);
                        }
                    }
                }
                cursor.close();
            }
            final List<AllFilesAdapter.FileItem> finalItems = items;
            mainHandler.post(() -> {
                allFilesAdapter.setItems(finalItems);
                tvItemCount.setText(finalItems.size() + " 个");
                updateSelectedCount();
            });
        }).start();
    }

    private void updateSelectedCount() {
        int count = 0;
        if (isPhotoView) {
            for (PhotoAdapter.PhotoItem item : photoAdapter.getItems()) {
                if (item.isSelected) count++;
            }
        } else {
            for (AllFilesAdapter.FileItem item : allFilesAdapter.getItems()) {
                if (item.isSelected) count++;
            }
        }
        tvSelectedCount.setText("已选 " + count + " 项");
    }

    private void setupListeners() {
        ivSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_local_photos) {
                showLocalPhotos();
                return true;
            } else if (id == R.id.nav_local_all) {
                showLocalAll();
                return true;
            } else if (id == R.id.nav_cloud_photos || id == R.id.nav_cloud_all) {
                if (webdavClient == null) {
                    Toast.makeText(this, "请先配置并连接 WebDAV", Toast.LENGTH_SHORT).show();
                    return false;
                }
                Intent intent = new Intent(this, CloudActivity.class);
                intent.putExtra("type", id == R.id.nav_cloud_photos ? "photo" : "all");
                startActivity(intent);
                return true;
            }
            return false;
        });

        // 适配器点击事件
        photoAdapter.setOnItemClickListener((item, position) -> {
            item.isSelected = !item.isSelected;
            photoAdapter.notifyItemChanged(position);
            updateSelectedCount();
        });

        allFilesAdapter.setOnItemClickListener((item, position) -> {
            item.isSelected = !item.isSelected;
            allFilesAdapter.notifyItemChanged(position);
            updateSelectedCount();
        });

        // 上传选中的文件
        btnUpload.setOnClickListener(v -> uploadSelected());
        // 删除选中的本地文件
        btnDeleteLocal.setOnClickListener(v -> deleteSelectedLocal());
        // 一键同步所有（增量）
        btnSyncAll.setOnClickListener(v -> syncAllToCloud());
    }

    private void uploadSelected() {
        List<File> filesToUpload = new ArrayList<>();
        if (isPhotoView) {
            for (PhotoAdapter.PhotoItem item : photoAdapter.getItems()) {
                if (item.isSelected && item.file != null && item.file.exists()) {
                    filesToUpload.add(item.file);
                }
            }
        } else {
            for (AllFilesAdapter.FileItem item : allFilesAdapter.getItems()) {
                if (item.isSelected && item.file != null && item.file.exists()) {
                    filesToUpload.add(item.file);
                }
            }
        }
        if (filesToUpload.isEmpty()) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }
        if (webdavClient == null) {
            Toast.makeText(this, "未连接 WebDAV", Toast.LENGTH_SHORT).show();
            return;
        }
        String remoteDir = etRemoteDir.getText().toString().trim();
        final String finalRemoteDir = remoteDir.isEmpty() ? "" : remoteDir;

        // 检查/创建目录
        new Thread(() -> {
            if (!finalRemoteDir.isEmpty()) {
                // 检查目录是否存在
                List<String> dirs = webdavClient.listDirectory("");
                boolean exists = false;
                for (String d : dirs) {
                    if (d.equals(finalRemoteDir + "/") || d.equals(finalRemoteDir)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    boolean created = webdavClient.createDirectory(finalRemoteDir);
                    if (!created) {
                        mainHandler.post(() -> {
                            Toast.makeText(MainActivity.this, "目录创建失败", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }
                }
            }
            // 上传文件
            int success = 0, fail = 0;
            for (File f : filesToUpload) {
                boolean ok = webdavClient.uploadFile(finalRemoteDir, f);
                if (ok) success++; else fail++;
            }
            final int finalSuccess = success;
            final int finalFail = fail;
            mainHandler.post(() -> {
                Toast.makeText(MainActivity.this, "上传完成: 成功 " + finalSuccess + ", 失败 " + finalFail, Toast.LENGTH_LONG).show();
                loadRemoteFileList(); // 刷新标记
                // 清除选中
                if (isPhotoView) {
                    for (PhotoAdapter.PhotoItem item : photoAdapter.getItems()) item.isSelected = false;
                    photoAdapter.notifyDataSetChanged();
                } else {
                    for (AllFilesAdapter.FileItem item : allFilesAdapter.getItems()) item.isSelected = false;
                    allFilesAdapter.notifyDataSetChanged();
                }
                updateSelectedCount();
            });
        }).start();
    }

    private void deleteSelectedLocal() {
        List<File> filesToDelete = new ArrayList<>();
        if (isPhotoView) {
            for (PhotoAdapter.PhotoItem item : photoAdapter.getItems()) {
                if (item.isSelected && item.file != null && item.file.exists()) {
                    filesToDelete.add(item.file);
                }
            }
        } else {
            for (AllFilesAdapter.FileItem item : allFilesAdapter.getItems()) {
                if (item.isSelected && item.file != null && item.file.exists()) {
                    filesToDelete.add(item.file);
                }
            }
        }
        if (filesToDelete.isEmpty()) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("删除本地文件")
                .setMessage("确定要删除选中的 " + filesToDelete.size() + " 个文件吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    int success = 0;
                    for (File f : filesToDelete) {
                        if (f.delete()) success++;
                    }
                    final int finalSuccess = success;
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "已删除 " + finalSuccess + " 个本地文件", Toast.LENGTH_SHORT).show();
                        if (isPhotoView) loadLocalPhotos();
                        else loadAllFiles();
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void syncAllToCloud() {
        if (webdavClient == null) {
            Toast.makeText(this, "未连接 WebDAV", Toast.LENGTH_SHORT).show();
            return;
        }
        String remoteDir = etRemoteDir.getText().toString().trim();
        final String finalRemoteDir = remoteDir.isEmpty() ? "" : remoteDir;

        new Thread(() -> {
            // 获取本地所有文件
            List<File> localFiles = new ArrayList<>();
            if (isPhotoView) {
                for (PhotoAdapter.PhotoItem item : photoAdapter.getItems()) {
                    if (item.file != null && item.file.exists()) {
                        localFiles.add(item.file);
                    }
                }
            } else {
                for (AllFilesAdapter.FileItem item : allFilesAdapter.getItems()) {
                    if (item.file != null && item.file.exists()) {
                        localFiles.add(item.file);
                    }
                }
            }
            if (localFiles.isEmpty()) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "没有文件可同步", Toast.LENGTH_SHORT).show());
                return;
            }

            // 获取远程文件列表
            List<String> remoteFiles = webdavClient.listDirectory(finalRemoteDir);
            Set<String> remoteNames = new HashSet<>();
            for (String f : remoteFiles) {
                if (!f.endsWith("/")) {
                    remoteNames.add(f);
                }
            }

            // 创建目录（如果不存在）
            if (!finalRemoteDir.isEmpty()) {
                boolean dirExists = false;
                for (String d : remoteFiles) {
                    if (d.equals(finalRemoteDir + "/") || d.equals(finalRemoteDir)) {
                        dirExists = true;
                        break;
                    }
                }
                if (!dirExists) {
                    webdavClient.createDirectory(finalRemoteDir);
                }
            }

            int uploaded = 0, skipped = 0, failed = 0;
            for (File f : localFiles) {
                if (remoteNames.contains(f.getName())) {
                    skipped++;
                } else {
                    boolean ok = webdavClient.uploadFile(finalRemoteDir, f);
                    if (ok) uploaded++;
                    else failed++;
                }
            }
            final int finalUploaded = uploaded;
            final int finalSkipped = skipped;
            final int finalFailed = failed;
            mainHandler.post(() -> {
                Toast.makeText(MainActivity.this, "同步完成: 上传 " + finalUploaded + ", 跳过 " + finalSkipped + ", 失败 " + finalFailed, Toast.LENGTH_LONG).show();
                loadRemoteFileList();
            });
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCurrentConnection();
        if (isPhotoView) loadLocalPhotos();
        else loadAllFiles();
    }
}

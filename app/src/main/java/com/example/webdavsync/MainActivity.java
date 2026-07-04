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

    private RecyclerView rvPhotos;
    private TextView tvConnectionStatus, tvItemCount, tvSelectedCount;
    private EditText etRemoteDir;
    private ImageView ivSettings;
    private BottomNavigationView bottomNav;
    private Button btnUpload, btnDeleteLocal, btnSyncAll;

    private PhotoAdapter photoAdapter;
    private AllFilesAdapter allFilesAdapter;
    private boolean isPhotoView = true;

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
        rvPhotos = findViewById(R.id.rv_photos);
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        tvItemCount = findViewById(R.id.tv_item_count);
        tvSelectedCount = findViewById(R.id.tv_selected_count);
        etRemoteDir = findViewById(R.id.et_remote_dir);
        ivSettings = findViewById(R.id.iv_settings);
        bottomNav = findViewById(R.id.bottom_navigation);
        btnUpload = findViewById(R.id.btn_upload);
        btnDeleteLocal = findViewById(R.id.btn_delete_local);
        btnSyncAll = findViewById(R.id.btn_sync_all);

        rvPhotos.setLayoutManager(new GridLayoutManager(this, 3));
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
        tvSelectedCount.setText("已选 " + count);
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

        btnUpload.setOnClickListener(v -> uploadSelected());
        btnDeleteLocal.setOnClickListener(v -> deleteSelectedLocal());
        btnSyncAll.setOnClickListener(v -> syncAllToCloud());
    }

    private void uploadSelected() {
        if (webdavClient == null) {
            Toast.makeText(this, "请先连接 WebDAV", Toast.LENGTH_SHORT).show();
            return;
        }
        String remoteDir = etRemoteDir.getText().toString().trim();
        final String finalRemoteDir = remoteDir.isEmpty() ? "" : remoteDir;

        List<File> selectedFiles = new ArrayList<>();
        if (isPhotoView) {
            for (PhotoAdapter.PhotoItem item : photoAdapter.getItems()) {
                if (item.isSelected && item.file != null && item.file.exists()) {
                    selectedFiles.add(item.file);
                }
            }
        } else {
            for (AllFilesAdapter.FileItem item : allFilesAdapter.getItems()) {
                if (item.isSelected && item.file != null && item.file.exists()) {
                    selectedFiles.add(item.file);
                }
            }
        }
        if (selectedFiles.isEmpty()) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "开始上传 " + selectedFiles.size() + " 个文件", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int success = 0, fail = 0;
            for (File f : selectedFiles) {
                boolean ok = webdavClient.uploadFile(finalRemoteDir, f);
                if (ok) success++; else fail++;
            }
            final int finalSuccess = success;
            final int finalFail = fail;
            mainHandler.post(() -> {
                Toast.makeText(MainActivity.this, "上传完成: 成功 " + finalSuccess + ", 失败 " + finalFail, Toast.LENGTH_LONG).show();
                loadRemoteFileList();
                if (isPhotoView) loadLocalPhotos();
                else loadAllFiles();
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
        List<File> selectedFiles = new ArrayList<>();
        if (isPhotoView) {
            for (PhotoAdapter.PhotoItem item : photoAdapter.getItems()) {
                if (item.isSelected && item.file != null && item.file.exists()) {
                    selectedFiles.add(item.file);
                }
            }
        } else {
            for (AllFilesAdapter.FileItem item : allFilesAdapter.getItems()) {
                if (item.isSelected && item.file != null && item.file.exists()) {
                    selectedFiles.add(item.file);
                }
            }
        }
        if (selectedFiles.isEmpty()) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("删除本地文件")
                .setMessage("确定要删除选中的 " + selectedFiles.size() + " 个文件吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    int success = 0;
                    for (File f : selectedFiles) {
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
            Toast.makeText(this, "请先连接 WebDAV", Toast.LENGTH_SHORT).show();
            return;
        }
        String remoteDir = etRemoteDir.getText().toString().trim();
        final String finalRemoteDir = remoteDir.isEmpty() ? "" : remoteDir;

        Toast.makeText(this, "开始同步全部文件...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<String> remoteFiles = webdavClient.listDirectory(finalRemoteDir);
            Set<String> remoteSet = new HashSet<>();
            for (String f : remoteFiles) {
                if (!f.endsWith("/")) remoteSet.add(f);
            }

            List<File> localFiles = new ArrayList<>();
            if (isPhotoView) {
                for (PhotoAdapter.PhotoItem item : photoAdapter.getItems()) {
                    if (item.file != null && item.file.exists() && !remoteSet.contains(item.name)) {
                        localFiles.add(item.file);
                    }
                }
            } else {
                for (AllFilesAdapter.FileItem item : allFilesAdapter.getItems()) {
                    if (item.file != null && item.file.exists() && !remoteSet.contains(item.name)) {
                        localFiles.add(item.file);
                    }
                }
            }
            if (localFiles.isEmpty()) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "没有新文件需要同步", Toast.LENGTH_SHORT).show());
                return;
            }

            int success = 0, fail = 0;
            for (File f : localFiles) {
                if (webdavClient.uploadFile(finalRemoteDir, f)) success++;
                else fail++;
            }
            final int finalSuccess = success;
            final int finalFail = fail;
            mainHandler.post(() -> {
                Toast.makeText(MainActivity.this, "同步完成: 成功 " + finalSuccess + ", 失败 " + finalFail, Toast.LENGTH_LONG).show();
                loadRemoteFileList();
                if (isPhotoView) loadLocalPhotos();
                else loadAllFiles();
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

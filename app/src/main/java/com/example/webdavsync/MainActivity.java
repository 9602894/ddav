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
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 100;

    private RecyclerView rvPhotos;
    private TextView tvConnectionStatus, tvPhotoCount, tvSelectedCount;
    private Button btnSync, btnCloud, btnDeleteLocal;
    private ImageView ivSettings;
    private EditText etRemoteDir;

    private PhotoAdapter adapter;
    private WebDAVClient webdavClient;
    private SharedPreferences prefs;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Set<String> remoteFileNames = new HashSet<>();

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
        loadLocalPhotos();
        setupListeners();
    }

    private void initViews() {
        rvPhotos = findViewById(R.id.rv_photos);
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        tvPhotoCount = findViewById(R.id.tv_photo_count);
        tvSelectedCount = findViewById(R.id.tv_selected_count);
        btnSync = findViewById(R.id.btn_sync);
        btnCloud = findViewById(R.id.btn_cloud);
        btnDeleteLocal = findViewById(R.id.btn_delete_local);
        ivSettings = findViewById(R.id.iv_settings);
        etRemoteDir = findViewById(R.id.et_remote_dir);
    }

    private void setupRecyclerView() {
        rvPhotos.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new PhotoAdapter(this);
        adapter.setShowCloudBadge(true);
        rvPhotos.setAdapter(adapter);

        adapter.setOnItemClickListener((item, position) -> updateSelectedCount());
        adapter.setOnItemLongClickListener((item, position) -> {
            showDeleteLocalDialog(item, position);
        });
    }

    private void loadCurrentConnection() {
        String server = prefs.getString("current_server", "");
        String username = prefs.getString("current_username", "");
        String password = prefs.getString("current_password", "");

        if (!server.isEmpty()) {
            webdavClient = new WebDAVClient(server, username, password);
            WebDAVClientHolder.setClient(webdavClient);
            tvConnectionStatus.setText("🔗 " + server);
            new Thread(() -> {
                boolean ok = webdavClient.testConnection();
                mainHandler.post(() -> {
                    if (ok) {
                        tvConnectionStatus.setText("✅ 已连接: " + server);
                        WebDAVClient.updateOkHttpClient(username, password);
                        loadRemoteFileList();
                    } else {
                        tvConnectionStatus.setText("⚠️ 连接失败: " + server);
                    }
                });
            }).start();
        } else {
            tvConnectionStatus.setText("⚪ 未配置连接");
        }
    }

    private void loadRemoteFileList() {
        if (webdavClient == null) return;
        new Thread(() -> {
            List<String> files = webdavClient.listDirectory("");
            remoteFileNames.clear();
            for (String f : files) {
                if (!f.endsWith("/")) remoteFileNames.add(f);
            }
            mainHandler.post(() -> {
                for (PhotoAdapter.PhotoItem item : adapter.getItems()) {
                    item.isOnCloud = remoteFileNames.contains(item.name);
                }
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void loadLocalPhotos() {
        tvPhotoCount.setText("加载中...");
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
                adapter.setItems(finalItems);
                tvPhotoCount.setText(finalItems.size() + " 张照片");
                updateSelectedCount();
            });
        }).start();
    }

    private void updateSelectedCount() {
        int count = 0;
        for (PhotoAdapter.PhotoItem item : adapter.getItems()) {
            if (item.isSelected) count++;
        }
        tvSelectedCount.setText("已选择 " + count + " 项");
    }

    private void setupListeners() {
        ivSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnSync.setOnClickListener(v -> syncToCloud());
        btnCloud.setOnClickListener(v -> {
            if (webdavClient == null) {
                Toast.makeText(this, "请先配置 WebDAV 连接", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, CloudActivity.class));
        });
        btnDeleteLocal.setOnClickListener(v -> deleteSelectedLocal());
    }

    private void syncToCloud() {
        List<PhotoAdapter.PhotoItem> selected = new ArrayList<>();
        for (PhotoAdapter.PhotoItem item : adapter.getItems()) {
            if (item.isSelected && item.file != null && item.file.exists()) {
                selected.add(item);
            }
        }
        if (selected.isEmpty()) {
            Toast.makeText(this, "请先选择照片", Toast.LENGTH_SHORT).show();
            return;
        }
        if (webdavClient == null) {
            Toast.makeText(this, "请先配置 WebDAV 连接", Toast.LENGTH_SHORT).show();
            return;
        }
        String remoteDir = etRemoteDir.getText().toString().trim();
        final String finalRemoteDir = remoteDir.isEmpty() ? "" : remoteDir;

        if (!finalRemoteDir.isEmpty()) {
            new Thread(() -> {
                boolean dirExists = false;
                List<String> dirs = webdavClient.listDirectory("");
                for (String d : dirs) {
                    if (d.equals(finalRemoteDir + "/") || d.equals(finalRemoteDir)) {
                        dirExists = true;
                        break;
                    }
                }
                if (!dirExists) {
                    boolean created = webdavClient.createDirectory(finalRemoteDir);
                    mainHandler.post(() -> {
                        if (created) {
                            Toast.makeText(MainActivity.this, "目录已创建: /" + finalRemoteDir, Toast.LENGTH_SHORT).show();
                            performUpload(selected, finalRemoteDir);
                        } else {
                            Toast.makeText(MainActivity.this, "创建目录失败: /" + finalRemoteDir, Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    mainHandler.post(() -> performUpload(selected, finalRemoteDir));
                }
            }).start();
        } else {
            performUpload(selected, "");
        }
    }

    private void performUpload(List<PhotoAdapter.PhotoItem> selected, final String remoteDir) {
        Toast.makeText(this, "开始同步 " + selected.size() + " 张照片到 /" + remoteDir, Toast.LENGTH_SHORT).show();
        btnSync.setEnabled(false);
        new Thread(() -> {
            int success = 0, fail = 0;
            for (PhotoAdapter.PhotoItem item : selected) {
                boolean ok = webdavClient.uploadFile(remoteDir, item.file);
                if (ok) { success++; item.isOnCloud = true; } else { fail++; }
            }
            final int finalSuccess = success;
            final int finalFail = fail;
            mainHandler.post(() -> {
                btnSync.setEnabled(true);
                Toast.makeText(MainActivity.this, "同步完成: 成功 " + finalSuccess + ", 失败 " + finalFail, Toast.LENGTH_LONG).show();
                for (PhotoAdapter.PhotoItem item : adapter.getItems()) item.isSelected = false;
                adapter.notifyDataSetChanged();
                updateSelectedCount();
                loadRemoteFileList();
            });
        }).start();
    }

    private void deleteSelectedLocal() {
        List<PhotoAdapter.PhotoItem> selected = new ArrayList<>();
        for (PhotoAdapter.PhotoItem item : adapter.getItems()) {
            if (item.isSelected && item.file != null && item.file.exists()) {
                selected.add(item);
            }
        }
        if (selected.isEmpty()) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除本地文件")
                .setMessage("确定要删除选中的 " + selected.size() + " 个文件吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    int success = 0;
                    for (PhotoAdapter.PhotoItem item : selected) {
                        if (item.file.delete()) success++;
                    }
                    final int finalSuccess = success;
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "已删除 " + finalSuccess + " 个本地文件", Toast.LENGTH_SHORT).show();
                        loadLocalPhotos();
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteLocalDialog(PhotoAdapter.PhotoItem item, int position) {
        if (item.file == null || !item.file.exists()) return;
        new AlertDialog.Builder(this)
                .setTitle("删除本地文件")
                .setMessage("确定要删除 \"" + item.name + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    if (item.file.delete()) {
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                        loadLocalPhotos();
                    } else {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadLocalPhotos();
            } else {
                Toast.makeText(this, "需要存储权限", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCurrentConnection();
        loadLocalPhotos();
    }
}

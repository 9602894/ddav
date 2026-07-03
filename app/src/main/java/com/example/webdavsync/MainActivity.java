package com.example.webdavsync;

import android.Manifest;
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
    private Button btnSync, btnCloud;
    private ImageView ivSettings;

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

        // 请求权限，如果失败则退出
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] perms = {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, perms, REQUEST_PERMISSIONS);
                return; // 等待回调
            }
        }

        // 权限已授予，正常初始化
        initAfterPermissions();
    }

    private void initAfterPermissions() {
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
        ivSettings = findViewById(R.id.iv_settings);
    }

    private void setupRecyclerView() {
        rvPhotos.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new PhotoAdapter(this);
        adapter.setShowCloudBadge(true);
        rvPhotos.setAdapter(adapter);

        adapter.setOnItemClickListener((item, position) -> updateSelectedCount());
    }

    private void loadCurrentConnection() {
        // 从设置中获取当前连接的配置名称
        String currentConfig = prefs.getString("current_config", "");
        if (!currentConfig.isEmpty()) {
            // 从配置存储中加载详细信息
            SharedPreferences configPrefs = getSharedPreferences("webdav_configs", MODE_PRIVATE);
            String server = configPrefs.getString("config_" + currentConfig + "_server", "");
            String user = configPrefs.getString("config_" + currentConfig + "_username", "");
            String pass = configPrefs.getString("config_" + currentConfig + "_password", "");
            if (!server.isEmpty()) {
                webdavClient = new WebDAVClient(server, user, pass);
                tvConnectionStatus.setText("🔗 " + server);
                new Thread(() -> {
                    boolean ok = webdavClient.testConnection();
                    mainHandler.post(() -> {
                        if (ok) {
                            tvConnectionStatus.setText("✅ 已连接: " + server);
                            loadRemoteFileList();
                        } else {
                            tvConnectionStatus.setText("⚠️ 连接失败: " + server);
                            webdavClient = null;
                        }
                    });
                }).start();
                return;
            }
        }
        // 没有有效配置
        tvConnectionStatus.setText("⚪ 未配置连接");
        webdavClient = null;
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

            String[] projection = {
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_MODIFIED
            };
            Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            Cursor cursor = getContentResolver().query(uri, projection, null, null,
                    MediaStore.Images.Media.DATE_MODIFIED + " DESC");

            if (cursor != null) {
                int dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                int nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                int dateIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED);

                while (cursor.moveToNext()) {
                    String path = cursor.getString(dataIndex);
                    String name = cursor.getString(nameIndex);
                    long date = cursor.getLong(dateIndex);

                    if (path != null) {
                        File file = new File(path);
                        if (file.exists()) {
                            PhotoAdapter.PhotoItem item = new PhotoAdapter.PhotoItem(name);
                            item.file = file;
                            item.dateModified = date;
                            item.isOnCloud = remoteFileNames.contains(name);
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
        ivSettings.setOnClickListener(v -> {
            startActivityForResult(new Intent(this, SettingsActivity.class), 1);
        });

        btnSync.setOnClickListener(v -> syncToCloud());

        btnCloud.setOnClickListener(v -> {
            if (webdavClient == null) {
                Toast.makeText(this, "请先配置并切换 WebDAV 连接", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, CloudActivity.class));
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            // 设置返回，重新加载连接
            loadCurrentConnection();
        }
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

        Toast.makeText(this, "开始同步 " + selected.size() + " 张照片", Toast.LENGTH_SHORT).show();
        btnSync.setEnabled(false);

        new Thread(() -> {
            int success = 0, fail = 0;
            for (PhotoAdapter.PhotoItem item : selected) {
                boolean ok = webdavClient.uploadFile("", item.file);
                if (ok) {
                    success++;
                    item.isOnCloud = true;
                } else {
                    fail++;
                }
            }

            final int finalSuccess = success;
            final int finalFail = fail;
            mainHandler.post(() -> {
                btnSync.setEnabled(true);
                Toast.makeText(MainActivity.this,
                        "同步完成: 成功 " + finalSuccess + ", 失败 " + finalFail,
                        Toast.LENGTH_LONG).show();
                for (PhotoAdapter.PhotoItem item : adapter.getItems()) {
                    item.isSelected = false;
                }
                adapter.notifyDataSetChanged();
                updateSelectedCount();
                loadRemoteFileList();
            });
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限授予，继续初始化
                initAfterPermissions();
            } else {
                // 权限被拒绝，给出提示并退出
                Toast.makeText(this, "需要存储权限才能访问相册", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 刷新连接状态
        if (webdavClient == null) {
            loadCurrentConnection();
        }
    }
}

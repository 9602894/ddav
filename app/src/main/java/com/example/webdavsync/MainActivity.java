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
    private TextView tvConnectionStatus, tvItemCount;
    private EditText etRemoteDir;
    private ImageView ivSettings;
    private BottomNavigationView bottomNav;

    private PhotoAdapter photoAdapter;
    private AllFilesAdapter allFilesAdapter;
    private boolean isPhotoView = true;

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
        etRemoteDir = findViewById(R.id.et_remote_dir);
        ivSettings = findViewById(R.id.iv_settings);
        bottomNav = findViewById(R.id.bottom_navigation);
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
    }

    private void showLocalAll() {
        rvPhotos.setAdapter(allFilesAdapter);
        isPhotoView = false;
        loadAllFiles();
    }

    private void loadCurrentConnection() {
        // 从 SharedPreferences 读取当前连接的配置名
        String configName = prefs.getString("current_config", "");
        if (!configName.isEmpty()) {
            String server = prefs.getString("config_" + configName + "_server", "");
            String username = prefs.getString("config_" + configName + "_username", "");
            String password = prefs.getString("config_" + configName + "_password", "");
            if (!server.isEmpty()) {
                webdavClient = new WebDAVClient(server, username, password);
                WebDAVClientHolder.setClient(webdavClient);
                tvConnectionStatus.setText("✅ 已连接: " + configName);
                // 后台测试连接
                new Thread(() -> {
                    boolean ok = webdavClient.testConnection();
                    mainHandler.post(() -> {
                        if (ok) {
                            WebDAVClient.updateOkHttpClient(username, password);
                            loadRemoteFileList();
                        } else {
                            tvConnectionStatus.setText("⚠️ 连接失效: " + configName);
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
        // ... 与之前相同，略 ...
    }

    private void loadAllFiles() {
        // ... 与之前相同，略 ...
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
                    Toast.makeText(this, "请先在设置中连接", Toast.LENGTH_SHORT).show();
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
        });

        allFilesAdapter.setOnItemClickListener((item, position) -> {
            item.isSelected = !item.isSelected;
            allFilesAdapter.notifyItemChanged(position);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从设置返回后重新加载连接
        loadCurrentConnection();
        if (isPhotoView) loadLocalPhotos();
        else loadAllFiles();
    }
}

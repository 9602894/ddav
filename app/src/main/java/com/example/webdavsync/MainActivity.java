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
    private TextView tvConnectionStatus, tvPhotoCount;
    private EditText etRemoteDir;
    private ImageView ivSettings;
    private BottomNavigationView bottomNav;

    private PhotoAdapter photoAdapter;          // 本地相册
    private AllFilesAdapter allFilesAdapter;    // 本地全部
    private int currentView = 0; // 0=相册, 1=全部

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
        showPhotoView(); // 默认显示相册
        setupListeners();
    }

    private void initViews() {
        contentFrame = findViewById(R.id.content_frame);
        rvPhotos = new RecyclerView(this);
        rvPhotos.setLayoutManager(new GridLayoutManager(this, 3));
        contentFrame.addView(rvPhotos);

        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        tvPhotoCount = findViewById(R.id.tv_photo_count);
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

    private void showPhotoView() {
        rvPhotos.setAdapter(photoAdapter);
        currentView = 0;
        loadLocalPhotos();
    }

    private void showAllFilesView() {
        rvPhotos.setAdapter(allFilesAdapter);
        currentView = 1;
        loadAllFiles();
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
                if (!f.endsWith("/")) {
                    remoteFileNames.add(f);
                }
            }
            mainHandler.post(() -> {
                // 更新两个适配器的云端标记
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
                photoAdapter.setItems(finalItems);
                tvPhotoCount.setText(finalItems.size() + " 张");
            });
        }).start();
    }

    private void loadAllFiles() {
        tvPhotoCount.setText("加载中...");
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
                tvPhotoCount.setText(finalItems.size() + " 个");
            });
        }).start();
    }

    private void setupListeners() {
        ivSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_local_photos) {
                showPhotoView();
                return true;
            } else if (id == R.id.nav_local_all) {
                showAllFilesView();
                return true;
            } else if (id == R.id.nav_cloud_photos || id == R.id.nav_cloud_all) {
                if (webdavClient == null) {
                    Toast.makeText(this, "请先配置 WebDAV 连接", Toast.LENGTH_SHORT).show();
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
        });

        allFilesAdapter.setOnItemClickListener((item, position) -> {
            item.isSelected = !item.isSelected;
            allFilesAdapter.notifyItemChanged(position);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCurrentConnection();
        if (currentView == 0) loadLocalPhotos();
        else loadAllFiles();
    }
}

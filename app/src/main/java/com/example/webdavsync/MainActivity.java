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
    private TextView tvConnectionStatus, tvFileCount, tvSelectedCount;
    private Button btnSync, btnCloud, btnDeleteLocal;
    private ImageView ivSettings;
    private EditText etRemoteDir;
    private BottomNavigationView bottomNav;

    private PhotoAdapter photoAdapter;
    private AllFilesAdapter allFilesAdapter;
    private WebDAVClient webdavClient;
    private SharedPreferences prefs;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Set<String> remoteFileNames = new HashSet<>();

    private int currentView = 0; // 0=相册, 1=云端, 2=全部文件

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
        showView(0); // 默认显示相册
        setupListeners();
    }

    private void initViews() {
        contentFrame = findViewById(R.id.content_frame);
        rvPhotos = new RecyclerView(this);
        rvPhotos.setLayoutManager(new GridLayoutManager(this, 3));
        contentFrame.addView(rvPhotos);

        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        tvFileCount = findViewById(R.id.tv_file_count);
        tvSelectedCount = findViewById(R.id.tv_selected_count);
        btnSync = findViewById(R.id.btn_sync);
        btnCloud = findViewById(R.id.btn_cloud);
        btnDeleteLocal = findViewById(R.id.btn_delete_local);
        ivSettings = findViewById(R.id.iv_settings);
        etRemoteDir = findViewById(R.id.et_remote_dir);
        bottomNav = findViewById(R.id.bottom_navigation);
    }

    private void setupRecyclerView() {
        photoAdapter = new PhotoAdapter(this);
        photoAdapter.setShowCloudBadge(true);
        allFilesAdapter = new AllFilesAdapter(this);
        allFilesAdapter.setShowCloudBadge(true);
    }

    private void showView(int view) {
        currentView = view;
        if (view == 0) {
            rvPhotos.setAdapter(photoAdapter);
            tvFileCount.setText(photoAdapter.getItemCount() + " 张照片");
            loadLocalPhotos();
        } else if (view == 1) {
            // 云端视图直接跳转 CloudActivity
            startActivity(new Intent(this, CloudActivity.class));
            bottomNav.setSelectedItemId(R.id.nav_photos); // 避免陷入循环
            return;
        } else if (view == 2) {
            rvPhotos.setAdapter(allFilesAdapter);
            tvFileCount.setText(allFilesAdapter.getItemCount() + " 个文件");
            loadAllFiles();
        }
        updateSelectedCount();
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
                // 更新两个适配器
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
        tvFileCount.setText("加载中...");
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
                tvFileCount.setText(finalItems.size() + " 张照片");
                updateSelectedCount();
            });
        }).start();
    }

    private void loadAllFiles() {
        tvFileCount.setText("加载中...");
        new Thread(() -> {
            List<AllFilesAdapter.FileItem> items = new ArrayList<>();
            // 查询所有文件（不限制mimeType）
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
                tvFileCount.setText(finalItems.size() + " 个文件");
                updateSelectedCount();
            });
        }).start();
    }

    private void updateSelectedCount() {
        int count = 0;
        if (currentView == 0) {
            for (PhotoAdapter.PhotoItem item : photoAdapter.getItems()) {
                if (item.isSelected) count++;
            }
        } else if (currentView == 2) {
            for (AllFilesAdapter.FileItem item : allFilesAdapter.getItems()) {
                if (item.isSelected) count++;
            }
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

        // 底部导航切换
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_photos) {
                showView(0);
                return true;
            } else if (id == R.id.nav_cloud) {
                if (webdavClient == null) {
                    Toast.makeText(this, "请先配置 WebDAV 连接", Toast.LENGTH_SHORT).show();
                    return false;
                }
                startActivity(new Intent(this, CloudActivity.class));
                return true;
            } else if (id == R.id.nav_all_files) {
                showView(2);
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
    }

    private void syncToCloud() {
        // 收集选中的文件
        List<Object> selected = new ArrayList<>();
        if (currentView == 0) {
            for (PhotoAdapter.PhotoItem item : photoAdapter.getItems()) {
                if (item.isSelected && item.file != null && item.file.exists()) {
                    selected.add(item);
                }
            }
        } else {
            for (AllFilesAdapter.FileItem item : allFilesAdapter.getItems()) {
                if (item.isSelected && item.file != null && item.file.exists()) {
                    selected.add(item);
                }
            }
        }
        if (selected.isEmpty()) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }
        if (webdavClient == null) {
            Toast.makeText(this, "请先配置 WebDAV 连接", Toast.LENGTH_SHORT).show();
            return;
        }

        String remoteDir = etRemoteDir.getText().toString().trim();
        final String finalRemoteDir = remoteDir.isEmpty() ? "" : remoteDir;

        // 创建目录
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

    private void performUpload(List<Object> selected, final String remoteDir) {
        Toast.makeText(this, "开始同步 " + selected.size() + " 个文件到 /" + remoteDir, Toast.LENGTH_SHORT).show();
        btnSync.setEnabled(false);

        new Thread(() -> {
            int success = 0, fail = 0;
            for (Object obj : selected) {
                File file = null;
                if (obj instanceof PhotoAdapter.PhotoItem) {
                    file = ((PhotoAdapter.PhotoItem) obj).file;
                } else if (obj instanceof AllFilesAdapter.FileItem) {
                    file = ((AllFilesAdapter.FileItem) obj).file;
                }
                if (file != null) {
                    boolean ok = webdavClient.uploadFile(remoteDir, file);
                    if (ok) success++;
                    else fail++;
                }
            }
            final int finalSuccess = success;
            final int finalFail = fail;
            mainHandler.post(() -> {
                btnSync.setEnabled(true);
                Toast.makeText(MainActivity.this, "同步完成: 成功 " + finalSuccess + ", 失败 " + finalFail, Toast.LENGTH_LONG).show();
                // 清除选中
                if (currentView == 0) {
                    for (PhotoAdapter.PhotoItem item : photoAdapter.getItems()) item.isSelected = false;
                    photoAdapter.notifyDataSetChanged();
                } else {
                    for (AllFilesAdapter.FileItem item : allFilesAdapter.getItems()) item.isSelected = false;
                    allFilesAdapter.notifyDataSetChanged();
                }
                updateSelectedCount();
                loadRemoteFileList();
            });
        }).start();
    }

    private void deleteSelectedLocal() {
        List<Object> selected = new ArrayList<>();
        if (currentView == 0) {
            for (PhotoAdapter.PhotoItem item : photoAdapter.getItems()) {
                if (item.isSelected && item.file != null && item.file.exists()) {
                    selected.add(item);
                }
            }
        } else {
            for (AllFilesAdapter.FileItem item : allFilesAdapter.getItems()) {
                if (item.isSelected && item.file != null && item.file.exists()) {
                    selected.add(item);
                }
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
                    for (Object obj : selected) {
                        File file = null;
                        if (obj instanceof PhotoAdapter.PhotoItem) {
                            file = ((PhotoAdapter.PhotoItem) obj).file;
                        } else if (obj instanceof AllFilesAdapter.FileItem) {
                            file = ((AllFilesAdapter.FileItem) obj).file;
                        }
                        if (file != null && file.delete()) success++;
                    }
                    final int finalSuccess = success;
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "已删除 " + finalSuccess + " 个本地文件", Toast.LENGTH_SHORT).show();
                        if (currentView == 0) loadLocalPhotos();
                        else loadAllFiles();
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCurrentConnection();
        if (currentView == 0) loadLocalPhotos();
        else if (currentView == 2) loadAllFiles();
    }
}

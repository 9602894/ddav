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
import android.text.InputType;
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
    private Button btnUpload, btnDeleteLocal, btnSyncAll, btnBrowseRemote;

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
        btnBrowseRemote = findViewById(R.id.btn_browse_remote);

        rvPhotos.setLayoutManager(new GridLayoutManager(this, 3));
    }

    private void setupRecyclerView() {
        photoAdapter = new PhotoAdapter(this);
        photoAdapter.setShowCloudBadge(true);
        allFilesAdapter = new AllFilesAdapter(this);
        allFilesAdapter.setShowCloudBadge(true);

        // ★ 本地文件长按菜单（重命名、删除）
        photoAdapter.setOnItemLongClickListener((item, position) -> {
            showLocalFileMenu(item, position);
        });
        allFilesAdapter.setOnItemLongClickListener((item, position) -> {
            showLocalFileMenu(item, position);
        });
    }

    private void showLocalFileMenu(Object item, int position) {
        String name;
        File file;
        if (isPhotoView) {
            PhotoAdapter.PhotoItem pi = (PhotoAdapter.PhotoItem) item;
            name = pi.name;
            file = pi.file;
        } else {
            AllFilesAdapter.FileItem fi = (AllFilesAdapter.FileItem) item;
            name = fi.name;
            file = fi.file;
        }
        if (file == null || !file.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] options = {"重命名", "删除"};
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showRenameDialog(file, position);
                    } else if (which == 1) {
                        deleteFile(file, position);
                    }
                })
                .show();
    }

    private void showRenameDialog(File file, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("重命名文件");
        final EditText input = new EditText(this);
        input.setText(file.getName());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton("确定", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            File newFile = new File(file.getParent(), newName);
            if (newFile.exists()) {
                Toast.makeText(this, "文件已存在", Toast.LENGTH_SHORT).show();
                return;
            }
            if (file.renameTo(newFile)) {
                Toast.makeText(this, "重命名成功", Toast.LENGTH_SHORT).show();
                // 刷新列表
                if (isPhotoView) loadLocalPhotos();
                else loadAllFiles();
            } else {
                Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void deleteFile(File file, int position) {
        new AlertDialog.Builder(this)
                .setTitle("删除文件")
                .setMessage("确定要删除 \"" + file.getName() + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    if (file.delete()) {
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                        if (isPhotoView) loadLocalPhotos();
                        else loadAllFiles();
                    } else {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ★ 远程目录选择（点击浏览按钮）
    private void showRemoteDirectoryPicker() {
        if (webdavClient == null) {
            Toast.makeText(this, "请先连接 WebDAV", Toast.LENGTH_SHORT).show();
            return;
        }
        // 获取当前输入框中的路径作为起始路径
        String currentPath = etRemoteDir.getText().toString().trim();
        if (currentPath.isEmpty()) currentPath = "/";
        showRemoteDirectoryDialog(currentPath);
    }

    private void showRemoteDirectoryDialog(final String path) {
        new Thread(() -> {
            List<String> items = webdavClient.listDirectory(path);
            mainHandler.post(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("选择目录: " + path);

                // 构建列表：目录（以 / 结尾）和文件（点击可选择当前路径）
                List<String> displayItems = new ArrayList<>();
                List<String> itemPaths = new ArrayList<>();

                // 添加当前路径选择项
                displayItems.add("📁 选择此目录");
                itemPaths.add(path);

                // 添加父目录（如果不是根目录）
                if (!path.equals("/")) {
                    String parent = path.substring(0, path.lastIndexOf('/'));
                    if (parent.isEmpty()) parent = "/";
                    displayItems.add("⬆ 返回上级");
                    itemPaths.add(parent);
                }

                if (items != null && !items.isEmpty()) {
                    for (String item : items) {
                        if (item.endsWith("/")) {
                            displayItems.add("📁 " + item);
                        } else {
                            displayItems.add("📄 " + item);
                        }
                        itemPaths.add(item);
                    }
                } else {
                    displayItems.add("(空目录)");
                    itemPaths.add("");
                }

                builder.setItems(displayItems.toArray(new String[0]), (dialog, which) -> {
                    String selected = itemPaths.get(which);
                    if (selected.equals(path) || selected.isEmpty()) {
                        // 选择当前目录
                        etRemoteDir.setText(path);
                        Toast.makeText(MainActivity.this, "已选择: " + path, Toast.LENGTH_SHORT).show();
                    } else if (selected.equals("parent")) {
                        // 返回上级
                        showRemoteDirectoryDialog(path.substring(0, path.lastIndexOf('/')));
                    } else if (selected.endsWith("/")) {
                        // 进入子目录
                        String newPath = path.endsWith("/") ? path + selected : path + "/" + selected;
                        newPath = newPath.replace("//", "/");
                        showRemoteDirectoryDialog(newPath);
                    } else {
                        // 点击文件：选择当前路径（即文件所在目录）
                        String dirPath = path.endsWith("/") ? path : path.substring(0, path.lastIndexOf('/') + 1);
                        etRemoteDir.setText(dirPath);
                        Toast.makeText(MainActivity.this, "已选择: " + dirPath, Toast.LENGTH_SHORT).show();
                    }
                });

                builder.setNegativeButton("取消", null);
                builder.show();
            });
        }).start();
    }

    // ... 其余已有方法（loadCurrentConnection, loadRemoteFileList, loadLocalPhotos, loadAllFiles, uploadSelected, deleteSelectedLocal, syncAllToCloud 等）保持不变

    // 为节省篇幅，省略已存在的相同方法，实际使用时需保留完整代码。
    // 注意：务必保留以下方法：
    // - loadCurrentConnection()
    // - loadRemoteFileList()
    // - loadLocalPhotos()
    // - loadAllFiles()
    // - updateSelectedCount()
    // - setupListeners()
    // - uploadSelected()
    // - deleteSelectedLocal()
    // - syncAllToCloud()
    // - onResume()
    // - onRequestPermissionsResult()
}

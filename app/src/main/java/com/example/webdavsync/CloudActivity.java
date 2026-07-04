package com.example.webdavsync;

import android.app.AlertDialog;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CloudActivity extends AppCompatActivity {

    private RecyclerView rvCloud;
    private TextView tvCloudPath, tvCloudCount, tvCloudSelected;
    private Button btnDownload, btnUp, btnDeleteCloud, btnNewFolder;
    private ImageView ivBack;

    private PhotoAdapter photoAdapter;
    private AllFilesAdapter allFilesAdapter;
    private String currentPath = "";
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Set<String> localFileNames = new HashSet<>();
    private WebDAVClient client;
    private String type;
    private boolean isPhotoView = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud);

        rvCloud = findViewById(R.id.rv_cloud_files);
        tvCloudPath = findViewById(R.id.tv_cloud_path);
        tvCloudCount = findViewById(R.id.tv_cloud_count);
        tvCloudSelected = findViewById(R.id.tv_cloud_selected);
        btnDownload = findViewById(R.id.btn_download);
        btnUp = findViewById(R.id.btn_cloud_up);
        btnDeleteCloud = findViewById(R.id.btn_delete_cloud);
        btnNewFolder = findViewById(R.id.btn_new_folder);
        ivBack = findViewById(R.id.iv_back);

        type = getIntent().getStringExtra("type");
        if (type == null) type = "photo";
        isPhotoView = type.equals("photo");

        client = WebDAVClientHolder.getClient();
        if (client == null) {
            Toast.makeText(this, "请先在主界面连接服务器", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        WebDAVClient.updateOkHttpClient(
                client.getUsername() != null ? client.getUsername() : "",
                client.getPassword() != null ? client.getPassword() : ""
        );

        collectLocalFiles();

        rvCloud.setLayoutManager(new GridLayoutManager(this, 3));

        if (isPhotoView) {
            photoAdapter = new PhotoAdapter(this);
            photoAdapter.setCloudView(true);
            photoAdapter.setShowLocalBadge(true);
            rvCloud.setAdapter(photoAdapter);
            photoAdapter.setOnItemClickListener((item, position) -> {
                if (item.name.endsWith("/")) {
                    String subPath = item.name.substring(0, item.name.length() - 1);
                    String newPath = currentPath.isEmpty() ? subPath : currentPath + "/" + subPath;
                    loadDirectory(newPath);
                } else {
                    item.isSelected = !item.isSelected;
                    photoAdapter.notifyItemChanged(position);
                    updateSelectedCount();
                }
            });
            // 长按弹出操作菜单（新建文件夹、重命名、删除）
            photoAdapter.setOnItemLongClickListener((item, position) -> {
                showFileOperationDialog(item, position);
                return true;
            });
        } else {
            allFilesAdapter = new AllFilesAdapter(this);
            allFilesAdapter.setCloudView(true);
            allFilesAdapter.setShowLocalBadge(true);
            rvCloud.setAdapter(allFilesAdapter);
            allFilesAdapter.setOnItemClickListener((item, position) -> {
                if (item.name.endsWith("/")) {
                    String subPath = item.name.substring(0, item.name.length() - 1);
                    String newPath = currentPath.isEmpty() ? subPath : currentPath + "/" + subPath;
                    loadDirectory(newPath);
                } else {
                    item.isSelected = !item.isSelected;
                    allFilesAdapter.notifyItemChanged(position);
                    updateSelectedCount();
                }
            });
            allFilesAdapter.setOnItemLongClickListener((item, position) -> {
                showFileOperationDialog(item, position);
                return true;
            });
        }

        loadDirectory("");

        tvCloudPath.setOnClickListener(v -> {
            if (!currentPath.isEmpty()) {
                loadDirectory("");
            } else {
                Toast.makeText(this, "已在根目录", Toast.LENGTH_SHORT).show();
            }
        });

        ivBack.setOnClickListener(v -> finish());

        btnUp.setOnClickListener(v -> {
            if (currentPath.isEmpty()) {
                Toast.makeText(this, "已在根目录", Toast.LENGTH_SHORT).show();
                return;
            }
            int lastSlash = currentPath.lastIndexOf('/');
            String parent = lastSlash > 0 ? currentPath.substring(0, lastSlash) : "";
            loadDirectory(parent);
        });

        btnDownload.setOnClickListener(v -> downloadSelected());
        btnDeleteCloud.setOnClickListener(v -> deleteSelected());
        btnNewFolder.setOnClickListener(v -> showNewFolderDialog());
    }

    private void collectLocalFiles() {
        localFileNames.clear();
        new Thread(() -> {
            if (isPhotoView) {
                String[] projection = {MediaStore.Files.FileColumns.DISPLAY_NAME};
                String selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                        + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                        + " OR " + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                        + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
                android.database.Cursor cursor = getContentResolver().query(
                        MediaStore.Files.getContentUri("external"),
                        projection,
                        selection,
                        null,
                        null
                );
                if (cursor != null) {
                    int nameIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME);
                    while (cursor.moveToNext()) {
                        String name = cursor.getString(nameIdx);
                        if (name != null) localFileNames.add(name);
                    }
                    cursor.close();
                }
            } else {
                String[] projection = {MediaStore.Files.FileColumns.DISPLAY_NAME};
                android.database.Cursor cursor = getContentResolver().query(
                        MediaStore.Files.getContentUri("external"),
                        projection,
                        null,
                        null,
                        null
                );
                if (cursor != null) {
                    int nameIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME);
                    while (cursor.moveToNext()) {
                        String name = cursor.getString(nameIdx);
                        if (name != null) localFileNames.add(name);
                    }
                    cursor.close();
                }
            }
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadDir != null && downloadDir.exists()) {
                for (File f : downloadDir.listFiles()) {
                    if (f.isFile()) localFileNames.add(f.getName());
                }
            }
            mainHandler.post(() -> {
                if (isPhotoView) {
                    for (PhotoAdapter.PhotoItem item : photoAdapter.getItems()) {
                        item.isOnLocal = localFileNames.contains(item.name);
                    }
                    photoAdapter.notifyDataSetChanged();
                } else {
                    for (AllFilesAdapter.FileItem item : allFilesAdapter.getItems()) {
                        item.isOnLocal = localFileNames.contains(item.name);
                    }
                    allFilesAdapter.notifyDataSetChanged();
                }
            });
        }).start();
    }

    private void loadDirectory(String path) {
        currentPath = path == null ? "" : path;
        tvCloudPath.setText("/" + currentPath);
        tvCloudCount.setText("加载中...");

        new Thread(() -> {
            List<String> items = client.listDirectory(currentPath);
            mainHandler.post(() -> {
                if (isPhotoView) {
                    List<PhotoAdapter.PhotoItem> list = new ArrayList<>();
                    if (items != null && !items.isEmpty()) {
                        boolean hasError = false;
                        for (String item : items) {
                            if (item.startsWith("错误") || item.startsWith("网络错误")) {
                                tvCloudCount.setText(item);
                                hasError = true;
                                break;
                            }
                        }
                        if (!hasError) {
                            for (String item : items) {
                                if (item.endsWith("/")) {
                                    PhotoAdapter.PhotoItem fi = new PhotoAdapter.PhotoItem(item);
                                    fi.name = item;
                                    fi.displayName = item;
                                    fi.isOnCloud = true;
                                    fi.isOnLocal = false;
                                    fi.remoteUrl = null;
                                    fi.isVideo = false;
                                    list.add(fi);
                                } else {
                                    String ext = item.substring(item.lastIndexOf('.') + 1).toLowerCase();
                                    if (ext.matches("jpg|jpeg|png|gif|bmp|webp|mp4|3gp|avi|mkv|mov|webm")) {
                                        PhotoAdapter.PhotoItem fi = new PhotoAdapter.PhotoItem(item);
                                        fi.name = item;
                                        fi.displayName = item;
                                        fi.isOnCloud = true;
                                        fi.isOnLocal = localFileNames.contains(item);
                                        String remotePath = currentPath.isEmpty() ? item : currentPath + "/" + item;
                                        fi.remoteUrl = client.getServerUrl() + "/" + remotePath;
                                        fi.isVideo = ext.matches("mp4|3gp|avi|mkv|mov|webm");
                                        fi.dateModified = 0;
                                        list.add(fi);
                                    }
                                }
                            }
                            Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name));
                            tvCloudCount.setText(list.size() + " 个项目");
                        }
                    } else {
                        tvCloudCount.setText("空目录");
                    }
                    photoAdapter.setItems(list);
                    updateSelectedCount();
                } else {
                    List<AllFilesAdapter.FileItem> list = new ArrayList<>();
                    if (items != null && !items.isEmpty()) {
                        boolean hasError = false;
                        for (String item : items) {
                            if (item.startsWith("错误") || item.startsWith("网络错误")) {
                                tvCloudCount.setText(item);
                                hasError = true;
                                break;
                            }
                        }
                        if (!hasError) {
                            for (String item : items) {
                                if (item.endsWith("/")) {
                                    AllFilesAdapter.FileItem fi = new AllFilesAdapter.FileItem(item);
                                    fi.name = item;
                                    fi.displayName = item;
                                    fi.isOnCloud = true;
                                    fi.isOnLocal = false;
                                    list.add(fi);
                                } else {
                                    AllFilesAdapter.FileItem fi = new AllFilesAdapter.FileItem(item);
                                    fi.name = item;
                                    fi.displayName = item;
                                    fi.isOnCloud = true;
                                    fi.isOnLocal = localFileNames.contains(item);
                                    fi.file = null;
                                    fi.dateModified = 0;
                                    list.add(fi);
                                }
                            }
                            Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name));
                            tvCloudCount.setText(list.size() + " 个项目");
                        }
                    } else {
                        tvCloudCount.setText("空目录");
                    }
                    allFilesAdapter.setItems(list);
                    updateSelectedCount();
                }
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
        tvCloudSelected.setText("已选择 " + count + " 项");
    }

    // 文件操作菜单（新建文件夹、重命名、删除）
    private void showFileOperationDialog(Object item, int position) {
        final String name;
        final boolean isDir;
        if (item instanceof PhotoAdapter.PhotoItem) {
            PhotoAdapter.PhotoItem pi = (PhotoAdapter.PhotoItem) item;
            name = pi.name;
            isDir = name.endsWith("/");
        } else {
            AllFilesAdapter.FileItem fi = (AllFilesAdapter.FileItem) item;
            name = fi.name;
            isDir = name.endsWith("/");
        }
        String[] options;
        if (isDir) {
            options = new String[]{"重命名", "删除"};
        } else {
            options = new String[]{"重命名", "删除"};
        }
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showRenameDialog(name, position);
                    } else if (which == 1) {
                        showDeleteConfirm(name, position);
                    }
                })
                .show();
    }

    private void showNewFolderDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("新建文件夹");
        final EditText input = new EditText(this);
        builder.setView(input);
        builder.setPositiveButton("确定", (dialog, which) -> {
            String folderName = input.getText().toString().trim();
            if (folderName.isEmpty()) {
                Toast.makeText(this, "请输入名称", Toast.LENGTH_SHORT).show();
                return;
            }
            String newPath = currentPath.isEmpty() ? folderName : currentPath + "/" + folderName;
            new Thread(() -> {
                boolean ok = client.createDirectory(newPath);
                mainHandler.post(() -> {
                    if (ok) {
                        Toast.makeText(CloudActivity.this, "文件夹已创建", Toast.LENGTH_SHORT).show();
                        loadDirectory(currentPath);
                    } else {
                        Toast.makeText(CloudActivity.this, "创建失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showRenameDialog(String oldName, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("重命名");
        final EditText input = new EditText(this);
        input.setText(oldName);
        builder.setView(input);
        builder.setPositiveButton("确定", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty() || newName.equals(oldName)) {
                Toast.makeText(this, "名称无效", Toast.LENGTH_SHORT).show();
                return;
            }
            // WebDAV 重命名 = 复制 + 删除（或 MOVE，但需要实现）
            // 为简化，提示功能未实现
            Toast.makeText(this, "重命名功能暂未实现（需 WebDAV MOVE）", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showDeleteConfirm(String name, int position) {
        new AlertDialog.Builder(this)
                .setTitle("删除")
                .setMessage("确定要删除 \"" + name + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    String remotePath = currentPath.isEmpty() ? name : currentPath + "/" + name;
                    new Thread(() -> {
                        boolean ok = client.deleteFile(remotePath);
                        mainHandler.post(() -> {
                            if (ok) {
                                Toast.makeText(CloudActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                                loadDirectory(currentPath);
                            } else {
                                Toast.makeText(CloudActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void downloadSelected() {
        // 同之前
    }

    private void deleteSelected() {
        // 同之前
    }
}

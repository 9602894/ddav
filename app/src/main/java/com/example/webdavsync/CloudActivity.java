package com.example.webdavsync;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CloudActivity extends AppCompatActivity {

    private RecyclerView rvCloud;
    private TextView tvCloudPath, tvCloudCount, tvCloudSelected;
    private Button btnDownload, btnUp, btnDeleteCloud;
    private ImageView ivBack;

    private PhotoAdapter photoAdapter;
    private AllFilesAdapter allFilesAdapter;
    private String currentPath = "";
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<String> localFileNames = new ArrayList<>();
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
                    // ★ 进入子目录
                    String subPath = item.name.substring(0, item.name.length() - 1);
                    String newPath = currentPath.isEmpty() ? subPath : currentPath + "/" + subPath;
                    loadDirectory(newPath);
                } else {
                    item.isSelected = !item.isSelected;
                    photoAdapter.notifyItemChanged(position);
                    updateSelectedCount();
                }
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
        }

        loadDirectory("");

        // ★ 点击路径返回根目录
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
    }

    private void collectLocalFiles() {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir != null && downloadDir.exists()) {
            for (File f : downloadDir.listFiles()) {
                if (f.isFile()) localFileNames.add(f.getName());
            }
        }
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

    private void downloadSelected() {
        List<String> selectedNames = new ArrayList<>();
        if (isPhotoView) {
            for (PhotoAdapter.PhotoItem item : photoAdapter.getItems()) {
                if (item.isSelected && !item.name.endsWith("/")) {
                    selectedNames.add(item.name);
                }
            }
        } else {
            for (AllFilesAdapter.FileItem item : allFilesAdapter.getItems()) {
                if (item.isSelected && !item.name.endsWith("/")) {
                    selectedNames.add(item.name);
                }
            }
        }
        if (selectedNames.isEmpty()) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "开始下载 " + selectedNames.size() + " 个文件", Toast.LENGTH_SHORT).show();
        btnDownload.setEnabled(false);

        new Thread(() -> {
            int success = 0, fail = 0;
            for (String name : selectedNames) {
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadDir.exists()) downloadDir.mkdirs();
                File destFile = new File(downloadDir, name);
                int count = 1;
                String base = name;
                String ext = "";
                int dot = name.lastIndexOf('.');
                if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
                while (destFile.exists()) {
                    destFile = new File(downloadDir, base + "_" + count + ext);
                    count++;
                }
                String remotePath = currentPath.isEmpty() ? name : currentPath + "/" + name;
                boolean ok = client.downloadFile(remotePath, destFile);
                if (ok) success++; else fail++;
            }
            final int finalSuccess = success;
            final int finalFail = fail;
            mainHandler.post(() -> {
                btnDownload.setEnabled(true);
                tvCloudCount.setText("下载完成: 成功 " + finalSuccess + ", 失败 " + finalFail);
                Toast.makeText(CloudActivity.this, "下载完成", Toast.LENGTH_LONG).show();
                if (isPhotoView) {
                    for (PhotoAdapter.PhotoItem item : photoAdapter.getItems()) item.isSelected = false;
                    photoAdapter.notifyDataSetChanged();
                } else {
                    for (AllFilesAdapter.FileItem item : allFilesAdapter.getItems()) item.isSelected = false;
                    allFilesAdapter.notifyDataSetChanged();
                }
                updateSelectedCount();
                collectLocalFiles();
                loadDirectory(currentPath);
            });
        }).start();
    }

    private void deleteSelected() {
        List<String> selectedNames = new ArrayList<>();
        if (isPhotoView) {
            for (PhotoAdapter.PhotoItem item : photoAdapter.getItems()) {
                if (item.isSelected && !item.name.endsWith("/")) {
                    selectedNames.add(item.name);
                }
            }
        } else {
            for (AllFilesAdapter.FileItem item : allFilesAdapter.getItems()) {
                if (item.isSelected && !item.name.endsWith("/")) {
                    selectedNames.add(item.name);
                }
            }
        }
        if (selectedNames.isEmpty()) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("删除云端文件")
                .setMessage("确定要删除选中的 " + selectedNames.size() + " 个文件吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    Toast.makeText(this, "开始删除...", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        int success = 0, fail = 0;
                        for (String name : selectedNames) {
                            String remotePath = currentPath.isEmpty() ? name : currentPath + "/" + name;
                            boolean ok = client.deleteFile(remotePath);
                            if (ok) success++; else fail++;
                        }
                        final int finalSuccess = success;
                        final int finalFail = fail;
                        mainHandler.post(() -> {
                            tvCloudCount.setText("删除完成: 成功 " + finalSuccess + ", 失败 " + finalFail);
                            Toast.makeText(CloudActivity.this, "删除完成", Toast.LENGTH_LONG).show();
                            loadDirectory(currentPath);
                        });
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}

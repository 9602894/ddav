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

    private PhotoAdapter photoAdapter;      // 用于相册
    private AllFilesAdapter allFilesAdapter; // 用于全部文件
    private RecyclerView.Adapter currentAdapter;
    private boolean isPhotoView = true;      // 默认相册

    private WebDAVClient client;
    private String currentPath = "";
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<String> localFileNames = new ArrayList<>();

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

        client = WebDAVClientHolder.getClient();
        if (client == null) {
            Toast.makeText(this, "请先在主界面连接服务器", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 获取类型
        String type = getIntent().getStringExtra("type");
        isPhotoView = !"all".equals(type); // 默认 photo

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
            currentAdapter = photoAdapter;
            // 设置点击监听
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
        } else {
            allFilesAdapter = new AllFilesAdapter(this);
            allFilesAdapter.setCloudView(true);
            allFilesAdapter.setShowLocalBadge(true);
            currentAdapter = allFilesAdapter;
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
        rvCloud.setAdapter(currentAdapter);

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
                                    // 目录
                                    PhotoAdapter.PhotoItem fi = new PhotoAdapter.PhotoItem(item);
                                    fi.name = item;
                                    fi.displayName = item;
                                    fi.isOnCloud = true;
                                    fi.isOnLocal = false;
                                    fi.remoteUrl = null;
                                    fi.isVideo = false;
                                    list.add(fi);
                                } else {
                                    // 仅当是图片或视频时才添加
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
                } else {
                    // 全部文件
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
                                AllFilesAdapter.FileItem fi = new AllFilesAdapter.FileItem(item);
                                fi.name = item;
                                fi.displayName = item;
                                fi.isOnCloud = true;
                                fi.isOnLocal = !item.endsWith("/") && localFileNames.contains(item);
                                if (!item.endsWith("/")) {
                                    String remotePath = currentPath.isEmpty() ? item : currentPath + "/" + item;
                                    // 不需要远程URL，因为没有缩略图
                                }
                                list.add(fi);
                            }
                            Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name));
                            tvCloudCount.setText(list.size() + " 个项目");
                        }
                    } else {
                        tvCloudCount.setText("空目录");
                    }
                    allFilesAdapter.setItems(list);
                }
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
        tvCloudSelected.setText("已选择 " + count + " 项");
    }

    private void downloadSelected() {
        // 实现略，与之前类似，但需根据 currentAdapter 获取选中的文件列表
        // ...
    }

    private void deleteSelected() {
        // 实现略
        // ...
    }
}

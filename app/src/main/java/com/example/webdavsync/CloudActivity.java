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

    private PhotoAdapter adapter;
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

        WebDAVClient.updateOkHttpClient(
                client.getUsername() != null ? client.getUsername() : "",
                client.getPassword() != null ? client.getPassword() : ""
        );

        collectLocalFiles();

        rvCloud.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new PhotoAdapter(this);
        adapter.setCloudView(true);
        adapter.setShowLocalBadge(true);
        rvCloud.setAdapter(adapter);

        adapter.setOnItemClickListener((item, position) -> updateSelectedCount());
        adapter.setOnItemLongClickListener((item, position) -> {
            showDeleteDialog(item, position);
        });

        loadDirectory("");

        // 点击路径返回根目录
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
                            // 判断是否为目录：以 / 结尾
                            boolean isDir = item.endsWith("/");
                            PhotoAdapter.PhotoItem fi = new PhotoAdapter.PhotoItem(item);
                            fi.name = item;
                            fi.displayName = isDir ? item.substring(0, item.length() - 1) : item;
                            fi.isOnCloud = true;
                            fi.isOnLocal = !isDir && localFileNames.contains(item);
                            if (!isDir) {
                                String remotePath = currentPath.isEmpty() ? item : currentPath + "/" + item;
                                fi.remoteUrl = client.getServerUrl() + "/" + remotePath;
                                String ext = item.substring(item.lastIndexOf('.') + 1).toLowerCase();
                                fi.isVideo = ext.matches("mp4|3gp|avi|mkv|mov|webm");
                            } else {
                                fi.remoteUrl = null;
                                fi.isVideo = false;
                            }
                            list.add(fi);
                        }
                        Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name));
                        tvCloudCount.setText(list.size() + " 个项目");
                    }
                } else {
                    tvCloudCount.setText("空目录");
                }

                adapter.setItems(list);
                updateSelectedCount();
            });
        }).start();
    }

    private void updateSelectedCount() {
        int count = 0;
        for (PhotoAdapter.PhotoItem item : adapter.getItems()) {
            if (item.isSelected) count++;
        }
        tvCloudSelected.setText("已选择 " + count + " 项");
    }

    private void downloadSelected() {
        // ... 与之前相同
    }

    private void deleteSelected() {
        // ... 与之前相同
    }

    private void showDeleteDialog(PhotoAdapter.PhotoItem item, int position) {
        // ... 与之前相同
    }
}

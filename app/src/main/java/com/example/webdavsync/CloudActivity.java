package com.example.webdavsync;

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
import java.util.List;

public class CloudActivity extends AppCompatActivity {

    private RecyclerView rvCloud;
    private TextView tvCloudPath, tvCloudCount, tvCloudSelected;
    private Button btnDownload, btnUp;
    private ImageView ivBack;

    private PhotoAdapter adapter;
    private WebDAVClient client;
    private String currentPath = "";
    private Handler mainHandler = new Handler(Looper.getMainLooper());

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
        ivBack = findViewById(R.id.iv_back);

        client = WebDAVClientHolder.getClient();
        if (client == null) {
            Toast.makeText(this, "请先在主界面连接服务器", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 更新 Glide 的 OkHttpClient 凭证
        String user = client.getUsername();
        String pass = client.getPassword();
        WebDAVClient.updateOkHttpClient(user != null ? user : "", pass != null ? pass : "");

        rvCloud.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new PhotoAdapter(this);
        adapter.setCloudView(true);
        rvCloud.setAdapter(adapter);

        adapter.setOnItemClickListener((item, position) -> updateSelectedCount());

        loadDirectory("");

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
                            if (item.endsWith("/")) continue; // 跳过目录
                            PhotoAdapter.PhotoItem fi = new PhotoAdapter.PhotoItem(item);
                            fi.name = item;
                            fi.isOnCloud = true;
                            // 构造远程 URL
                            String remotePath = currentPath.isEmpty() ? item : currentPath + "/" + item;
                            fi.remoteUrl = client.getServerUrl() + "/" + remotePath;
                            String ext = item.substring(item.lastIndexOf('.') + 1).toLowerCase();
                            fi.isVideo = ext.matches("mp4|3gp|avi|mkv|mov|webm");
                            list.add(fi);
                        }
                        tvCloudCount.setText(list.size() + " 个文件");
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
        List<PhotoAdapter.PhotoItem> selected = new ArrayList<>();
        for (PhotoAdapter.PhotoItem item : adapter.getItems()) {
            if (item.isSelected) {
                selected.add(item);
            }
        }

        if (selected.isEmpty()) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "开始下载 " + selected.size() + " 个文件", Toast.LENGTH_SHORT).show();
        btnDownload.setEnabled(false);

        new Thread(() -> {
            int success = 0, fail = 0;
            for (PhotoAdapter.PhotoItem item : selected) {
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadDir.exists()) downloadDir.mkdirs();

                String remotePath = currentPath.isEmpty() ? item.name : currentPath + "/" + item.name;
                File destFile = new File(downloadDir, item.name);
                int count = 1;
                String name = item.name;
                String ext = "";
                int dot = item.name.lastIndexOf('.');
                if (dot > 0) { name = item.name.substring(0, dot); ext = item.name.substring(dot); }
                while (destFile.exists()) {
                    destFile = new File(downloadDir, name + "_" + count + ext);
                    count++;
                }

                boolean ok = client.downloadFile(remotePath, destFile);
                if (ok) success++; else fail++;
            }

            final int finalSuccess = success;
            final int finalFail = fail;
            mainHandler.post(() -> {
                btnDownload.setEnabled(true);
                tvCloudCount.setText("下载完成: 成功 " + finalSuccess + ", 失败 " + finalFail);
                Toast.makeText(CloudActivity.this,
                        "下载完成: 成功 " + finalSuccess + ", 失败 " + finalFail,
                        Toast.LENGTH_LONG).show();
                for (PhotoAdapter.PhotoItem item : adapter.getItems()) {
                    item.isSelected = false;
                }
                adapter.notifyDataSetChanged();
                updateSelectedCount();
            });
        }).start();
    }
}

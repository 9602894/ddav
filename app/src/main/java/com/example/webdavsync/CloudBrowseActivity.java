package com.example.webdavsync;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CloudBrowseActivity extends AppCompatActivity {

    private RecyclerView rvCloud;
    private TextView tvCloudPath, tvCloudStatus;
    private Button btnCloudUp, btnCloudRefresh, btnCloudDownload;

    private FileAdapter adapter;
    private WebDAVClient client;
    private String currentPath = "";
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud_browse);

        rvCloud = findViewById(R.id.rv_cloud_files);
        tvCloudPath = findViewById(R.id.tv_cloud_path);
        tvCloudStatus = findViewById(R.id.tv_cloud_status);
        btnCloudUp = findViewById(R.id.btn_cloud_up);
        btnCloudRefresh = findViewById(R.id.btn_cloud_refresh);
        btnCloudDownload = findViewById(R.id.btn_cloud_download);

        client = WebDAVClientHolder.getClient();
        if (client == null) {
            Toast.makeText(this, "未连接服务器", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        rvCloud.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new FileAdapter(this, false);
        rvCloud.setAdapter(adapter);

        loadDirectory("");

        btnCloudUp.setOnClickListener(v -> {
            if (currentPath.isEmpty()) {
                Toast.makeText(this, "已在根目录", Toast.LENGTH_SHORT).show();
                return;
            }
            int lastSlash = currentPath.lastIndexOf('/');
            String parent = lastSlash > 0 ? currentPath.substring(0, lastSlash) : "";
            loadDirectory(parent);
        });

        btnCloudRefresh.setOnClickListener(v -> loadDirectory(currentPath));

        btnCloudDownload.setOnClickListener(v -> downloadSelected());
    }

    private void loadDirectory(String path) {
        currentPath = path == null ? "" : path;
        tvCloudPath.setText("云端: /" + currentPath);
        tvCloudStatus.setText("加载中...");

        new Thread(() -> {
            List<String> items = client.listDirectory(currentPath);
            mainHandler.post(() -> {
                List<FileAdapter.FileItem> list = new ArrayList<>();
                if (items != null && !items.isEmpty()) {
                    boolean hasError = false;
                    for (String item : items) {
                        if (item.startsWith("错误") || item.startsWith("网络错误") || item.startsWith("警告")) {
                            tvCloudStatus.setText(item);
                            hasError = true;
                            break;
                        }
                    }
                    if (hasError) {
                        tvCloudStatus.setText("加载失败");
                    } else {
                        tvCloudStatus.setText("共 " + items.size() + " 项");
                        for (String item : items) {
                            FileAdapter.FileItem fi = new FileAdapter.FileItem(item);
                            fi.remotePath = currentPath.isEmpty() ? item : currentPath + "/" + item;
                            list.add(fi);
                        }
                    }
                } else {
                    tvCloudStatus.setText("空目录");
                }
                adapter.setItems(list);
            });
        }).start();
    }

    private void downloadSelected() {
        List<FileAdapter.FileItem> selected = new ArrayList<>();
        for (FileAdapter.FileItem fi : adapter.getItems()) {
            if (fi.isSelected && !fi.name.endsWith("/")) {
                selected.add(fi);
            }
        }
        if (selected.isEmpty()) {
            Toast.makeText(this, "请先选中文件", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "开始下载 " + selected.size() + " 个文件", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int success = 0, fail = 0;
            for (FileAdapter.FileItem fi : selected) {
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadDir.exists()) downloadDir.mkdirs();
                File destFile = new File(downloadDir, fi.name);
                // 处理重名
                int count = 1;
                String name = fi.name;
                String ext = "";
                int dot = fi.name.lastIndexOf('.');
                if (dot > 0) { name = fi.name.substring(0, dot); ext = fi.name.substring(dot); }
                while (destFile.exists()) {
                    destFile = new File(downloadDir, name + "_" + count + ext);
                    count++;
                }
                boolean ok = client.downloadFile(fi.remotePath, destFile);
                if (ok) success++; else fail++;
            }
            final int finalSuccess = success;
            final int finalFail = fail;
            mainHandler.post(() -> {
                tvCloudStatus.setText("下载完成: 成功 " + finalSuccess + ", 失败 " + finalFail);
                Toast.makeText(CloudBrowseActivity.this, "下载完成", Toast.LENGTH_LONG).show();
                // 清除选中状态
                for (FileAdapter.FileItem fi : adapter.getItems()) fi.isSelected = false;
                adapter.notifyDataSetChanged();
            });
        }).start();
    }
}

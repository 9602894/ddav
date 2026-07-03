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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LocalBrowseActivity extends AppCompatActivity {

    private RecyclerView rvLocal;
    private TextView tvLocalPath, tvLocalStatus;
    private Button btnLocalUp, btnLocalUpload;

    private FileAdapter adapter;
    private WebDAVClient client;
    private String currentPath;
    private Set<String> remoteFileNames = new HashSet<>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_browse);

        rvLocal = findViewById(R.id.rv_local_files);
        tvLocalPath = findViewById(R.id.tv_local_path);
        tvLocalStatus = findViewById(R.id.tv_local_status);
        btnLocalUp = findViewById(R.id.btn_local_up);
        btnLocalUpload = findViewById(R.id.btn_local_upload);

        client = WebDAVClientHolder.getClient();
        if (client == null) {
            Toast.makeText(this, "未连接服务器", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        rvLocal.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new FileAdapter(this, true);
        rvLocal.setAdapter(adapter);

        currentPath = Environment.getExternalStorageDirectory() + "/Download";
        loadLocalDirectory(currentPath);

        btnLocalUp.setOnClickListener(v -> {
            File parent = new File(currentPath).getParentFile();
            if (parent != null) loadLocalDirectory(parent.getAbsolutePath());
            else Toast.makeText(this, "已在根目录", Toast.LENGTH_SHORT).show();
        });

        btnLocalUpload.setOnClickListener(v -> uploadSelected());
    }

    private void loadLocalDirectory(String path) {
        currentPath = path;
        tvLocalPath.setText("本地: " + currentPath);
        tvLocalStatus.setText("加载中...");

        new Thread(() -> {
            // 获取远程文件名列表（用于标记是否已上传）
            List<String> remoteItems = client.listDirectory("");
            remoteFileNames.clear();
            for (String item : remoteItems) {
                if (!item.endsWith("/")) {
                    remoteFileNames.add(item);
                }
            }

            File dir = new File(currentPath);
            List<FileAdapter.FileItem> list = new ArrayList<>();
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    // 添加父目录项？我们可以用“..”但网格不直观，我们用返回上级按钮。
                    // 只显示文件和目录，目录以 / 结尾
                    for (File f : files) {
                        FileAdapter.FileItem fi = new FileAdapter.FileItem(f.getName());
                        fi.file = f;
                        if (f.isDirectory()) {
                            fi.name = f.getName() + "/";
                        } else {
                            // 检查是否已上传
                            if (remoteFileNames.contains(f.getName())) {
                                fi.name = f.getName() + " ☁️";
                            }
                        }
                        list.add(fi);
                    }
                }
            }
            mainHandler.post(() -> {
                tvLocalStatus.setText("共 " + list.size() + " 项");
                adapter.setItems(list);
            });
        }).start();
    }

    private void uploadSelected() {
        List<FileAdapter.FileItem> selected = new ArrayList<>();
        for (FileAdapter.FileItem fi : adapter.getItems()) {
            if (fi.isSelected && fi.file != null && fi.file.isFile()) {
                selected.add(fi);
            }
        }
        if (selected.isEmpty()) {
            Toast.makeText(this, "请先选中文件", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "开始上传 " + selected.size() + " 个文件", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int success = 0, fail = 0;
            for (FileAdapter.FileItem fi : selected) {
                boolean ok = client.uploadFile("", fi.file);
                if (ok) success++; else fail++;
            }
            final int finalSuccess = success;
            final int finalFail = fail;
            mainHandler.post(() -> {
                tvLocalStatus.setText("上传完成: 成功 " + finalSuccess + ", 失败 " + finalFail);
                Toast.makeText(LocalBrowseActivity.this, "上传完成", Toast.LENGTH_LONG).show();
                loadLocalDirectory(currentPath); // 刷新
            });
        }).start();
    }
}

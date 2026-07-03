package com.example.webdavsync;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class CloudBrowseActivity extends AppCompatActivity {

    private ListView lvCloudFiles;
    private TextView tvCloudPath, tvCloudStatus;
    private Button btnCloudUp, btnCloudRefresh;

    private ArrayAdapter<String> adapter;
    private List<String> entries = new ArrayList<>();
    private WebDAVClient client;
    private String currentPath = "";
    private String serverUrl;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void start(Activity from, WebDAVClient client, String serverUrl) {
        Intent intent = new Intent(from, CloudBrowseActivity.class);
        intent.putExtra("server_url", serverUrl);
        from.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud_browse);

        lvCloudFiles = findViewById(R.id.lv_cloud_files);
        tvCloudPath = findViewById(R.id.tv_cloud_path);
        tvCloudStatus = findViewById(R.id.tv_cloud_status);
        btnCloudUp = findViewById(R.id.btn_cloud_up);
        btnCloudRefresh = findViewById(R.id.btn_cloud_refresh);

        serverUrl = getIntent().getStringExtra("server_url");

        // 从 MainActivity 获取已连接的 client（通过静态变量或单例）
        // 简化方案：使用 Application 级别的单例，或重新创建
        // 这里我们通过 Intent 传递 serverUrl，但 client 需要重新创建
        // 更好的方式：使用 Application 保存全局 client
        // 为了简化，我们重新连接（使用保存的密码）
        // 实际应用中建议使用 Application 单例

        // 从 MainActivity 获取连接信息（通过静态变量）
        // 这里使用一个简单的全局持有者
        client = WebDAVClientHolder.getClient();

        if (client == null) {
            Toast.makeText(this, "未连接到服务器，请先连接", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        loadDirectory("");

        lvCloudFiles.setOnItemClickListener((parent, view, position, id) -> {
            String item = entries.get(position);
            if (item.startsWith("错误") || item.startsWith("网络错误")) {
                return;
            }
            if (item.endsWith("/")) {
                // 进入子目录
                String newPath = currentPath.isEmpty() ? item.substring(0, item.length() - 1)
                        : currentPath + "/" + item.substring(0, item.length() - 1);
                loadDirectory(newPath);
            } else {
                Toast.makeText(CloudBrowseActivity.this, "文件: " + item, Toast.LENGTH_SHORT).show();
            }
        });

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
    }

    private void loadDirectory(String path) {
        currentPath = path == null ? "" : path;
        tvCloudPath.setText("云端: /" + currentPath);
        tvCloudStatus.setText("加载中...");

        new Thread(() -> {
            List<String> items = client.listDirectory(currentPath);
            mainHandler.post(() -> {
                entries.clear();
                if (items != null && !items.isEmpty()) {
                    // 检查是否有错误信息
                    if (items.get(0).startsWith("错误") || items.get(0).startsWith("网络错误")) {
                        tvCloudStatus.setText(items.get(0));
                        entries.add("(加载失败)");
                    } else {
                        tvCloudStatus.setText("共 " + items.size() + " 个项目");
                        entries.addAll(items);
                        // 按名称排序
                        java.util.Collections.sort(entries);
                    }
                } else {
                    tvCloudStatus.setText("空目录");
                    entries.add("(空)");
                }
                adapter = new ArrayAdapter<>(CloudBrowseActivity.this,
                        android.R.layout.simple_list_item_1, entries);
                lvCloudFiles.setAdapter(adapter);
            });
        }).start();
    }
}

// 全局持有者 - 简单单例
class WebDAVClientHolder {
    private static WebDAVClient client;

    public static void setClient(WebDAVClient c) {
        client = c;
    }

    public static WebDAVClient getClient() {
        return client;
    }
}

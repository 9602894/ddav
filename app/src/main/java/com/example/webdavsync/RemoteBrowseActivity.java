package com.example.webdavsync;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class RemoteBrowseActivity extends AppCompatActivity {
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> entries = new ArrayList<>();
    private WebDAVClient client;
    private String currentPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_browse);

        listView = findViewById(R.id.list_remote);

        String server = getIntent().getStringExtra("server_url");
        String user = getIntent().getStringExtra("username");
        String pass = getIntent().getStringExtra("password");
        currentPath = getIntent().getStringExtra("current_path");
        if (currentPath == null) currentPath = "/";

        client = new WebDAVClient(server, user, pass);

        loadDirectory(currentPath);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String item = entries.get(position);
            if (item.equals("../")) {
                // 返回父目录
                String parentPath = currentPath.substring(0, currentPath.lastIndexOf('/'));
                if (parentPath.isEmpty()) parentPath = "/";
                loadDirectory(parentPath);
            } else if (item.endsWith("/")) {
                String newPath = currentPath.equals("/") ? item : currentPath + "/" + item;
                loadDirectory(newPath);
            } else {
                Toast.makeText(RemoteBrowseActivity.this, "请选择目录（以 / 结尾）", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadDirectory(String path) {
        currentPath = path;
        new Thread(() -> {
            List<String> items = client.listDirectory(path);
            runOnUiThread(() -> {
                entries.clear();
                if (items != null && !items.isEmpty() && items.get(0).startsWith("错误")) {
                    // 显示错误信息
                    Toast.makeText(RemoteBrowseActivity.this, items.get(0), Toast.LENGTH_LONG).show();
                    entries.add("加载失败，请检查路径或权限");
                } else {
                    if (!path.equals("/")) {
                        entries.add("../");
                    }
                    for (String item : items) {
                        // 显示所有条目（包括文件和目录）
                        entries.add(item);
                    }
                    if (entries.isEmpty()) {
                        entries.add("(空目录)");
                    }
                }
                adapter = new ArrayAdapter<>(RemoteBrowseActivity.this, android.R.layout.simple_list_item_1, entries);
                listView.setAdapter(adapter);
            });
        }).start();
    }

    public void onSelectPath(View view) {
        Intent result = new Intent();
        result.putExtra("selected_path", currentPath);
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    public void onGoBack(View view) {
        finish();
    }
}

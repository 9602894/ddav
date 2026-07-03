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
            if (item.endsWith("/")) {
                // 进入子目录
                String newPath = currentPath.equals("/") ? item : currentPath + "/" + item;
                loadDirectory(newPath);
            } else {
                // 选择文件？这里我们只选目录，忽略文件
                Toast.makeText(RemoteBrowseActivity.this, "请选择目录", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadDirectory(String path) {
        currentPath = path;
        new Thread(() -> {
            List<String> items = client.listDirectory(path);
            runOnUiThread(() -> {
                entries.clear();
                // 添加父目录 ".."
                if (!path.equals("/")) {
                    entries.add("../");
                }
                // 只显示目录（以 / 结尾）
                for (String item : items) {
                    if (item.endsWith("/") || item.contains(".")) { // 简单判断，可能不准确，但可用
                        entries.add(item);
                    }
                }
                // 若列表为空，添加提示
                if (entries.isEmpty()) {
                    entries.add("(空目录)");
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

package com.example.webdavsync;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class CloudFilesActivity extends AppCompatActivity {
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> entries = new ArrayList<>();
    private WebDAVClient client;
    private String currentPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud_files);

        listView = findViewById(R.id.list_cloud);

        String server = getIntent().getStringExtra("server_url");
        String user = getIntent().getStringExtra("username");
        String pass = getIntent().getStringExtra("password");
        currentPath = getIntent().getStringExtra("remote_path");
        if (currentPath == null) currentPath = "/";

        client = new WebDAVClient(server, user, pass);
        loadDirectory(currentPath);
    }

    private void loadDirectory(String path) {
        currentPath = path;
        new Thread(() -> {
            List<String> items = client.listDirectory(path);
            runOnUiThread(() -> {
                entries.clear();
                entries.addAll(items);
                if (entries.isEmpty()) entries.add("(空目录)");
                adapter = new ArrayAdapter<>(CloudFilesActivity.this, android.R.layout.simple_list_item_1, entries);
                listView.setAdapter(adapter);
                setTitle("云端: " + path);
            });
        }).start();
    }
}

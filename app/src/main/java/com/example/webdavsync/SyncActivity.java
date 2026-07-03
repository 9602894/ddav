package com.example.webdavsync;

import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SyncActivity extends AppCompatActivity {
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> fileNames = new ArrayList<>();
    private List<String> cloudStatus = new ArrayList<>(); // 用于显示状态
    private WebDAVClient client;
    private String remoteDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync);

        listView = findViewById(R.id.list_sync);
        Button btnSync = findViewById(R.id.btn_sync_selected);

        String server = getIntent().getStringExtra("server_url");
        String user = getIntent().getStringExtra("username");
        String pass = getIntent().getStringExtra("password");
        remoteDir = getIntent().getStringExtra("remote_path");
        if (remoteDir == null) remoteDir = "/";
        String localPath = getIntent().getStringExtra("local_path");
        if (localPath == null) localPath = Environment.getExternalStorageDirectory() + "/Download";

        client = new WebDAVClient(server, user, pass);

        File dir = new File(localPath);
        if (dir.exists() && dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                if (f.isFile()) {
                    fileNames.add(f.getName());
                }
            }
        }

        // 异步获取云端文件列表以显示状态
        new Thread(() -> {
            List<String> remoteFiles = client.listDirectory(remoteDir);
            // 解析文件名
            List<String> remoteNames = new ArrayList<>();
            for (String item : remoteFiles) {
                if (!item.endsWith("/")) {
                    remoteNames.add(item.substring(item.lastIndexOf("/") + 1));
                }
            }
            final List<String> statusList = new ArrayList<>();
            for (String name : fileNames) {
                statusList.add(name + (remoteNames.contains(name) ? "  ☁️ (已上传)" : "  ⬆️ (待上传)"));
            }
            runOnUiThread(() -> {
                cloudStatus.addAll(statusList);
                adapter = new ArrayAdapter<>(SyncActivity.this, android.R.layout.simple_list_item_multiple_choice, cloudStatus);
                listView.setAdapter(adapter);
                listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            });
        }).start();

        btnSync.setOnClickListener(v -> {
            List<String> toUpload = new ArrayList<>();
            for (int i = 0; i < listView.getCount(); i++) {
                if (listView.isItemChecked(i)) {
                    toUpload.add(fileNames.get(i));
                }
            }
            if (toUpload.isEmpty()) {
                Toast.makeText(SyncActivity.this, "请选择文件", Toast.LENGTH_SHORT).show();
                return;
            }
            uploadSelected(toUpload);
        });
    }

    private void uploadSelected(List<String> names) {
        Toast.makeText(this, "开始上传...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int success = 0;
            for (String name : names) {
                File file = new File(Environment.getExternalStorageDirectory() + "/Download/" + name);
                if (client.uploadFile(remoteDir, file)) {
                    success++;
                }
            }
            final int finalSuccess = success;
            runOnUiThread(() -> {
                Toast.makeText(SyncActivity.this, "上传完成: " + finalSuccess + "/" + names.size() + " 成功", Toast.LENGTH_LONG).show();
                // 刷新列表
                finish();
                startActivity(getIntent()); // 简单刷新
            });
        }).start();
    }
}

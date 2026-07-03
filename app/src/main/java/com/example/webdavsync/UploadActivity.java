package com.example.webdavsync;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
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

public class UploadActivity extends AppCompatActivity {
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> fileNames = new ArrayList<>();
    private List<String> selectedFiles = new ArrayList<>(); // 存储文件路径
    private WebDAVClient client;
    private String remoteDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        listView = findViewById(R.id.list_upload);
        Button btnUpload = findViewById(R.id.btn_upload_selected);

        String server = getIntent().getStringExtra("server_url");
        String user = getIntent().getStringExtra("username");
        String pass = getIntent().getStringExtra("password");
        remoteDir = getIntent().getStringExtra("remote_path");
        if (remoteDir == null) remoteDir = "/";

        client = new WebDAVClient(server, user, pass);

        // 加载本地目录（默认 Download）
        String localPath = getIntent().getStringExtra("local_path");
        if (localPath == null) localPath = Environment.getExternalStorageDirectory() + "/Download";
        File dir = new File(localPath);
        if (dir.exists() && dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                if (f.isFile()) {
                    fileNames.add(f.getName());
                }
            }
        }
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, fileNames);
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        btnUpload.setOnClickListener(v -> {
            selectedFiles.clear();
            for (int i = 0; i < listView.getCount(); i++) {
                if (listView.isItemChecked(i)) {
                    selectedFiles.add(fileNames.get(i));
                }
            }
            if (selectedFiles.isEmpty()) {
                Toast.makeText(UploadActivity.this, "请选择文件", Toast.LENGTH_SHORT).show();
                return;
            }
            uploadFiles();
        });
    }

    private void uploadFiles() {
        Toast.makeText(this, "开始上传 " + selectedFiles.size() + " 个文件", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int success = 0;
            for (String name : selectedFiles) {
                File file = new File(Environment.getExternalStorageDirectory() + "/Download/" + name);
                if (client.uploadFile(remoteDir, file)) {
                    success++;
                }
            }
            final int finalSuccess = success;
            runOnUiThread(() -> {
                Toast.makeText(UploadActivity.this, "上传完成: " + finalSuccess + "/" + selectedFiles.size() + " 成功", Toast.LENGTH_LONG).show();
            });
        }).start();
    }
}

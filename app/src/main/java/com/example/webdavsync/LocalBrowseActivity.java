package com.example.webdavsync;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LocalBrowseActivity extends AppCompatActivity {

    private ListView lvLocalFiles;
    private TextView tvLocalPath, tvLocalStatus;
    private Button btnLocalUp, btnUploadSelected;

    private ArrayAdapter<String> adapter;
    private List<String> entries = new ArrayList<>();
    private List<String> filePaths = new ArrayList<>(); // 完整路径
    private String currentPath;
    private WebDAVClient client;
    private Set<String> remoteFileNames = new HashSet<>();

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_browse);

        lvLocalFiles = findViewById(R.id.lv_local_files);
        tvLocalPath = findViewById(R.id.tv_local_path);
        tvLocalStatus = findViewById(R.id.tv_local_status);
        btnLocalUp = findViewById(R.id.btn_local_up);
        btnUploadSelected = findViewById(R.id.btn_upload_selected);

        client = WebDAVClientHolder.getClient();
        if (client == null) {
            Toast.makeText(this, "未连接到服务器，请先连接", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 默认从 Download 目录开始
        currentPath = Environment.getExternalStorageDirectory() + "/Download";
        loadLocalDirectory(currentPath);

        // 点击条目：切换选中状态（多选）
        lvLocalFiles.setOnItemClickListener((parent, view, position, id) -> {
            String item = entries.get(position);
            if (item.equals("..")) {
                File parentFile = new File(currentPath).getParentFile();
                if (parentFile != null) {
                    loadLocalDirectory(parentFile.getAbsolutePath());
                }
                return;
            }
            // 切换选中状态
            boolean isChecked = lvLocalFiles.isItemChecked(position);
            lvLocalFiles.setItemChecked(position, !isChecked);
        });

        btnLocalUp.setOnClickListener(v -> {
            File parentFile = new File(currentPath).getParentFile();
            if (parentFile != null) {
                loadLocalDirectory(parentFile.getAbsolutePath());
            } else {
                Toast.makeText(this, "已在根目录", Toast.LENGTH_SHORT).show();
            }
        });

        btnUploadSelected.setOnClickListener(v -> uploadSelectedFiles());
    }

    private void loadLocalDirectory(String path) {
        currentPath = path;
        tvLocalPath.setText("本地: " + currentPath);
        tvLocalStatus.setText("加载中...");

        new Thread(() -> {
            List<String> remoteItems = client.listDirectory("");
            Set<String> remoteNames = new HashSet<>();
            for (String item : remoteItems) {
                if (!item.endsWith("/")) {
                    remoteNames.add(item);
                }
            }
            remoteFileNames = remoteNames;

            mainHandler.post(() -> {
                File dir = new File(currentPath);
                if (!dir.exists() || !dir.isDirectory()) {
                    tvLocalStatus.setText("目录不存在");
                    entries.clear();
                    filePaths.clear();
                    adapter = new ArrayAdapter<>(LocalBrowseActivity.this,
                            android.R.layout.simple_list_item_multiple_choice, entries);
                    lvLocalFiles.setAdapter(adapter);
                    lvLocalFiles.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                    return;
                }

                File[] files = dir.listFiles();
                entries.clear();
                filePaths.clear();

                if (files != null) {
                    // 父目录
                    if (!currentPath.equals("/") && dir.getParentFile() != null) {
                        entries.add("..");
                        filePaths.add(dir.getParentFile().getAbsolutePath());
                    }

                    List<File> dirs = new ArrayList<>();
                    List<File> fileList = new ArrayList<>();
                    for (File f : files) {
                        if (f.isDirectory()) {
                            dirs.add(f);
                        } else {
                            fileList.add(f);
                        }
                    }
                    java.util.Collections.sort(dirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                    java.util.Collections.sort(fileList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

                    for (File d : dirs) {
                        entries.add(d.getName() + "/");
                        filePaths.add(d.getAbsolutePath());
                    }
                    for (File f : fileList) {
                        String name = f.getName();
                        if (remoteFileNames.contains(name)) {
                            entries.add(name + " ☁️");
                        } else {
                            entries.add(name);
                        }
                        filePaths.add(f.getAbsolutePath());
                    }
                }

                tvLocalStatus.setText("共 " + entries.size() + " 个项目 (点击选中，再次点击取消)");
                adapter = new ArrayAdapter<>(LocalBrowseActivity.this,
                        android.R.layout.simple_list_item_multiple_choice, entries);
                lvLocalFiles.setAdapter(adapter);
                lvLocalFiles.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                // 清除所有选中状态
                for (int i = 0; i < lvLocalFiles.getCount(); i++) {
                    lvLocalFiles.setItemChecked(i, false);
                }
            });
        }).start();
    }

    private void uploadSelectedFiles() {
        List<Integer> selectedPositions = new ArrayList<>();
        for (int i = 0; i < lvLocalFiles.getCount(); i++) {
            if (lvLocalFiles.isItemChecked(i)) {
                selectedPositions.add(i);
            }
        }
        if (selectedPositions.isEmpty()) {
            Toast.makeText(this, "请先点击选中文件", Toast.LENGTH_SHORT).show();
            return;
        }

        List<File> toUpload = new ArrayList<>();
        for (int pos : selectedPositions) {
            String path = filePaths.get(pos);
            if (path != null) {
                File f = new File(path);
                if (f.exists() && f.isFile()) {
                    toUpload.add(f);
                }
            }
        }
        if (toUpload.isEmpty()) {
            Toast.makeText(this, "选中的项目中没有文件", Toast.LENGTH_SHORT).show();
            return;
        }

        tvLocalStatus.setText("上传中... " + toUpload.size() + " 个文件");
        new Thread(() -> {
            int success = 0, fail = 0;
            for (File f : toUpload) {
                if (client.uploadFile("", f)) {
                    success++;
                } else {
                    fail++;
                }
            }
            final int finalSuccess = success;
            final int finalFail = fail;
            mainHandler.post(() -> {
                tvLocalStatus.setText("上传完成: 成功 " + finalSuccess + ", 失败 " + finalFail);
                Toast.makeText(LocalBrowseActivity.this,
                        "上传完成: 成功 " + finalSuccess + ", 失败 " + finalFail,
                        Toast.LENGTH_LONG).show();
                loadLocalDirectory(currentPath);
            });
        }).start();
    }
}

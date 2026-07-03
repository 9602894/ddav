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

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Set<String> remoteFileNames = new HashSet<>();

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

        lvLocalFiles.setOnItemClickListener((parent, view, position, id) -> {
            String item = entries.get(position);
            if (item.equals("..")) {
                File parentFile = new File(currentPath).getParentFile();
                if (parentFile != null) {
                    loadLocalDirectory(parentFile.getAbsolutePath());
                }
                return;
            }
            String fullPath = filePaths.get(position);
            if (fullPath != null) {
                File file = new File(fullPath);
                if (file.isDirectory()) {
                    loadLocalDirectory(fullPath);
                } else {
                    Toast.makeText(LocalBrowseActivity.this, "文件: " + item, Toast.LENGTH_SHORT).show();
                }
            }
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

        // 先获取远程文件列表（用于标记已上传）
        new Thread(() -> {
            List<String> remoteItems = client.listDirectory("");
            Set<String> remoteNames = new HashSet<>();
            for (String item : remoteItems) {
                if (!item.endsWith("/")) {
                    // 提取文件名（可能带路径，我们只取最后一部分）
                    int lastSlash = item.lastIndexOf('/');
                    String name = lastSlash > 0 ? item.substring(lastSlash + 1) : item;
                    remoteNames.add(name);
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
                    // 添加父目录
                    if (!currentPath.equals("/") && dir.getParentFile() != null) {
                        entries.add("..");
                        filePaths.add(dir.getParentFile().getAbsolutePath());
                    }

                    // 先收集目录和文件
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
                        // 检查是否已上传
                        if (remoteFileNames.contains(name)) {
                            entries.add(name + " ☁️");
                        } else {
                            entries.add(name);
                        }
                        filePaths.add(f.getAbsolutePath());
                    }
                }

                tvLocalStatus.setText("共 " + entries.size() + " 个项目");
                adapter = new ArrayAdapter<>(LocalBrowseActivity.this,
                        android.R.layout.simple_list_item_multiple_choice, entries);
                lvLocalFiles.setAdapter(adapter);
                lvLocalFiles.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
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
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
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
            int success = 0;
            int fail = 0;
            for (File f : toUpload) {
                boolean ok = client.uploadFile("", f);
                if (ok) {
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
                // 刷新列表
                loadLocalDirectory(currentPath);
            });
        }).start();
    }
}

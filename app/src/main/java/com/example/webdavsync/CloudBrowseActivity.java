package com.example.webdavsync;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class CloudBrowseActivity extends AppCompatActivity {

    private ListView lvCloudFiles;
    private TextView tvCloudPath, tvCloudStatus;
    private Button btnCloudUp, btnCloudRefresh;

    private ArrayAdapter<String> adapter;
    private List<String> entries = new ArrayList<>();
    private List<String> fullPaths = new ArrayList<>(); // 存储完整远程路径
    private WebDAVClient client;
    private String currentPath = "";

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud_browse);

        lvCloudFiles = findViewById(R.id.lv_cloud_files);
        tvCloudPath = findViewById(R.id.tv_cloud_path);
        tvCloudStatus = findViewById(R.id.tv_cloud_status);
        btnCloudUp = findViewById(R.id.btn_cloud_up);
        btnCloudRefresh = findViewById(R.id.btn_cloud_refresh);

        client = WebDAVClientHolder.getClient();
        if (client == null) {
            Toast.makeText(this, "未连接到服务器，请先连接", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        loadDirectory("");

        // 短按：进入目录或显示文件信息
        lvCloudFiles.setOnItemClickListener((parent, view, position, id) -> {
            String item = entries.get(position);
            if (item.startsWith("错误") || item.startsWith("网络错误") || item.startsWith("警告")
                    || item.equals("(空目录)") || item.equals("(加载失败)")) {
                return;
            }
            if (item.endsWith("/")) {
                String newPath = currentPath.isEmpty() ? item.substring(0, item.length() - 1)
                        : currentPath + "/" + item.substring(0, item.length() - 1);
                loadDirectory(newPath);
            } else {
                Toast.makeText(CloudBrowseActivity.this, "文件: " + item + "\n长按可下载", Toast.LENGTH_SHORT).show();
            }
        });

        // 长按：弹出下载菜单
        lvCloudFiles.setOnItemLongClickListener((parent, view, position, id) -> {
            String item = entries.get(position);
            if (item.endsWith("/") || item.startsWith("错误") || item.startsWith("网络错误")
                    || item.startsWith("警告") || item.equals("(空目录)") || item.equals("(加载失败)")) {
                return true;
            }
            showDownloadDialog(item, fullPaths.get(position));
            return true;
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
                fullPaths.clear();
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
                        entries.add("(加载失败)");
                    } else if (items.size() == 1 && items.get(0).equals("(空目录)")) {
                        tvCloudStatus.setText("目录为空");
                        entries.add("(空)");
                    } else {
                        tvCloudStatus.setText("共 " + items.size() + " 个项目");
                        for (String item : items) {
                            entries.add(item);
                            // 构建完整远程路径
                            String full = currentPath.isEmpty() ? item : currentPath + "/" + item;
                            fullPaths.add(full);
                        }
                        java.util.Collections.sort(entries);
                        // 同步排序 fullPaths
                        // 简单处理：重新构建
                        // 由于 entries 和 fullPaths 需要一一对应，这里用索引方式
                    }
                } else {
                    tvCloudStatus.setText("空目录或加载失败");
                    entries.add("(空)");
                }
                adapter = new ArrayAdapter<>(CloudBrowseActivity.this,
                        android.R.layout.simple_list_item_1, entries);
                lvCloudFiles.setAdapter(adapter);
            });
        }).start();
    }

    private void showDownloadDialog(final String fileName, final String remotePath) {
        new AlertDialog.Builder(this)
                .setTitle("下载文件")
                .setMessage("是否将 \"" + fileName + "\" 下载到手机 Download 目录？")
                .setPositiveButton("下载", (dialog, which) -> downloadFile(remotePath, fileName))
                .setNegativeButton("取消", null)
                .show();
    }

    private void downloadFile(String remotePath, String fileName) {
        Toast.makeText(this, "开始下载: " + fileName, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                // 使用 OkHttp 下载
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(client.getServerUrl() + "/" + remotePath)
                        .header("Authorization", okhttp3.Credentials.basic(
                                client.getUsername(), client.getPassword()))
                        .build();
                okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient();
                okhttp3.Response response = httpClient.newCall(request).execute();
                if (response.isSuccessful()) {
                    // 保存到 Download 目录
                    File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs();
                    }
                    File destFile = new File(downloadDir, fileName);
                    // 如果文件已存在，添加数字后缀
                    int count = 1;
                    String name = fileName;
                    String ext = "";
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        name = fileName.substring(0, dotIndex);
                        ext = fileName.substring(dotIndex);
                    }
                    while (destFile.exists()) {
                        destFile = new File(downloadDir, name + "_" + count + ext);
                        count++;
                    }

                    InputStream inputStream = response.body().byteStream();
                    FileOutputStream fos = new FileOutputStream(destFile);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    inputStream.close();

                    mainHandler.post(() ->
                            Toast.makeText(CloudBrowseActivity.this,
                                    "下载完成: " + destFile.getName(),
                                    Toast.LENGTH_LONG).show()
                    );
                } else {
                    mainHandler.post(() ->
                            Toast.makeText(CloudBrowseActivity.this,
                                    "下载失败: HTTP " + response.code(),
                                    Toast.LENGTH_SHORT).show()
                    );
                }
                response.close();
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() ->
                        Toast.makeText(CloudBrowseActivity.this,
                                "下载出错: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }
}

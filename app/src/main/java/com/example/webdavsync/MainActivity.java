package com.example.webdavsync;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 100;
    private EditText etServerUrl, etUsername, etPassword, etLocalPath;
    private Button btnTest, btnSync, btnBrowse;
    private SharedPreferences prefs;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etServerUrl = findViewById(R.id.et_server_url);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etLocalPath = findViewById(R.id.et_local_path);
        btnTest = findViewById(R.id.btn_test);
        btnSync = findViewById(R.id.btn_sync);
        btnBrowse = findViewById(R.id.btn_browse);

        prefs = getSharedPreferences("webdav_prefs", MODE_PRIVATE);
        loadSettings();

        // 请求权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, perms, REQUEST_PERMISSIONS);
            }
        }

        // 保存配置（输入变化时自动保存）
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) { saveSettings(); }
        };
        etServerUrl.addTextChangedListener(watcher);
        etUsername.addTextChangedListener(watcher);
        etPassword.addTextChangedListener(watcher);
        etLocalPath.addTextChangedListener(watcher);

        btnTest.setOnClickListener(v -> testConnection());
        btnSync.setOnClickListener(v -> startSync());
        btnBrowse.setOnClickListener(v -> {
            // 简单提示，可扩展为文件夹选择器
            Toast.makeText(this, "请手动输入路径，如 /sdcard/Download", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadSettings() {
        etServerUrl.setText(prefs.getString("server_url", ""));
        etUsername.setText(prefs.getString("username", ""));
        etPassword.setText(prefs.getString("password", ""));
        etLocalPath.setText(prefs.getString("local_path", Environment.getExternalStorageDirectory() + "/Download"));
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("server_url", etServerUrl.getText().toString().trim());
        editor.putString("username", etUsername.getText().toString().trim());
        editor.putString("password", etPassword.getText().toString().trim());
        editor.putString("local_path", etLocalPath.getText().toString().trim());
        editor.apply();
    }

    private void testConnection() {
        String serverUrl = etServerUrl.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "测试连接中...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            WebDAVClient client = new WebDAVClient(serverUrl, username, password);
            boolean success = client.testConnection();
            mainHandler.post(() -> {
                if (success) {
                    Toast.makeText(MainActivity.this, "连接成功！", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, "连接失败，请检查配置", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void startSync() {
        String serverUrl = etServerUrl.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String localPath = etLocalPath.getText().toString().trim();

        if (serverUrl.isEmpty() || localPath.isEmpty()) {
            Toast.makeText(this, "请填写完整配置", Toast.LENGTH_SHORT).show();
            return;
        }

        File dir = new File(localPath);
        if (!dir.exists() || !dir.isDirectory()) {
            Toast.makeText(this, "本地目录不存在或不是目录", Toast.LENGTH_LONG).show();
            return;
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            Toast.makeText(this, "本地目录为空，没有文件可同步", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "开始同步，共 " + files.length + " 个文件", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            WebDAVClient client = new WebDAVClient(serverUrl, username, password);
            // 获取远程文件列表
            List<String> remoteFiles = client.listFiles();
            int uploaded = 0, skipped = 0;

            for (File file : files) {
                if (file.isFile()) {
                    if (remoteFiles.contains(file.getName())) {
                        skipped++;
                    } else {
                        boolean ok = client.uploadFile(file);
                        if (ok) uploaded++;
                    }
                }
            }

            final int finalUploaded = uploaded;
            final int finalSkipped = skipped;
            mainHandler.post(() -> {
                String msg = "同步完成！上传 " + finalUploaded + " 个文件，跳过 " + finalSkipped + " 个已存在文件。";
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要存储权限才能读取本地文件", Toast.LENGTH_LONG).show();
            }
        }
    }
}

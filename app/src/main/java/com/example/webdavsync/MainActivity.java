package com.example.webdavsync;

import android.Manifest;
import android.content.Intent;
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

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 100;
    private EditText etServerUrl, etUsername, etPassword, etRemotePath, etLocalPath;
    private Button btnTest, btnUpload, btnSync, btnCloud, btnBrowseRemote;
    private SharedPreferences prefs;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etServerUrl = findViewById(R.id.et_server_url);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etRemotePath = findViewById(R.id.et_remote_path);
        etLocalPath = findViewById(R.id.et_local_path);
        btnTest = findViewById(R.id.btn_test);
        btnUpload = findViewById(R.id.btn_upload);
        btnSync = findViewById(R.id.btn_sync);
        btnCloud = findViewById(R.id.btn_cloud);
        btnBrowseRemote = findViewById(R.id.btn_browse_remote);

        prefs = getSharedPreferences("webdav_prefs", MODE_PRIVATE);
        loadSettings();

        // 权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, perms, REQUEST_PERMISSIONS);
            }
        }

        // 自动保存（输入即存）
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) { saveSettings(); }
        };
        etServerUrl.addTextChangedListener(watcher);
        etUsername.addTextChangedListener(watcher);
        etPassword.addTextChangedListener(watcher);
        etRemotePath.addTextChangedListener(watcher);
        etLocalPath.addTextChangedListener(watcher);

        btnTest.setOnClickListener(v -> testConnection());
        btnBrowseRemote.setOnClickListener(v -> startRemoteBrowse());
        btnUpload.setOnClickListener(v -> startUpload());
        btnSync.setOnClickListener(v -> startSync());
        btnCloud.setOnClickListener(v -> startCloud());
    }

    private void loadSettings() {
        etServerUrl.setText(prefs.getString("server_url", ""));
        etUsername.setText(prefs.getString("username", ""));
        etPassword.setText(prefs.getString("password", ""));
        etRemotePath.setText(prefs.getString("remote_path", "/"));
        etLocalPath.setText(prefs.getString("local_path", Environment.getExternalStorageDirectory() + "/Download"));
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("server_url", etServerUrl.getText().toString().trim());
        editor.putString("username", etUsername.getText().toString().trim());
        editor.putString("password", etPassword.getText().toString().trim());
        editor.putString("remote_path", etRemotePath.getText().toString().trim());
        editor.putString("local_path", etLocalPath.getText().toString().trim());
        editor.apply();
    }

    private void testConnection() {
        String server = etServerUrl.getText().toString().trim();
        String user = etUsername.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        if (server.isEmpty()) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "测试中...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            WebDAVClient client = new WebDAVClient(server, user, pass);
            boolean ok = client.testConnection();
            mainHandler.post(() -> {
                Toast.makeText(MainActivity.this, ok ? "连接成功！" : "连接失败，请检查配置", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void startRemoteBrowse() {
        Intent intent = new Intent(this, RemoteBrowseActivity.class);
        intent.putExtra("server_url", etServerUrl.getText().toString().trim());
        intent.putExtra("username", etUsername.getText().toString().trim());
        intent.putExtra("password", etPassword.getText().toString().trim());
        intent.putExtra("current_path", etRemotePath.getText().toString().trim());
        startActivityForResult(intent, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            String selected = data.getStringExtra("selected_path");
            if (selected != null) {
                etRemotePath.setText(selected);
                saveSettings();
                Toast.makeText(this, "远程目录已设置为: " + selected, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startUpload() {
        Intent intent = new Intent(this, UploadActivity.class);
        intent.putExtra("server_url", etServerUrl.getText().toString().trim());
        intent.putExtra("username", etUsername.getText().toString().trim());
        intent.putExtra("password", etPassword.getText().toString().trim());
        intent.putExtra("remote_path", etRemotePath.getText().toString().trim());
        startActivity(intent);
    }

    private void startSync() {
        Intent intent = new Intent(this, SyncActivity.class);
        intent.putExtra("server_url", etServerUrl.getText().toString().trim());
        intent.putExtra("username", etUsername.getText().toString().trim());
        intent.putExtra("password", etPassword.getText().toString().trim());
        intent.putExtra("remote_path", etRemotePath.getText().toString().trim());
        intent.putExtra("local_path", etLocalPath.getText().toString().trim());
        startActivity(intent);
    }

    private void startCloud() {
        Intent intent = new Intent(this, CloudFilesActivity.class);
        intent.putExtra("server_url", etServerUrl.getText().toString().trim());
        intent.putExtra("username", etUsername.getText().toString().trim());
        intent.putExtra("password", etPassword.getText().toString().trim());
        intent.putExtra("remote_path", etRemotePath.getText().toString().trim());
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
        }
    }
}

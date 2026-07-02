package com.example.webdavsync;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private EditText etServerUrl, etUsername, etPassword, etLocalPath;
    private Button btnTest, btnSync, btnSave;
    private ProgressBar progressBar;
    private TextView tvStatus;

    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "WebDAVConfig";
    private static final String KEY_SERVER = "server_url";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_LOCAL_PATH = "local_path";

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
        btnSave = findViewById(R.id.btn_save);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);

        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        loadConfig();

        // 请求存储权限（Android 6.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            }
        }

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveConfig();
                Toast.makeText(MainActivity.this, "配置已保存", Toast.LENGTH_SHORT).show();
            }
        });

        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String serverUrl = etServerUrl.getText().toString().trim();
                String username = etUsername.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                if (serverUrl.isEmpty()) {
                    Toast.makeText(MainActivity.this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
                    return;
                }
                progressBar.setVisibility(View.VISIBLE);
                tvStatus.setText("测试连接中...");
                new TestConnectionTask().execute(serverUrl, username, password);
            }
        });

        btnSync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String serverUrl = etServerUrl.getText().toString().trim();
                String username = etUsername.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                String localPath = etLocalPath.getText().toString().trim();
                if (serverUrl.isEmpty() || localPath.isEmpty()) {
                    Toast.makeText(MainActivity.this, "请填写服务器地址和本地路径", Toast.LENGTH_SHORT).show();
                    return;
                }
                File localDir = new File(localPath);
                if (!localDir.exists() || !localDir.isDirectory()) {
                    Toast.makeText(MainActivity.this, "本地路径无效或不是目录", Toast.LENGTH_SHORT).show();
                    return;
                }
                progressBar.setVisibility(View.VISIBLE);
                tvStatus.setText("同步中...");
                new SyncTask().execute(serverUrl, username, password, localPath);
            }
        });
    }

    private void loadConfig() {
        etServerUrl.setText(sharedPreferences.getString(KEY_SERVER, ""));
        etUsername.setText(sharedPreferences.getString(KEY_USERNAME, ""));
        etPassword.setText(sharedPreferences.getString(KEY_PASSWORD, ""));
        etLocalPath.setText(sharedPreferences.getString(KEY_LOCAL_PATH,
                Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download"));
    }

    private void saveConfig() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_SERVER, etServerUrl.getText().toString().trim());
        editor.putString(KEY_USERNAME, etUsername.getText().toString().trim());
        editor.putString(KEY_PASSWORD, etPassword.getText().toString().trim());
        editor.putString(KEY_LOCAL_PATH, etLocalPath.getText().toString().trim());
        editor.apply();
    }

    // 测试连接 AsyncTask
    private class TestConnectionTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {
            WebDAVClient client = new WebDAVClient(params[0], params[1], params[2]);
            return client.testConnection();
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressBar.setVisibility(View.GONE);
            if (success) {
                tvStatus.setText("连接成功！");
                Toast.makeText(MainActivity.this, "连接成功", Toast.LENGTH_SHORT).show();
            } else {
                tvStatus.setText("连接失败");
                Toast.makeText(MainActivity.this, "连接失败，请检查配置", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 同步 AsyncTask
    private class SyncTask extends AsyncTask<String, String, Boolean> {
        private int totalFiles = 0;
        private int uploaded = 0;
        private String statusMsg = "";

        @Override
        protected Boolean doInBackground(String... params) {
            String serverUrl = params[0];
            String username = params[1];
            String password = params[2];
            String localPath = params[3];

            WebDAVClient client = new WebDAVClient(serverUrl, username, password);
            // 先测试连接
            if (!client.testConnection()) {
                statusMsg = "连接失败";
                return false;
            }

            // 获取远程文件列表
            List<String> remoteFiles = client.listFiles();
            if (remoteFiles == null) {
                statusMsg = "获取远程文件列表失败";
                return false;
            }

            File localDir = new File(localPath);
            File[] files = localDir.listFiles();
            if (files == null || files.length == 0) {
                statusMsg = "本地目录为空或无文件";
                return false;
            }

            totalFiles = files.length;
            int successCount = 0;
            for (File file : files) {
                if (file.isFile()) {
                    // 检查是否已存在（增量同步，只上传不存在的）
                    if (!remoteFiles.contains(file.getName())) {
                        publishProgress("上传: " + file.getName());
                        boolean uploadedOk = client.uploadFile(file);
                        if (uploadedOk) {
                            successCount++;
                            uploaded++;
                        } else {
                            // 可以记录失败
                        }
                    } else {
                        publishProgress("跳过已存在: " + file.getName());
                    }
                }
            }
            statusMsg = "同步完成！成功上传 " + successCount + " 个文件，跳过 " + (totalFiles - successCount) + " 个已存在文件。";
            return true;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            tvStatus.setText(values[0]);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            progressBar.setVisibility(View.GONE);
            tvStatus.setText(statusMsg);
            Toast.makeText(MainActivity.this, statusMsg, Toast.LENGTH_LONG).show();
        }
    }
}

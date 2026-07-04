package com.example.webdavsync;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private EditText etServer, etUsername, etPassword;
    private Button btnTest, btnSave;
    private TextView tvStatus;
    private SharedPreferences prefs;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etServer = findViewById(R.id.et_server);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnTest = findViewById(R.id.btn_test_connection);
        btnSave = findViewById(R.id.btn_save_settings);
        tvStatus = findViewById(R.id.tv_settings_status);

        prefs = getSharedPreferences("webdav_prefs", MODE_PRIVATE);

        etServer.setText(prefs.getString("server_url", ""));
        etUsername.setText(prefs.getString("username", ""));
        etPassword.setText(prefs.getString("password", ""));

        btnTest.setOnClickListener(v -> testConnection());
        btnSave.setOnClickListener(v -> saveAndConnect());
    }

    private void testConnection() {
        String server = etServer.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (server.isEmpty()) {
            tvStatus.setText("请输入服务器地址");
            return;
        }

        tvStatus.setText("测试连接中...");
        btnTest.setEnabled(false);

        new Thread(() -> {
            WebDAVClient client = new WebDAVClient(server, username, password);
            boolean ok = client.testConnection();
            mainHandler.post(() -> {
                btnTest.setEnabled(true);
                if (ok) {
                    tvStatus.setText("✅ 连接成功！");
                    Toast.makeText(SettingsActivity.this, "连接成功", Toast.LENGTH_SHORT).show();
                } else {
                    tvStatus.setText("❌ 连接失败，请检查配置");
                    Toast.makeText(SettingsActivity.this, "连接失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    // ★ 保存配置并自动连接
    private void saveAndConnect() {
        String server = etServer.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (server.isEmpty()) {
            tvStatus.setText("请输入服务器地址");
            return;
        }

        // 先测试连接
        tvStatus.setText("测试连接并保存...");
        btnSave.setEnabled(false);

        new Thread(() -> {
            WebDAVClient client = new WebDAVClient(server, username, password);
            boolean ok = client.testConnection();
            mainHandler.post(() -> {
                btnSave.setEnabled(true);
                if (ok) {
                    // 保存配置
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("server_url", server);
                    editor.putString("username", username);
                    editor.putString("password", password);
                    editor.apply();

                    // ★ 保存当前连接信息，供 MainActivity 使用
                    editor.putString("current_server", server);
                    editor.putString("current_username", username);
                    editor.putString("current_password", password);
                    editor.apply();

                    // ★ 更新全局客户端
                    WebDAVClientHolder.setClient(client);

                    tvStatus.setText("✅ 配置已保存并连接成功");
                    Toast.makeText(SettingsActivity.this, "配置已保存并连接成功", Toast.LENGTH_LONG).show();

                    // 返回主界面
                    finish();
                } else {
                    tvStatus.setText("❌ 连接失败，无法保存");
                    Toast.makeText(SettingsActivity.this, "连接失败，请检查配置", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }
}

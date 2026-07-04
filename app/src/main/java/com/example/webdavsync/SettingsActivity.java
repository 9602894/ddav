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

        // 加载已保存的配置
        etServer.setText(prefs.getString("server_url", ""));
        etUsername.setText(prefs.getString("username", ""));
        etPassword.setText(prefs.getString("password", ""));

        btnTest.setOnClickListener(v -> testConnection());
        btnSave.setOnClickListener(v -> saveSettings());
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

    private void saveSettings() {
        String server = etServer.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (server.isEmpty()) {
            tvStatus.setText("请输入服务器地址");
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("server_url", server);
        editor.putString("username", username);
        editor.putString("password", password);
        editor.apply();

        tvStatus.setText("✅ 配置已保存");
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();

        // 更新全局客户端
        WebDAVClient client = new WebDAVClient(server, username, password);
        WebDAVClientHolder.setClient(client);

        finish();
    }
}

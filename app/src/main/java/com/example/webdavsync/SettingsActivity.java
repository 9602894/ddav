package com.example.webdavsync;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private EditText etConfigName, etServer, etUsername, etPassword;
    private Button btnTest, btnSave, btnDelete, btnConnect;
    private TextView tvStatus;
    private ListView lvConfigs;
    private SharedPreferences prefs;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private ArrayAdapter<String> configAdapter;
    private List<String> configNames = new ArrayList<>();
    private String selectedConfigName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etConfigName = findViewById(R.id.et_config_name);
        etServer = findViewById(R.id.et_server);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnTest = findViewById(R.id.btn_test_connection);
        btnSave = findViewById(R.id.btn_save_settings);
        btnDelete = findViewById(R.id.btn_delete_config);
        btnConnect = findViewById(R.id.btn_connect_config);
        tvStatus = findViewById(R.id.tv_settings_status);
        lvConfigs = findViewById(R.id.lv_configs);

        prefs = getSharedPreferences("webdav_prefs", MODE_PRIVATE);
        loadConfigList();

        lvConfigs.setOnItemClickListener((parent, view, position, id) -> {
            selectedConfigName = configNames.get(position);
            loadConfigToForm(selectedConfigName);
        });

        btnTest.setOnClickListener(v -> testConnection());
        btnSave.setOnClickListener(v -> saveConfig());
        btnDelete.setOnClickListener(v -> deleteConfig());
        btnConnect.setOnClickListener(v -> connectConfig());
    }

    private void loadConfigList() {
        configNames.clear();
        Set<String> keySet = prefs.getStringSet("config_keys", new HashSet<>());
        configNames.addAll(keySet);
        if (configAdapter == null) {
            configAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, configNames);
            lvConfigs.setAdapter(configAdapter);
            lvConfigs.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        } else {
            configAdapter.notifyDataSetChanged();
        }
    }

    private void loadConfigToForm(String key) {
        String server = prefs.getString("config_" + key + "_server", "");
        String username = prefs.getString("config_" + key + "_username", "");
        String password = prefs.getString("config_" + key + "_password", "");
        etConfigName.setText(key);
        etServer.setText(server);
        etUsername.setText(username);
        etPassword.setText(password);
        selectedConfigName = key;
    }

    private void saveConfig() {
        String name = etConfigName.getText().toString().trim();
        String server = etServer.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (name.isEmpty() || server.isEmpty()) {
            tvStatus.setText("配置名称和服务器地址不能为空");
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("config_" + name + "_server", server);
        editor.putString("config_" + name + "_username", username);
        editor.putString("config_" + name + "_password", password);
        Set<String> keySet = prefs.getStringSet("config_keys", new HashSet<>());
        keySet.add(name);
        editor.putStringSet("config_keys", keySet);
        editor.apply();

        tvStatus.setText("✅ 配置已保存: " + name);
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
        loadConfigList();
        selectedConfigName = name;
    }

    private void deleteConfig() {
        if (selectedConfigName == null) {
            tvStatus.setText("请先选择要删除的配置");
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("config_" + selectedConfigName + "_server");
        editor.remove("config_" + selectedConfigName + "_username");
        editor.remove("config_" + selectedConfigName + "_password");
        Set<String> keySet = prefs.getStringSet("config_keys", new HashSet<>());
        keySet.remove(selectedConfigName);
        editor.putStringSet("config_keys", keySet);
        editor.apply();

        tvStatus.setText("配置已删除");
        Toast.makeText(this, "配置已删除", Toast.LENGTH_SHORT).show();
        loadConfigList();
        selectedConfigName = null;
        etConfigName.setText("");
        etServer.setText("");
        etUsername.setText("");
        etPassword.setText("");
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

    private void connectConfig() {
        if (selectedConfigName == null) {
            tvStatus.setText("请先选择配置");
            return;
        }

        String server = prefs.getString("config_" + selectedConfigName + "_server", "");
        String username = prefs.getString("config_" + selectedConfigName + "_username", "");
        String password = prefs.getString("config_" + selectedConfigName + "_password", "");

        if (server.isEmpty()) {
            tvStatus.setText("配置不完整");
            return;
        }

        tvStatus.setText("正在连接...");
        new Thread(() -> {
            WebDAVClient client = new WebDAVClient(server, username, password);
            boolean ok = client.testConnection();
            mainHandler.post(() -> {
                if (ok) {
                    WebDAVClientHolder.setClient(client);
                    // 保存当前连接配置名
                    prefs.edit().putString("current_config", selectedConfigName).apply();
                    tvStatus.setText("✅ 已连接: " + selectedConfigName);
                    Toast.makeText(SettingsActivity.this, "已连接到 " + selectedConfigName, Toast.LENGTH_LONG).show();
                    finish(); // 返回主界面
                } else {
                    tvStatus.setText("❌ 连接失败");
                    Toast.makeText(SettingsActivity.this, "连接失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }
}

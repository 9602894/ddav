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
    private List<String> configNames = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private String selectedConfig = null;

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
            selectedConfig = configNames.get(position);
            loadConfigToForm(selectedConfig);
        });

        btnSave.setOnClickListener(v -> saveConfig());
        btnDelete.setOnClickListener(v -> deleteConfig());
        btnTest.setOnClickListener(v -> testConnection());
        btnConnect.setOnClickListener(v -> connectConfig());
    }

    private void loadConfigList() {
        configNames.clear();
        Set<String> keySet = prefs.getStringSet("config_keys", new HashSet<>());
        configNames.addAll(keySet);
        if (adapter == null) {
            adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, configNames);
            lvConfigs.setAdapter(adapter);
            lvConfigs.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    private void loadConfigToForm(String name) {
        String server = prefs.getString("conn_" + name + "_server", "");
        String user = prefs.getString("conn_" + name + "_username", "");
        String pass = prefs.getString("conn_" + name + "_password", "");
        etConfigName.setText(name);
        etServer.setText(server);
        etUsername.setText(user);
        etPassword.setText(pass);
    }

    private void saveConfig() {
        String name = etConfigName.getText().toString().trim();
        String server = etServer.getText().toString().trim();
        String user = etUsername.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        if (name.isEmpty() || server.isEmpty()) {
            tvStatus.setText("名称和服务器地址不能为空");
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("conn_" + name + "_server", server);
        editor.putString("conn_" + name + "_username", user);
        editor.putString("conn_" + name + "_password", pass);
        Set<String> keySet = prefs.getStringSet("config_keys", new HashSet<>());
        keySet.add(name);
        editor.putStringSet("config_keys", keySet);
        editor.apply();

        tvStatus.setText("配置已保存");
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
        loadConfigList();
        selectedConfig = name;
    }

    private void deleteConfig() {
        if (selectedConfig == null) {
            tvStatus.setText("请先选择一个配置");
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("conn_" + selectedConfig + "_server");
        editor.remove("conn_" + selectedConfig + "_username");
        editor.remove("conn_" + selectedConfig + "_password");
        Set<String> keySet = prefs.getStringSet("config_keys", new HashSet<>());
        keySet.remove(selectedConfig);
        editor.putStringSet("config_keys", keySet);
        // 如果当前连接的是这个配置，清除当前连接
        String current = prefs.getString("current_connection", "");
        if (current.equals(selectedConfig)) {
            editor.remove("current_connection");
        }
        editor.apply();

        tvStatus.setText("配置已删除");
        Toast.makeText(this, "配置已删除", Toast.LENGTH_SHORT).show();
        loadConfigList();
        selectedConfig = null;
        etConfigName.setText("");
        etServer.setText("");
        etUsername.setText("");
        etPassword.setText("");
    }

    private void testConnection() {
        String server = etServer.getText().toString().trim();
        String user = etUsername.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        if (server.isEmpty()) {
            tvStatus.setText("请输入服务器地址");
            return;
        }

        tvStatus.setText("测试中...");
        btnTest.setEnabled(false);

        new Thread(() -> {
            WebDAVClient client = new WebDAVClient(server, user, pass);
            boolean ok = client.testConnection();
            mainHandler.post(() -> {
                btnTest.setEnabled(true);
                if (ok) {
                    tvStatus.setText("✅ 连接成功");
                    Toast.makeText(SettingsActivity.this, "连接成功", Toast.LENGTH_SHORT).show();
                } else {
                    tvStatus.setText("❌ 连接失败");
                    Toast.makeText(SettingsActivity.this, "连接失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void connectConfig() {
        if (selectedConfig == null) {
            tvStatus.setText("请先选择配置");
            return;
        }
        String server = prefs.getString("conn_" + selectedConfig + "_server", "");
        String user = prefs.getString("conn_" + selectedConfig + "_username", "");
        String pass = prefs.getString("conn_" + selectedConfig + "_password", "");

        if (server.isEmpty()) {
            tvStatus.setText("配置不完整");
            return;
        }

        tvStatus.setText("连接中...");
        new Thread(() -> {
            WebDAVClient client = new WebDAVClient(server, user, pass);
            boolean ok = client.testConnection();
            mainHandler.post(() -> {
                if (ok) {
                    // 保存为当前连接
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("current_connection", selectedConfig);
                    editor.apply();
                    WebDAVClientHolder.setClient(client);
                    tvStatus.setText("✅ 已连接到 " + selectedConfig);
                    Toast.makeText(SettingsActivity.this, "连接成功", Toast.LENGTH_SHORT).show();
                    finish(); // 返回主界面
                } else {
                    tvStatus.setText("❌ 连接失败");
                    Toast.makeText(SettingsActivity.this, "连接失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }
}

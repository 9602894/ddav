package com.example.webdavsync;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "webdav_configs";
    private static final String KEY_CONFIG_NAMES = "config_names";

    private EditText etConfigName, etServer, etUsername, etPassword;
    private Button btnTest, btnSave, btnDelete, btnSwitch;
    private TextView tvStatus;
    private ImageView ivBack;
    private ListView lvConfigs;

    private SharedPreferences prefs;
    private List<String> configNames = new ArrayList<>();
    private ArrayAdapter<String> configAdapter;
    private String selectedConfigName = null;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadConfigNames();

        ivBack.setOnClickListener(v -> finish());

        lvConfigs.setOnItemClickListener((parent, view, position, id) -> {
            selectedConfigName = configNames.get(position);
            loadConfigToForm(selectedConfigName);
            lvConfigs.setItemChecked(position, true);
        });

        btnSwitch.setOnClickListener(v -> switchToSelected());

        btnTest.setOnClickListener(v -> testConnection());

        btnSave.setOnClickListener(v -> saveConfig());

        btnDelete.setOnClickListener(v -> deleteConfig());
    }

    private void initViews() {
        etConfigName = findViewById(R.id.et_config_name);
        etServer = findViewById(R.id.et_server);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnTest = findViewById(R.id.btn_test_connection);
        btnSave = findViewById(R.id.btn_save_settings);
        btnDelete = findViewById(R.id.btn_delete_config);
        btnSwitch = findViewById(R.id.btn_switch_config);
        tvStatus = findViewById(R.id.tv_settings_status);
        ivBack = findViewById(R.id.iv_back);
        lvConfigs = findViewById(R.id.lv_configs);
    }

    private void loadConfigNames() {
        configNames.clear();
        Set<String> nameSet = prefs.getStringSet(KEY_CONFIG_NAMES, new HashSet<>());
        configNames.addAll(nameSet);
        if (configAdapter == null) {
            configAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, configNames);
            lvConfigs.setAdapter(configAdapter);
            lvConfigs.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        } else {
            configAdapter.notifyDataSetChanged();
        }
        // 清除选中状态
        lvConfigs.clearChoices();
        selectedConfigName = null;
    }

    private void loadConfigToForm(String name) {
        String server = prefs.getString("config_" + name + "_server", "");
        String user = prefs.getString("config_" + name + "_username", "");
        String pass = prefs.getString("config_" + name + "_password", "");
        etConfigName.setText(name);
        etServer.setText(server);
        etUsername.setText(user);
        etPassword.setText(pass);
        tvStatus.setText("已加载配置: " + name);
    }

    private void clearForm() {
        etConfigName.setText("");
        etServer.setText("");
        etUsername.setText("");
        etPassword.setText("");
        selectedConfigName = null;
        lvConfigs.clearChoices();
        tvStatus.setText("");
    }

    private void testConnection() {
        String server = etServer.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (server.isEmpty()) {
            tvStatus.setText("请填写服务器地址");
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

    private void saveConfig() {
        String name = etConfigName.getText().toString().trim();
        String server = etServer.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (name.isEmpty() || server.isEmpty()) {
            tvStatus.setText("配置名称和服务器地址不能为空");
            return;
        }

        // 先测试连接
        tvStatus.setText("测试连接...");
        btnSave.setEnabled(false);

        new Thread(() -> {
            WebDAVClient client = new WebDAVClient(server, username, password);
            boolean ok = client.testConnection();
            mainHandler.post(() -> {
                btnSave.setEnabled(true);
                if (!ok) {
                    tvStatus.setText("❌ 连接失败，配置未保存");
                    Toast.makeText(SettingsActivity.this, "连接失败，配置未保存", Toast.LENGTH_LONG).show();
                    return;
                }

                // 保存配置
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("config_" + name + "_server", server);
                editor.putString("config_" + name + "_username", username);
                editor.putString("config_" + name + "_password", password);

                Set<String> nameSet = prefs.getStringSet(KEY_CONFIG_NAMES, new HashSet<>());
                nameSet.add(name);
                editor.putStringSet(KEY_CONFIG_NAMES, nameSet);
                editor.apply();

                loadConfigNames();
                tvStatus.setText("✅ 配置已保存: " + name);
                Toast.makeText(SettingsActivity.this, "配置已保存", Toast.LENGTH_SHORT).show();
                clearForm();
            });
        }).start();
    }

    private void deleteConfig() {
        if (selectedConfigName == null) {
            tvStatus.setText("请先选择一个配置");
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("config_" + selectedConfigName + "_server");
        editor.remove("config_" + selectedConfigName + "_username");
        editor.remove("config_" + selectedConfigName + "_password");

        Set<String> nameSet = prefs.getStringSet(KEY_CONFIG_NAMES, new HashSet<>());
        nameSet.remove(selectedConfigName);
        editor.putStringSet(KEY_CONFIG_NAMES, nameSet);
        editor.apply();

        loadConfigNames();
        clearForm();
        tvStatus.setText("已删除配置: " + selectedConfigName);
        Toast.makeText(this, "配置已删除", Toast.LENGTH_SHORT).show();
    }

    private void switchToSelected() {
        if (selectedConfigName == null) {
            tvStatus.setText("请先选择一个配置");
            return;
        }

        String server = prefs.getString("config_" + selectedConfigName + "_server", "");
        String user = prefs.getString("config_" + selectedConfigName + "_username", "");
        String pass = prefs.getString("config_" + selectedConfigName + "_password", "");

        if (server.isEmpty()) {
            tvStatus.setText("配置不完整，请重新保存");
            return;
        }

        tvStatus.setText("切换到 " + selectedConfigName + "...");
        btnSwitch.setEnabled(false);

        new Thread(() -> {
            WebDAVClient client = new WebDAVClient(server, user, pass);
            boolean ok = client.testConnection();
            mainHandler.post(() -> {
                btnSwitch.setEnabled(true);
                if (ok) {
                    // 保存到全局单例
                    WebDAVClientHolder.setClient(client);
                    // 保存当前连接配置名称
                    getSharedPreferences("webdav_prefs", MODE_PRIVATE)
                            .edit()
                            .putString("current_config", selectedConfigName)
                            .apply();
                    tvStatus.setText("✅ 已切换到: " + selectedConfigName);
                    Toast.makeText(SettingsActivity.this, "已切换到: " + selectedConfigName, Toast.LENGTH_SHORT).show();
                    // 返回主界面刷新
                    setResult(RESULT_OK);
                    finish();
                } else {
                    tvStatus.setText("❌ 连接失败，无法切换");
                    Toast.makeText(SettingsActivity.this, "连接失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConfigNames();
    }
}

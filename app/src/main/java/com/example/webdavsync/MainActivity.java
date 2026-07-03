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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "webdav_configs";
    private static final String KEY_CONFIG_NAMES = "config_names";

    private EditText etConfigName, etServerUrl, etUsername, etPassword;
    private Button btnSaveConfig, btnDeleteConfig, btnConnect, btnCloudBrowse, btnLocalBrowse;
    private ListView lvConfigs;
    private TextView tvConnectionStatus;

    private ArrayAdapter<String> configAdapter;
    private List<String> configNames = new ArrayList<>();
    private SharedPreferences prefs;
    private String selectedConfigName = null;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadConfigNames();
        setupListeners();
        updateConnectionStatus();
        // 检查是否已有连接
        if (WebDAVClientHolder.getClient() != null) {
            updateConnectionStatus();
        }
    }

    private void initViews() {
        etConfigName = findViewById(R.id.et_config_name);
        etServerUrl = findViewById(R.id.et_server_url);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnSaveConfig = findViewById(R.id.btn_save_config);
        btnDeleteConfig = findViewById(R.id.btn_delete_config);
        btnConnect = findViewById(R.id.btn_connect);
        btnCloudBrowse = findViewById(R.id.btn_cloud_browse);
        btnLocalBrowse = findViewById(R.id.btn_local_browse);
        lvConfigs = findViewById(R.id.lv_configs);
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
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
    }

    private void setupListeners() {
        btnSaveConfig.setOnClickListener(v -> saveConfig());

        btnDeleteConfig.setOnClickListener(v -> deleteConfig());

        lvConfigs.setOnItemClickListener((parent, view, position, id) -> {
            selectedConfigName = configNames.get(position);
            loadConfigToForm(selectedConfigName);
        });

        btnConnect.setOnClickListener(v -> connectToSelected());

        btnCloudBrowse.setOnClickListener(v -> {
            if (WebDAVClientHolder.getClient() == null) {
                Toast.makeText(this, "请先连接", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, CloudBrowseActivity.class));
        });

        btnLocalBrowse.setOnClickListener(v -> {
            if (WebDAVClientHolder.getClient() == null) {
                Toast.makeText(this, "请先连接", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, LocalBrowseActivity.class));
        });
    }

    private void saveConfig() {
        String name = etConfigName.getText().toString().trim();
        String server = etServerUrl.getText().toString().trim();
        String user = etUsername.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        if (name.isEmpty() || server.isEmpty()) {
            Toast.makeText(this, "配置名称和服务器地址不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        // 先测试连接
        Toast.makeText(this, "正在测试连接...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            WebDAVClient testClient = new WebDAVClient(server, user, pass);
            boolean success = testClient.testConnection();
            mainHandler.post(() -> {
                if (success) {
                    // 保存配置
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("config_" + name + "_server", server);
                    editor.putString("config_" + name + "_username", user);
                    editor.putString("config_" + name + "_password", pass);

                    Set<String> nameSet = prefs.getStringSet(KEY_CONFIG_NAMES, new HashSet<>());
                    nameSet.add(name);
                    editor.putStringSet(KEY_CONFIG_NAMES, nameSet);
                    editor.apply();

                    loadConfigNames();
                    Toast.makeText(MainActivity.this, "配置已保存: " + name, Toast.LENGTH_SHORT).show();
                    clearForm();
                } else {
                    Toast.makeText(MainActivity.this, "连接测试失败，配置未保存", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void deleteConfig() {
        if (selectedConfigName == null) {
            Toast.makeText(this, "请先选择要删除的配置", Toast.LENGTH_SHORT).show();
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

        // 如果当前连接的是这个配置，断开
        if (WebDAVClientHolder.getClient() != null && selectedConfigName.equals(WebDAVClientHolder.getConfigName())) {
            WebDAVClientHolder.clear();
            updateConnectionStatus();
        }

        loadConfigNames();
        selectedConfigName = null;
        clearForm();
        Toast.makeText(this, "配置已删除", Toast.LENGTH_SHORT).show();
    }

    private void loadConfigToForm(String name) {
        String server = prefs.getString("config_" + name + "_server", "");
        String user = prefs.getString("config_" + name + "_username", "");
        String pass = prefs.getString("config_" + name + "_password", "");

        etConfigName.setText(name);
        etServerUrl.setText(server);
        etUsername.setText(user);
        etPassword.setText(pass);
    }

    private void clearForm() {
        etConfigName.setText("");
        etServerUrl.setText("");
        etUsername.setText("");
        etPassword.setText("");
    }

    private void connectToSelected() {
        if (selectedConfigName == null) {
            Toast.makeText(this, "请先选择配置", Toast.LENGTH_SHORT).show();
            return;
        }

        String server = prefs.getString("config_" + selectedConfigName + "_server", "");
        String user = prefs.getString("config_" + selectedConfigName + "_username", "");
        String pass = prefs.getString("config_" + selectedConfigName + "_password", "");

        if (server.isEmpty()) {
            Toast.makeText(this, "配置不完整，请重新保存", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "正在连接...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            WebDAVClient client = new WebDAVClient(server, user, pass);
            boolean success = client.testConnection();

            mainHandler.post(() -> {
                if (success) {
                    WebDAVClientHolder.setClient(client, selectedConfigName);
                    updateConnectionStatus();
                    Toast.makeText(MainActivity.this, "连接成功: " + selectedConfigName, Toast.LENGTH_LONG).show();
                } else {
                    WebDAVClientHolder.clear();
                    updateConnectionStatus();
                    Toast.makeText(MainActivity.this, "连接失败，请检查配置", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void updateConnectionStatus() {
        if (WebDAVClientHolder.getClient() != null) {
            String name = WebDAVClientHolder.getConfigName();
            tvConnectionStatus.setText("✅ 已连接: " + name);
            btnCloudBrowse.setEnabled(true);
            btnLocalBrowse.setEnabled(true);
        } else {
            tvConnectionStatus.setText("❌ 未连接");
            btnCloudBrowse.setEnabled(false);
            btnLocalBrowse.setEnabled(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateConnectionStatus();
    }
}

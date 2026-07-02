package com.example.webdavsync;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText etServerUrl, etUsername, etPassword;
    private Button btnTest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etServerUrl = findViewById(R.id.et_server_url);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnTest = findViewById(R.id.btn_test);

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

                // 在后台线程执行网络操作
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        WebDAVClient client = new WebDAVClient(serverUrl, username, password);
                        boolean success = client.testConnection();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (success) {
                                    Toast.makeText(MainActivity.this, "连接成功！", Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(MainActivity.this, "连接失败，请检查配置", Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }
                }).start();
            }
        });
    }
}

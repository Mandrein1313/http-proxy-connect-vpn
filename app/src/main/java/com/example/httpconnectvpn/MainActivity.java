package com.example.httpconnectvpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int VPN_REQUEST = 1001;
    public static final String ACTION_LOG = "com.example.httpconnectvpn.ADD_LOG";

    private LinearLayout btnConnect;
    private ImageView powerIcon;
    private TextView connectText, statusText, proxyText, logText;
    private ScrollView layoutMainContainer, layoutLogContainer;
    private TabLayout tabLayout;
    private EditText hostInput, portInput;
    private BottomNavigationView bottomNavigationView;
    private MaterialToolbar toolbar;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    private boolean connected = false;
    private boolean isReceiverRegistered = false;
    private SharedPreferences prefs;

    // โหมดการเชื่อมต่อปัจจุบัน ("http" หรือ "ssh")
    private String currentMode = "http";

    // Launcher สำหรับ Export Config
    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            this::exportConfigFile
    );

    // Launcher สำหรับ Import Config
    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            this::importConfigFile
    );

    // Receiver รับสถานะ VPN และ Log
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (i == null || i.getAction() == null) return;

            if (ProxyVpnService.ACTION_STATE.equals(i.getAction())) {
                connected = i.getBooleanExtra("connected", false);
                updateUi();
            } else if (ACTION_LOG.equals(i.getAction()) || ProxyVpnService.ACTION_LOG.equals(i.getAction())) {
                String message = i.getStringExtra("message");
                appendLog(message);
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("VpnPrefs", Context.MODE_PRIVATE);
        currentMode = prefs.getString("connection_mode", "http");

        // Binding Views
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_view);
        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tabLayout);
        layoutMainContainer = findViewById(R.id.layoutMainContainer);
        layoutLogContainer = findViewById(R.id.layoutLogContainer);
        btnConnect = findViewById(R.id.btnConnect);
        powerIcon = findViewById(R.id.powerIcon);
        connectText = findViewById(R.id.connectText);
        statusText = findViewById(R.id.statusText);
        proxyText = findViewById(R.id.proxyText);
        logText = findViewById(R.id.logText);
        hostInput = findViewById(R.id.hostInput);
        portInput = findViewById(R.id.portInput);
        bottomNavigationView = findViewById(R.id.bottomNavigation);

        // โหลดค่าล่าสุดตามโหมด
        loadLastConfig();

        // สลับแท็บ Main / Log
        if (tabLayout != null) {
            tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    if (tab.getPosition() == 0) {
                        if (layoutMainContainer != null) layoutMainContainer.setVisibility(View.VISIBLE);
                        if (layoutLogContainer != null) layoutLogContainer.setVisibility(View.GONE);
                    } else {
                        if (layoutMainContainer != null) layoutMainContainer.setVisibility(View.GONE);
                        if (layoutLogContainer != null) layoutLogContainer.setVisibility(View.VISIBLE);
                    }
                }
                @Override public void onTabUnselected(TabLayout.Tab tab) {}
                @Override public void onTabReselected(TabLayout.Tab tab) {}
            });
        }

        // Drawer
        if (toolbar != null && drawerLayout != null) {
            toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_settings) {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                } else if (id == R.id.nav_home) {
                    if (tabLayout != null && tabLayout.getTabAt(0) != null) {
                        tabLayout.getTabAt(0).select();
                    }
                }
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
                return true;
            });
        }

        // Bottom Navigation
        if (bottomNavigationView != null) {
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    if (tabLayout != null && tabLayout.getTabAt(0) != null) {
                        tabLayout.getTabAt(0).select();
                    }
                    return true;
                } else if (id == R.id.nav_export) {
                    exportLauncher.launch("aetherlink_config.json");
                    return true;
                } else if (id == R.id.nav_import) {
                    importLauncher.launch(new String[]{"*/*"});
                    return true;
                } else if (id == R.id.nav_logs) {
                    if (tabLayout != null && tabLayout.getTabAt(1) != null) {
                        tabLayout.getTabAt(1).select();
                    }
                    return true;
                }
                return false;
            });
        }

        if (btnConnect != null) {
            btnConnect.setOnClickListener(v -> toggleVpn());
        }

        updateUi();
    }

    private void loadLastConfig() {
        if ("ssh".equals(currentMode)) {
            if (hostInput != null) hostInput.setText(prefs.getString("ssh_host", "jp6.vpnjantit.com"));
            if (portInput != null) portInput.setText(String.valueOf(prefs.getInt("ssh_port", 22)));
        } else {
            if (hostInput != null) hostInput.setText(prefs.getString("proxy_host", "proxy.internal.example"));
            if (portInput != null) portInput.setText(String.valueOf(prefs.getInt("proxy_port", 8080)));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerLogReceiver();
        // โหลดโหมดล่าสุดจาก Settings (ถ้ามี)
        currentMode = prefs.getString("connection_mode", currentMode);
        updateUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterLogReceiver();
    }

    private void registerLogReceiver() {
        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ProxyVpnService.ACTION_STATE);
            filter.addAction(ACTION_LOG);
            filter.addAction(ProxyVpnService.ACTION_LOG);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(receiver, filter);
            }
            isReceiverRegistered = true;
        }
    }

    private void unregisterLogReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(receiver);
            } catch (Exception ignored) {}
            isReceiverRegistered = false;
        }
    }

    public void appendLog(String message) {
        if (logText != null && message != null) {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            logText.append("\n[" + time + "] " + message);
            if (layoutLogContainer != null) {
                layoutLogContainer.post(() -> layoutLogContainer.fullScroll(ScrollView.FOCUS_DOWN));
            }
        }
    }

    // ======================== Export / Import ========================
    private void exportConfigFile(Uri uri) {
        if (uri == null) return;
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            JSONObject json = new JSONObject();

            json.put("mode", currentMode);
            json.put("host", hostInput != null ? hostInput.getText().toString().trim() : "");
            json.put("port", portInput != null ? Integer.parseInt(portInput.getText().toString().trim()) : 8080);

            // SSH fields
            json.put("ssh_host", prefs.getString("ssh_host", ""));
            json.put("ssh_port", prefs.getInt("ssh_port", 22));
            json.put("ssh_user", prefs.getString("ssh_user", ""));
            json.put("ssh_pass", prefs.getString("ssh_pass", ""));
            json.put("payload", prefs.getString("payload", ""));
            json.put("sni", prefs.getString("sni", ""));
            json.put("dns1", prefs.getString("dns1", "8.8.8.8"));
            json.put("dns2", prefs.getString("dns2", "1.1.1.1"));

            if (os != null) {
                os.write(json.toString(4).getBytes());
                appendLog("✅ ส่งออกไฟล์ Config สำเร็จ");
                Toast.makeText(this, "บันทึก Config สำเร็จ", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            appendLog("❌ ส่งออกไฟล์ล้มเหลว: " + e.getMessage());
        }
    }

    private void importConfigFile(Uri uri) {
        if (uri == null) return;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return;

            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            String jsonStr = new String(bytes);

            JSONObject json = new JSONObject(jsonStr);

            currentMode = json.optString("mode", "http");
            String host = json.optString("host", "");
            int port = json.optInt("port", 8080);

            if (hostInput != null) hostInput.setText(host);
            if (portInput != null) portInput.setText(String.valueOf(port));

            prefs.edit()
                    .putString("connection_mode", currentMode)
                    .putString("proxy_host", host)
                    .putInt("proxy_port", port)
                    .putString("ssh_host", json.optString("ssh_host", host))
                    .putInt("ssh_port", json.optInt("ssh_port", 22))
                    .putString("ssh_user", json.optString("ssh_user", ""))
                    .putString("ssh_pass", json.optString("ssh_pass", ""))
                    .putString("payload", json.optString("payload", ""))
                    .putString("sni", json.optString("sni", ""))
                    .putString("dns1", json.optString("dns1", "8.8.8.8"))
                    .putString("dns2", json.optString("dns2", "1.1.1.1"))
                    .apply();

            appendLog("✅ นำเข้า Config สำเร็จ (โหมด: " + currentMode + ")");
            Toast.makeText(this, "นำเข้า Config สำเร็จ", Toast.LENGTH_SHORT).show();
            updateUi();
        } catch (Exception e) {
            appendLog("❌ รูปแบบไฟล์ Config ไม่ถูกต้อง");
        }
    }

    // ======================== เชื่อมต่อ / ตัดการเชื่อมต่อ ========================
    private void toggleVpn() {
        if (connected) {
            appendLog("กำลังหยุดการทำงาน...");
            Intent stopIntent = new Intent(this, ProxyVpnService.class);
            stopIntent.setAction("STOP");
            startService(stopIntent);
            connected = false;
            updateUi();
            return;
        }

        // บันทึกค่าปัจจุบัน
        String host = hostInput != null ? hostInput.getText().toString().trim() : "";
        int port = 22;
        try {
            if (portInput != null) {
                port = Integer.parseInt(portInput.getText().toString().trim());
            }
        } catch (Exception ignored) {}

        if (host.isEmpty()) {
            Toast.makeText(this, "กรุณากรอก Host", Toast.LENGTH_SHORT).show();
            return;
        }

        // บันทึกลง prefs
        if ("ssh".equals(currentMode)) {
            prefs.edit()
                    .putString("ssh_host", host)
                    .putInt("ssh_port", port)
                    .apply();
        } else {
            prefs.edit()
                    .putString("proxy_host", host)
                    .putInt("proxy_port", port)
                    .apply();
        }

        appendLog("กำลังเตรียมการเชื่อมต่อ (" + currentMode.toUpperCase() + ") → " + host + ":" + port);

        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            startActivityForResult(prepare, VPN_REQUEST);
        } else {
            startVpnService(host, port);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST) {
            if (resultCode == RESULT_OK) {
                String host = hostInput != null ? hostInput.getText().toString().trim() : "";
                int port = 22;
                try {
                    if (portInput != null) port = Integer.parseInt(portInput.getText().toString().trim());
                } catch (Exception ignored) {}
                startVpnService(host, port);
            } else {
                appendLog("ผู้ใช้ปฏิเสธการขออนุญาต VPN");
            }
        }
    }

    private void startVpnService(String host, int port) {
        Intent intent = new Intent(this, ProxyVpnService.class);

        if ("ssh".equals(currentMode)) {
            // โหมด SSH (vpnjantit)
            intent.putExtra("mode", "ssh");
            intent.putExtra("host", host);
            intent.putExtra("port", port);
            intent.putExtra("username", prefs.getString("ssh_user", ""));
            intent.putExtra("password", prefs.getString("ssh_pass", ""));
            intent.putExtra("payload", prefs.getString("payload", ""));
        } else {
            // โหมด HTTP เดิม
            intent.putExtra("mode", "http");
            intent.putExtra("host", host);
            intent.putExtra("port", port);
        }

        ContextCompat.startForegroundService(this, intent);
        connected = true;
        appendLog("ส่งคำสั่งเชื่อมต่อ Service แล้ว");
        updateUi();
    }

    private void updateUi() {
        if (connected) {
            if (connectText != null) connectText.setText("ตัดการเชื่อมต่อ");
            if (statusText != null) statusText.setText("เชื่อมต่อสำเร็จ");
            if (powerIcon != null) powerIcon.setColorFilter(Color.parseColor("#4ADE80"));
        } else {
            if (connectText != null) connectText.setText("เชื่อมต่อ");
            if (statusText != null) statusText.setText("ยังไม่เชื่อมต่อ");
            if (powerIcon != null) powerIcon.clearColorFilter();
        }

        if (proxyText != null && hostInput != null && portInput != null) {
            String modeLabel = "ssh".equals(currentMode) ? "SSH" : "HTTP";
            proxyText.setText(hostInput.getText().toString() + ":" + portInput.getText().toString() + " · " + modeLabel);
        }
    }

    @Override
    protected void onDestroy() {
        unregisterLogReceiver();
        super.onDestroy();
    }
}
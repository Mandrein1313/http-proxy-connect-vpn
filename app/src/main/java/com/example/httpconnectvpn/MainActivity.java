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

    // Launcher สำหรับ Export Config (JSON File)
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
            } else if (ACTION_LOG.equals(i.getAction())) {
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

        // แสดงค่า Proxy ล่าสุด
        if (hostInput != null) hostInput.setText(prefs.getString("proxy_host", "proxy.internal.example"));
        if (portInput != null) portInput.setText(String.valueOf(prefs.getInt("proxy_port", 8080)));

        // สลับแท็บ หน้าหลัก (Main) / ล็อก (Log)
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

        // Drawer Menu Button
        if (toolbar != null && drawerLayout != null) {
            toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        // จัดการคลิก Navigation Drawer
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

        // จัดการคลิก Bottom Navigation Bar
        if (bottomNavigationView != null) {
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    if (tabLayout != null && tabLayout.getTabAt(0) != null) {
                        tabLayout.getTabAt(0).select();
                    }
                    return true;
                } else if (id == R.id.nav_export) {
                    exportLauncher.launch("vpn_config.config");
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

        if (btnConnect != null) btnConnect.setOnClickListener(v -> toggleVpn());
        updateUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerLogReceiver();
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

    // ฟังก์ชันส่งออกไฟล์ คอนฟิก (รวมทั้ง HTTP Proxy และ SSH)
    private void exportConfigFile(Uri uri) {
        if (uri == null) return;
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            JSONObject json = new JSONObject();
            json.put("host", hostInput != null ? hostInput.getText().toString().trim() : "");
            json.put("port", portInput != null ? Integer.parseInt(portInput.getText().toString().trim()) : 8080);
            json.put("ssh_host", prefs.getString("ssh_host", ""));
            json.put("ssh_port", prefs.getInt("ssh_port", 22));
            json.put("ssh_user", prefs.getString("ssh_user", ""));
            json.put("ssh_pass", prefs.getString("ssh_pass", ""));
            json.put("payload", prefs.getString("payload", ""));
            json.put("sni", prefs.getString("sni", ""));
            json.put("dns1", prefs.getString("dns1", "8.8.8.8"));
            json.put("dns2", prefs.getString("dns2", "8.8.4.4"));

            if (os != null) {
                os.write(json.toString(4).getBytes());
                appendLog("ส่งออกไฟล์ คอนฟิก สำเร็จ!");
                Toast.makeText(this, "บันทึกไฟล์คอนฟิกสำเร็จ", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            appendLog("เกิดข้อผิดพลาดในการส่งออกไฟล์");
        }
    }

    // ฟังก์ชันนำเข้าไฟล์ คอนฟิก
    private void importConfigFile(Uri uri) {
        if (uri == null) return;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return;
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            String jsonStr = new String(bytes);

            JSONObject json = new JSONObject(jsonStr);
            String host = json.optString("host", "");
            int port = json.optInt("port", 8080);

            if (hostInput != null) hostInput.setText(host);
            if (portInput != null) portInput.setText(String.valueOf(port));

            prefs.edit()
                    .putString("proxy_host", host)
                    .putInt("proxy_port", port)
                    .putString("ssh_host", json.optString("ssh_host", ""))
                    .putInt("ssh_port", json.optInt("ssh_port", 22))
                    .putString("ssh_user", json.optString("ssh_user", ""))
                    .putString("ssh_pass", json.optString("ssh_pass", ""))
                    .putString("payload", json.optString("payload", ""))
                    .putString("sni", json.optString("sni", ""))
                    .putString("dns1", json.optString("dns1", "8.8.8.8"))
                    .putString("dns2", json.optString("dns2", "8.8.4.4"))
                    .apply();

            appendLog("นำเข้าไฟล์ คอนฟิก สำเร็จ!");
            Toast.makeText(this, "นำเข้าค่าตั้งค่าสำเร็จ", Toast.LENGTH_SHORT).show();
            updateUi();
        } catch (Exception e) {
            appendLog("รูปแบบไฟล์ คอนฟิก ไม่ถูกต้อง");
        }
    }

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

        String host = hostInput != null ? hostInput.getText().toString().trim() : "";
        int port = 8080;
        try {
            if (portInput != null) port = Integer.parseInt(portInput.getText().toString().trim());
        } catch (Exception ignored) {}

        appendLog("กำลังเตรียมการเชื่อมต่อ VPN ไปยัง " + host + ":" + port);
        prefs.edit().putString("proxy_host", host).putInt("proxy_port", port).apply();

        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            startActivityForResult(prepare, VPN_REQUEST);
        } else {
            startVpn(host, port);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST) {
            if (resultCode == RESULT_OK) {
                String host = hostInput != null ? hostInput.getText().toString().trim() : "";
                int port = 8080;
                try {
                    if (portInput != null) port = Integer.parseInt(portInput.getText().toString().trim());
                } catch (Exception ignored) {}
                startVpn(host, port);
            } else {
                appendLog("ผู้ใช้ปฏิเสธการขออนุญาต VPN");
            }
        }
    }

    private void startVpn(String host, int port) {
        Intent i = new Intent(this, ProxyVpnService.class)
                .putExtra("host", host)
                .putExtra("port", port);
        ContextCompat.startForegroundService(this, i);
        connected = true;
        appendLog("ส่งคำสั่งเชื่อมต่อ Service เรียบร้อย");
        updateUi();
    }

    private void updateUi() {
        if (connected) {
            if (connectText != null) connectText.setText("ตัดเชื่อมต่อ");
            if (statusText != null) statusText.setText("เชื่อมต่อสำเร็จ");
            if (powerIcon != null) powerIcon.setColorFilter(Color.parseColor("#4ADE80"));
        } else {
            if (connectText != null) connectText.setText("เชื่อมต่อ");
            if (statusText != null) statusText.setText("ยังไม่เชื่อม");
            if (powerIcon != null) powerIcon.clearColorFilter();
        }

        if (proxyText != null && hostInput != null && portInput != null) {
            proxyText.setText(hostInput.getText().toString() + ":" + portInput.getText().toString() + " · HTTP");
        }
    }

    @Override
    protected void onDestroy() {
        unregisterLogReceiver();
        super.onDestroy();
    }
}

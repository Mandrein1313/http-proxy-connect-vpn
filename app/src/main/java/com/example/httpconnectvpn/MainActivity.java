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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

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
    private ScrollView logScrollView;
    private EditText hostInput, portInput;
    private BottomNavigationView bottomNavigationView;
    private MaterialToolbar toolbar;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private boolean connected = false;
    private SharedPreferences prefs;

    // ระบบเลือกไฟล์สำหรับการ Export / Import
    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            this::exportConfigFile
    );

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            this::importConfigFile
    );

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
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

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_view);
        toolbar = findViewById(R.id.toolbar);
        btnConnect = findViewById(R.id.btnConnect);
        powerIcon = findViewById(R.id.powerIcon);
        connectText = findViewById(R.id.connectText);
        statusText = findViewById(R.id.statusText);
        proxyText = findViewById(R.id.proxyText);
        logText = findViewById(R.id.logText);
        logScrollView = findViewById(R.id.logScrollView);
        hostInput = findViewById(R.id.hostInput);
        portInput = findViewById(R.id.portInput);
        bottomNavigationView = findViewById(R.id.bottomNavigation);

        hostInput.setText(prefs.getString("proxy_host", "proxy.internal.example"));
        portInput.setText(String.valueOf(prefs.getInt("proxy_port", 8080)));

        if (toolbar != null && drawerLayout != null) {
            toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        if (bottomNavigationView != null) {
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    return true;
                } else if (id == R.id.nav_export) {
                    exportLauncher.launch("vpn_config.config");
                    return true;
                } else if (id == R.id.nav_import) {
                    importLauncher.launch(new String[]{"*/*"});
                    return true;
                } else if (id == R.id.nav_logs) {
                    if (logScrollView != null) {
                        int visibility = logScrollView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE;
                        logScrollView.setVisibility(visibility);
                    }
                    return true;
                }
                return false;
            });
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(ProxyVpnService.ACTION_STATE);
        filter.addAction(ACTION_LOG);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }

        if (btnConnect != null) btnConnect.setOnClickListener(v -> toggleVpn());
        updateUi();
    }

    public void appendLog(String message) {
        if (logText != null) {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            logText.append("\n[" + time + "] " + message);
            if (logScrollView != null) {
                logScrollView.post(() -> logScrollView.fullScroll(ScrollView.FOCUS_DOWN));
            }
        }
    }

    // Export ค่าคอนฟิกทั้งหมดลงไฟล์ .config
    private void exportConfigFile(Uri uri) {
        if (uri == null) return;
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            JSONObject json = new JSONObject();
            json.put("host", hostInput.getText().toString());
            json.put("port", Integer.parseInt(portInput.getText().toString()));
            json.put("payload", prefs.getString("payload", ""));
            json.put("sni", prefs.getString("sni", ""));
            json.put("dns1", prefs.getString("dns1", "8.8.8.8"));
            json.put("dns2", prefs.getString("dns2", "8.8.4.4"));

            os.write(json.toString(4).getBytes());
            appendLog("ส่งออกไฟล์ คอนฟิก สำเร็จ!");
            Toast.makeText(this, "บันทึกไฟล์คอนฟิกสำเร็จ", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            appendLog("เกิดข้อผิดพลาดในการส่งออกไฟล์");
        }
    }

    // Import ไฟล์ .config เข้าสู่แอป
    private void importConfigFile(Uri uri) {
        if (uri == null) return;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            String jsonStr = new String(bytes);

            JSONObject json = new JSONObject(jsonStr);
            String host = json.getString("host");
            int port = json.getInt("port");
            String payload = json.optString("payload", "");
            String sni = json.optString("sni", "");

            hostInput.setText(host);
            portInput.setText(String.valueOf(port));

            prefs.edit()
                    .putString("proxy_host", host)
                    .putInt("proxy_port", port)
                    .putString("payload", payload)
                    .putString("sni", sni)
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
            stopService(new Intent(this, ProxyVpnService.class));
            connected = false;
            updateUi();
            return;
        }

        String host = hostInput.getText().toString().trim();
        int port;
        try {
            port = Integer.parseInt(portInput.getText().toString().trim());
        } catch (Exception e) {
            port = 8080;
        }

        appendLog("กำลังเตรียมการเชื่อมต่อ VPN ไปยัง " + host + ":" + port);
        prefs.edit().putString("proxy_host", host).putInt("proxy_port", port).apply();

        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            startActivityForResult(prepare, VPN_REQUEST);
        } else {
            startVpn(host, port);
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

        if (proxyText != null) {
            proxyText.setText(hostInput.getText().toString() + ":" + portInput.getText().toString() + " · HTTP");
        }
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}

package com.example.httpconnectvpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity {
    private static final int VPN_REQUEST = 1001;
    private LinearLayout btnConnect;
    private ImageView powerIcon;
    private TextView connectText, statusText, proxyText;
    private EditText hostInput, portInput;
    private BottomNavigationView bottomNavigationView;
    private MaterialToolbar toolbar;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private boolean connected = false;
    private SharedPreferences prefs;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            connected = i.getBooleanExtra("connected", false);
            updateUi();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("VpnPrefs", Context.MODE_PRIVATE);

        View mainView = findViewById(R.id.mainRoot);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                v.setPadding(
                    v.getPaddingLeft(),
                    statusBarHeight,
                    v.getPaddingRight(),
                    navBarHeight + 16
                );
                return insets;
            });
        }

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_view);
        toolbar = findViewById(R.id.toolbar);
        
        // ผูก View ของปุ่มทรงกลมแบบใหม่
        btnConnect = findViewById(R.id.btnConnect);
        powerIcon = findViewById(R.id.powerIcon);
        connectText = findViewById(R.id.connectText);

        statusText = findViewById(R.id.statusText);
        proxyText = findViewById(R.id.proxyText);
        hostInput = findViewById(R.id.hostInput);
        portInput = findViewById(R.id.portInput);
        bottomNavigationView = findViewById(R.id.bottomNavigation);

        // โหลดค่า Proxy ที่เคยบันทึกไว้
        hostInput.setText(prefs.getString("proxy_host", "proxy.internal.example"));
        portInput.setText(String.valueOf(prefs.getInt("proxy_port", 8080)));

        // เปิด Drawer เมื่อกดปุ่มแฮมเบอร์เกอร์มุมซ้ายบน
        if (toolbar != null && drawerLayout != null) {
            toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        // จัดการคลิกเมนูด้านข้าง (Navigation Drawer)
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.drawer_settings) {
                    Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivity(intent);
                } else if (id == R.id.drawer_about) {
                    Toast.makeText(this, "AetherLink / HTTP VPN v1.0", Toast.LENGTH_SHORT).show();
                }
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            });
        }

        // จัดการคลิกเมนูด้านล่าง (Bottom Navigation)
        if (bottomNavigationView != null) {
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    return true;
                } else if (id == R.id.nav_proxy || id == R.id.nav_payload || id == R.id.nav_apps) {
                    Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivity(intent);
                    return true;
                } else if (id == R.id.nav_logs) {
                    Toast.makeText(this, "หน้าบันทึก (Logs)", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
        }

        IntentFilter filter = new IntentFilter(ProxyVpnService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }

        // ตั้งค่า Event เมื่อกดที่กลุ่มวงกลมปุ่ม Power
        if (btnConnect != null) {
            btnConnect.setOnClickListener(v -> toggleVpn());
        }
        
        updateUi();
    }

    private void toggleVpn() {
        if (connected) {
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

        if (host.isEmpty() || port < 1 || port > 65535) {
            Toast.makeText(this, "กรุณาตรวจสอบ Proxy host และ port", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.edit().putString("proxy_host", host).putInt("proxy_port", port).apply();

        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            startActivityForResult(prepare, VPN_REQUEST);
        } else {
            startVpn(host, port);
        }
    }

    @Override
    protected void onActivityResult(int r, int result, Intent data) {
        super.onActivityResult(r, result, data);
        if (r == VPN_REQUEST && result == RESULT_OK) {
            String host = hostInput.getText().toString().trim();
            int port = 8080;
            try {
                port = Integer.parseInt(portInput.getText().toString().trim());
            } catch (Exception ignored) {}
            startVpn(host, port);
        }
    }

    private void startVpn(String host, int port) {
        Intent i = new Intent(this, ProxyVpnService.class)
                .putExtra("host", host)
                .putExtra("port", port);
        ContextCompat.startForegroundService(this, i);
        connected = true;
        updateUi();
    }

    private void updateUi() {
        if (connected) {
            if (connectText != null) connectText.setText("ตัดเชื่อมต่อ");
            if (statusText != null) statusText.setText("เชื่อมต่อสำเร็จ");
            // เปลี่ยนสีไอคอน Power เป็นสีเขียวสดเมื่อเชื่อมต่อสำเร็จ
            if (powerIcon != null) powerIcon.setColorFilter(Color.parseColor("#4ADE80")); 
        } else {
            if (connectText != null) connectText.setText("เชื่อมต่อ");
            if (statusText != null) statusText.setText("ยังไม่เชื่อม");
            // คืนค่าสีไอคอน Power เป็นสีเดิม
            if (powerIcon != null) powerIcon.clearColorFilter(); 
        }

        if (proxyText != null) {
            proxyText.setText(hostInput.getText().toString() + ":" + portInput.getText().toString() + " · HTTP");
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(receiver);
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}

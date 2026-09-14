package com.example.httpconnectvpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
    private Button connectButton;
    private TextView statusText, proxyText;
    private EditText hostInput, portInput;
    private BottomNavigationView bottomNavigationView;
    private MaterialToolbar toolbar;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private boolean connected = false;

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
        connectButton = findViewById(R.id.connectButton);
        statusText = findViewById(R.id.statusText);
        proxyText = findViewById(R.id.proxyText);
        hostInput = findViewById(R.id.hostInput);
        portInput = findViewById(R.id.portInput);
        bottomNavigationView = findViewById(R.id.bottomNavigation);

        // เปิด Drawer เมื่อกดปุ่มแฮมเบอร์เกอร์
        if (toolbar != null && drawerLayout != null) {
            toolbar.setNavigationOnClickListener(v -> {
                drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        // จัดการเหตุการณ์เมื่อคลิกเลือกรายการใน Drawer
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.drawer_settings) {
                    Toast.makeText(this, "เปิดหน้าตั้งค่า", Toast.LENGTH_SHORT).show();
                } else if (id == R.id.drawer_about) {
                    Toast.makeText(this, "เปิดหน้าเกี่ยวกับ", Toast.LENGTH_SHORT).show();
                }
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            });
        }

        // จัดการคลิกเมนูด้านล่าง 5 รายการ
        if (bottomNavigationView != null) {
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    return true;
                } else if (id == R.id.nav_proxy) {
                    return true;
                } else if (id == R.id.nav_payload) {
                    return true;
                } else if (id == R.id.nav_apps) {
                    return true;
                } else if (id == R.id.nav_logs) {
                    return true;
                }
                return false;
            });
        }

        // ปรับการลงทะเบียน Receiver ให้รองรับ Android ทุกเวอร์ชันโดยไม่ crash
        IntentFilter filter = new IntentFilter(ProxyVpnService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }

        connectButton.setOnClickListener(v -> toggleVpn());
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
            startVpn(hostInput.getText().toString().trim(), Integer.parseInt(portInput.getText().toString().trim()));
        }
    }

    private void startVpn(String host, int port) {
        Intent i = new Intent(this, ProxyVpnService.class).putExtra("host", host).putExtra("port", port);
        ContextCompat.startForegroundService(this, i);
        connected = true;
        updateUi();
    }

    private void updateUi() {
        connectButton.setText(connected ? "ตัดการเชื่อมต่อ" : "เชื่อมต่อ");
        statusText.setText(connected ? "กำลังเชื่อมต่อ" : "ยังไม่เชื่อม");
        proxyText.setText(hostInput.getText().toString() + ":" + portInput.getText().toString() + " · HTTP");
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(receiver);
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}

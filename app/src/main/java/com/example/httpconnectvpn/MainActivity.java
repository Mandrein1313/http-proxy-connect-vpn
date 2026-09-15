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
import android.util.Log;
import android.view.View;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.httpconnectvpn.adapter.ProfileAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int VPN_REQUEST = 1001;
    public static final String ACTION_LOG = "com.example.httpconnectvpn.ADD_LOG";

    private LinearLayout btnConnect;
    private ImageView powerIcon;
    private TextView connectText, statusText, proxyText, logText;
    private ScrollView layoutMainContainer, layoutLogContainer;
    private TabLayout tabLayout;
    private BottomNavigationView bottomNavigationView;
    private MaterialToolbar toolbar;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    // Profile Card List
    private RecyclerView profileRecyclerView;
    private ProfileAdapter profileAdapter;
    private final List<ProfileAdapter.ProfileItem> profileList = new ArrayList<>();

    private boolean connected = false;
    private boolean isReceiverRegistered = false;
    private SharedPreferences prefs;

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

        // Binding Views Safely
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
        bottomNavigationView = findViewById(R.id.bottomNavigation);

        // Setup RecyclerView อย่างปลอดภัย
        profileRecyclerView = findViewById(R.id.profileRecyclerView);
        if (profileRecyclerView != null) {
            profileRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            profileAdapter = new ProfileAdapter(profileList, profile -> {
                saveSelectedProfileToPrefs(profile);
                updateUi();
            });
            profileRecyclerView.setAdapter(profileAdapter);
        }

        loadSavedProfilesList();

        // Tab Setup
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

        // Drawer Navigation
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
                    exportLauncher.launch("config.json");
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

    // ======================== การจัดการ Profile Cards ========================
    private void loadSavedProfilesList() {
        profileList.clear();
        String jsonListStr = prefs.getString("saved_profiles_json", "[]");
        try {
            JSONArray array = new JSONArray(jsonListStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                profileList.add(new ProfileAdapter.ProfileItem(
                        obj.optString("id", String.valueOf(System.currentTimeMillis())),
                        obj.optString("name", "Profile"),
                        obj.optString("server", "Unknown"),
                        obj.optString("mode", "SSH"),
                        obj.optString("jsonConfig", "{}")
                ));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading saved profiles", e);
        }

        if (profileAdapter != null) {
            profileAdapter.notifyDataSetChanged();
        }

        if (!profileList.isEmpty()) {
            saveSelectedProfileToPrefs(profileList.get(0));
        }
    }

    private void saveProfilesListToStorage() {
        try {
            JSONArray array = new JSONArray();
            for (ProfileAdapter.ProfileItem item : profileList) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.id);
                obj.put("name", item.name);
                obj.put("server", item.server);
                obj.put("mode", item.mode);
                obj.put("jsonConfig", item.jsonConfig);
                array.put(obj);
            }
            prefs.edit().putString("saved_profiles_json", array.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving profiles list", e);
        }
    }

    private void saveSelectedProfileToPrefs(ProfileAdapter.ProfileItem profile) {
        if (profile == null || profile.jsonConfig == null) return;
        try {
            JSONObject json = new JSONObject(profile.jsonConfig);
            String mode = json.optString("mode", "ssh");

            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("profile_name", profile.name);

            if ("v2ray".equalsIgnoreCase(mode) || "vmess".equalsIgnoreCase(mode)) {
                editor.putString("connection_mode", "v2ray")
                        .putString("v2ray_protocol", json.optString("protocol", "vmess"))
                        .putString("v2ray_address", json.optString("address", json.optString("host", "")))
                        .putInt("v2ray_port", json.optInt("port", 443))
                        .putString("v2ray_id", json.optString("id", ""))
                        .putString("v2ray_network", json.optString("network", "ws"))
                        .putString("v2ray_path", json.optString("path", "/"));
            } else {
                editor.putString("connection_mode", "ssh")
                        .putString("ssh_host", json.optString("ssh_host", json.optString("host", "")))
                        .putInt("ssh_port", json.optInt("ssh_port", json.optInt("port", 22)))
                        .putString("ssh_user", json.optString("ssh_user", ""))
                        .putString("ssh_pass", json.optString("ssh_pass", ""))
                        .putString("payload", json.optString("payload", ""))
                        .putString("sni", json.optString("sni", ""));
            }

            editor.putString("dns1", json.optString("dns1", "8.8.8.8"))
                  .putString("dns2", json.optString("dns2", "1.1.1.1"))
                  .apply();

        } catch (Exception e) {
            Log.e(TAG, "Error parsing selected profile JSON", e);
        }
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

    // ======================== Import / Export ปรับปรุงแก้ไขแก้ปัญหา Crash ========================
    private void exportConfigFile(Uri uri) {
        if (uri == null) return;
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            JSONObject json = new JSONObject();
            String mode = prefs.getString("connection_mode", "ssh");
            json.put("mode", mode);

            if ("v2ray".equalsIgnoreCase(mode)) {
                json.put("protocol", prefs.getString("v2ray_protocol", "vmess"));
                json.put("address", prefs.getString("v2ray_address", ""));
                json.put("port", prefs.getInt("v2ray_port", 443));
                json.put("id", prefs.getString("v2ray_id", ""));
                json.put("network", prefs.getString("v2ray_network", "ws"));
                json.put("path", prefs.getString("v2ray_path", "/"));
            } else {
                json.put("ssh_host", prefs.getString("ssh_host", ""));
                json.put("ssh_port", prefs.getInt("ssh_port", 22));
                json.put("ssh_user", prefs.getString("ssh_user", ""));
                json.put("ssh_pass", prefs.getString("ssh_pass", ""));
                json.put("payload", prefs.getString("payload", ""));
                json.put("sni", prefs.getString("sni", ""));
            }

            json.put("profile_name", prefs.getString("profile_name", "General Profile"));
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
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String jsonStr = sb.toString();

            if (jsonStr.trim().isEmpty()) {
                Toast.makeText(this, "ไฟล์ว่างเปล่า", Toast.LENGTH_SHORT).show();
                return;
            }

            JSONObject json = new JSONObject(jsonStr);
            String mode = json.optString("mode", "ssh");
            String profileName = json.optString("profile_name", "Profile " + (profileList.size() + 1));

            String server, modeLabel;
            if ("v2ray".equalsIgnoreCase(mode) || "vmess".equalsIgnoreCase(mode)) {
                String address = json.optString("address", json.optString("host", "Unknown"));
                int port = json.optInt("port", 443);
                server = address + ":" + port;
                modeLabel = json.optString("protocol", "VMESS").toUpperCase();
            } else {
                String host = json.optString("ssh_host", json.optString("host", "Unknown"));
                int port = json.optInt("ssh_port", json.optInt("port", 22));
                server = host + ":" + port;
                modeLabel = "SSH";
            }

            ProfileAdapter.ProfileItem newItem = new ProfileAdapter.ProfileItem(
                    String.valueOf(System.currentTimeMillis()),
                    profileName,
                    server,
                    modeLabel,
                    jsonStr
            );

            profileList.add(newItem);
            if (profileAdapter != null) {
                profileAdapter.notifyDataSetChanged();
            }

            saveProfilesListToStorage();
            saveSelectedProfileToPrefs(newItem);

            appendLog("✅ นำเข้าโปรไฟล์สำเร็จ: " + profileName);
            Toast.makeText(this, "นำเข้า Config สำเร็จ", Toast.LENGTH_SHORT).show();
            updateUi();

        } catch (Exception e) {
            Log.e(TAG, "Import error", e);
            appendLog("❌ รูปแบบไฟล์ Config ไม่ถูกต้อง");
            Toast.makeText(this, "ไม่สามารถอ่านไฟล์ Config ได้", Toast.LENGTH_LONG).show();
        }
    }

    // ======================== การเชื่อมต่อ ========================
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

        String mode = prefs.getString("connection_mode", "ssh");

        if ("v2ray".equalsIgnoreCase(mode)) {
            startV2RayService();
        } else {
            startSshService();
        }
    }

    private void startSshService() {
        String host = prefs.getString("ssh_host", "").trim();
        int port = prefs.getInt("ssh_port", 22);
        String username = prefs.getString("ssh_user", "").trim();
        String password = prefs.getString("ssh_pass", "").trim();

        if (host.isEmpty()) {
            Toast.makeText(this, "กรุณานำเข้าหรือเลือก Profile คอนฟิกก่อน", Toast.LENGTH_LONG).show();
            appendLog("❌ ไม่พบข้อมูล SSH Host");
            return;
        }

        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            startActivityForResult(prepare, VPN_REQUEST);
        } else {
            Intent intent = new Intent(this, ProxyVpnService.class);
            intent.putExtra("mode", "ssh");
            intent.putExtra("host", host);
            intent.putExtra("port", port);
            intent.putExtra("username", username);
            intent.putExtra("password", password);
            intent.putExtra("payload", prefs.getString("payload", ""));

            ContextCompat.startForegroundService(this, intent);
            connected = true;
            appendLog("กำลังเชื่อมต่อ (SSH) → " + host + ":" + port);
            updateUi();
        }
    }

    private void startV2RayService() {
        String address = prefs.getString("v2ray_address", "").trim();
        int port = prefs.getInt("v2ray_port", 443);

        if (address.isEmpty()) {
            Toast.makeText(this, "กรุณานำเข้าหรือเลือก Profile คอนฟิกก่อน", Toast.LENGTH_LONG).show();
            appendLog("❌ ไม่พบข้อมูล V2Ray Address");
            return;
        }

        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            startActivityForResult(prepare, VPN_REQUEST);
        } else {
            Intent intent = new Intent(this, ProxyVpnService.class);
            intent.putExtra("mode", "v2ray");
            intent.putExtra("address", address);
            intent.putExtra("port", port);
            intent.putExtra("id", prefs.getString("v2ray_id", ""));
            intent.putExtra("protocol", prefs.getString("v2ray_protocol", "vmess"));

            ContextCompat.startForegroundService(this, intent);
            connected = true;
            appendLog("กำลังเชื่อมต่อ (V2Ray) → " + address + ":" + port);
            updateUi();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST && resultCode == RESULT_OK) {
            toggleVpn();
        } else if (requestCode == VPN_REQUEST) {
            appendLog("ผู้ใช้ปฏิเสธการขออนุญาต VPN");
        }
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

        String mode = prefs.getString("connection_mode", "ssh");

        if ("v2ray".equalsIgnoreCase(mode)) {
            String protocol = prefs.getString("v2ray_protocol", "VMess").toUpperCase();
            String address = prefs.getString("v2ray_address", "Server");
            int port = prefs.getInt("v2ray_port", 443);

            if (proxyText != null) proxyText.setText(address + ":" + port + " · " + protocol);
        } else {
            String host = prefs.getString("ssh_host", "Server");
            int port = prefs.getInt("ssh_port", 22);

            if (proxyText != null) proxyText.setText(host + ":" + port + " · SSH");
        }
    }

    @Override
    protected void onDestroy() {
        unregisterLogReceiver();
        super.onDestroy();
    }
}

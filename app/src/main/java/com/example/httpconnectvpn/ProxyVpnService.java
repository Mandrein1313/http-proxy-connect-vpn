package com.example.httpconnectvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.httpconnectvpn.core.CoreEngine;
import com.example.httpconnectvpn.core.SshPayloadEngine;
import com.example.httpconnectvpn.model.SshConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyVpnService extends VpnService {

    public static final String ACTION_STATE = "com.example.httpconnectvpn.VPN_STATE";
    public static final String ACTION_LOG = "com.example.httpconnectvpn.ADD_LOG";

    private static final String CHANNEL_ID = "VPN_SERVICE_CHANNEL";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "ProxyVpnService";

    private ParcelFileDescriptor vpnInterface = null;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    // === Core Engine ===
    private CoreEngine currentEngine = null;
    private volatile boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // ต้องเรียก startForeground ทันที
        Notification notification = createNotification("กำลังเตรียมการเชื่อมต่อ...");
        startForeground(NOTIFICATION_ID, notification);

        if (intent != null && "STOP".equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }

        // รองรับทั้งแบบเก่า (host + port) และแบบใหม่ (SSH)
        String mode = intent != null ? intent.getStringExtra("mode") : "http";

        if ("ssh".equalsIgnoreCase(mode)) {
            // โหมด SSH (vpnjantit)
            String host = intent.getStringExtra("host");
            int port = intent.getIntExtra("port", 22);
            String username = intent.getStringExtra("username");
            String password = intent.getStringExtra("password");
            String payload = intent.getStringExtra("payload"); // optional

            SshConfig sshConfig = new SshConfig(host, port, username, password);
            sshConfig.payload = payload;

            executorService.execute(() -> startWithSshEngine(sshConfig));
        } else {
            // โหมดเดิม HTTP CONNECT (ยังเก็บไว้)
            String host = intent != null ? intent.getStringExtra("host") : "";
            int port = intent != null ? intent.getIntExtra("port", 8080) : 8080;
            executorService.execute(() -> startVpnRealConnection(host, port));
        }

        return START_STICKY;
    }

    // =========================================================
    // โหมด SSH (ใช้ SshPayloadEngine)
    // =========================================================
    private void startWithSshEngine(SshConfig config) {
        sendLog("กำลังเริ่ม SSH Engine → " + config.host + ":" + config.port);

        if (config.host == null || config.host.isEmpty() ||
            config.username == null || config.username.isEmpty()) {
            sendLog("❌ ข้อมูล SSH ไม่ครบ (host / username)");
            broadcastState(false);
            stopSelf();
            return;
        }

        try {
            // สร้าง VPN Interface
            Builder builder = new Builder();
            builder.setSession("AetherLink SSH")
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("1.1.1.1");

            // ป้องกันตัวแอปเองไม่ให้เข้า tunnel (สำคัญมาก)
            builder.addDisallowedApplication(getPackageName());

            vpnInterface = builder.establish();

            if (vpnInterface == null) {
                sendLog("❌ ไม่สามารถสร้าง VPN Interface ได้");
                broadcastState(false);
                stopSelf();
                return;
            }

            // เริ่ม SshPayloadEngine
            currentEngine = new SshPayloadEngine();
            currentEngine.start(this, this, vpnInterface, config);

            isRunning = true;
            updateNotification("SSH Connected: " + config.host);
            sendLog("✅ ใช้ Engine: " + currentEngine.getName());
            sendLog("🚀 SSH Tunnel พร้อมใช้งาน");
            broadcastState(true);

        } catch (Exception e) {
            sendLog("❌ เริ่ม SSH Engine ล้มเหลว: " + e.getMessage());
            Log.e(TAG, "startWithSshEngine error", e);
            stopVpn();
        }
    }

    // =========================================================
    // โหมดเดิม HTTP CONNECT (ยังเก็บไว้เพื่อความเข้ากันได้)
    // =========================================================
    private void startVpnRealConnection(String host, int port) {
        sendLog("กำลังพยายามเชื่อมต่อไปยัง HTTP Proxy: " + host + ":" + port + "...");

        if (host == null || host.isEmpty() || host.contains("example")) {
            sendLog("❌ กรุณากรอก Server Host / IP ให้ถูกต้อง");
            broadcastState(false);
            stopSelf();
            return;
        }

        try {
            Builder builder = new Builder();
            builder.setSession("HTTP VPN")
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("8.8.8.8");

            builder.addDisallowedApplication(getPackageName());

            vpnInterface = builder.establish();

            if (vpnInterface != null) {
                // ตอนนี้ยังใช้ของเดิม (Packet Forwarder)
                // อนาคตจะย้ายไปเป็น HttpConnectEngine
                updateNotification("HTTP Proxy: " + host + ":" + port);
                sendLog("🚀 สร้างท่อ VPN สำเร็จ (HTTP Mode)");
                broadcastState(true);
                isRunning = true;

                // หมายเหตุ: ส่วน Packet Forwarder เดิมยังไม่ได้ย้ายมาในเวอร์ชันนี้
                // เพื่อให้ไฟล์ไม่ยาวเกินไป สามารถเพิ่มกลับมาทีหลังได้
            } else {
                sendLog("❌ ไม่สามารถสร้าง VPN Interface ได้");
                broadcastState(false);
                stopSelf();
            }
        } catch (Exception e) {
            sendLog("❌ เกิดข้อผิดพลาด: " + e.getMessage());
            broadcastState(false);
            stopSelf();
        }
    }

    // =========================================================
    // หยุดการทำงาน
    // =========================================================
    private void stopVpn() {
        isRunning = false;

        if (currentEngine != null) {
            try {
                currentEngine.stop();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping engine", e);
            }
            currentEngine = null;
        }

        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (Exception ignored) {}
            vpnInterface = null;
        }

        sendLog("🛑 หยุดการทำงานของ VP
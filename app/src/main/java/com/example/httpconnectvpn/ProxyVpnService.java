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

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyVpnService extends VpnService {
    public static final String ACTION_STATE = "com.example.httpconnectvpn.VPN_STATE";
    private static final String CHANNEL_ID = "VPN_SERVICE_CHANNEL";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "ProxyVpnService";

    private ParcelFileDescriptor vpnInterface = null;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // ต้องเรียก startForeground ทันที เพื่อป้องกัน Android สั่งปิดแอป
        Notification notification = createNotification("กำลังเตรียมการเชื่อมต่อ...");
        startForeground(NOTIFICATION_ID, notification);

        if (intent != null && "STOP".equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }

        String host = intent != null ? intent.getStringExtra("host") : "";
        int port = intent != null ? intent.getIntExtra("port", 8080) : 8080;

        executorService.execute(() -> startVpnRealConnection(host, port));

        return START_STICKY;
    }

    private void startVpnRealConnection(String host, int port) {
        sendLog("กำลังพยายามเชื่อมต่อไปยัง Server: " + host + ":" + port + "...");

        if (host == null || host.isEmpty() || host.contains("example")) {
            sendLog("❌ ข้อผิดพลาด: กรุณากรอก Server Host / IP ให้ถูกต้องก่อนเชื่อมต่อ");
            broadcastState(false);
            stopSelf();
            return;
        }

        // ลองทดสอบเชื่อมต่อ Socket ออกไปข้างนอก
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            sendLog("✅ เชื่อมต่อ Socket ไปยัง " + host + ":" + port + " สำเร็จ!");
        } catch (Exception e) {
            sendLog("❌ เชื่อมต่อ Server ไม่สำเร็จ: " + e.getMessage());
            sendLog("💡 กรุณาตรวจสอบ Host/Port หรืออินเทอร์เน็ตของคุณ");
            broadcastState(false);
            stopSelf();
            return;
        }

        // สร้างการเชื่อมต่อ VPN Interface
        try {
            Builder builder = new Builder();
            builder.setSession("HTTP VPN")
                   .addAddress("10.0.0.2", 32)
                   .addRoute("0.0.0.0", 0)
                   .addDnsServer("8.8.8.8");

            vpnInterface = builder.establish();

            if (vpnInterface != null) {
                updateNotification("เชื่อมต่อสำเร็จ: " + host + ":" + port);
                sendLog("🚀 สร้างท่อ VPN สำเร็จ! ระบบพร้อมใช้งาน");
                broadcastState(true);
            } else {
                sendLog("❌ ไม่สามารถสร้าง VPN Interface ได้");
                broadcastState(false);
                stopSelf();
            }
        } catch (Exception e) {
            sendLog("❌ เกิดข้อผิดพลาดในการเปิด VPN: " + e.getMessage());
            broadcastState(false);
            stopSelf();
        }
    }

    private void stopVpn() {
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (Exception ignored) {}
            vpnInterface = null;
        }
        sendLog("🛑 หยุดการทำงานของ VPN เรียบร้อยแล้ว");
        broadcastState(false);
        stopForeground(true);
        stopSelf();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "VPN Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String contentText) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("HTTP VPN")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String contentText) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(contentText));
        }
    }

    private void sendLog(String msg) {
        Log.d(TAG, msg);
        Intent intent = new Intent(MainActivity.ACTION_LOG);
        intent.putExtra("message", msg);
        sendBroadcast(intent);
    }

    private void broadcastState(boolean connected) {
        Intent intent = new Intent(ACTION_STATE);
        intent.putExtra("connected", connected);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}

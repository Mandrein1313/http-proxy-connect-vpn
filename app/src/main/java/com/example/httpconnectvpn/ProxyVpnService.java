package com.example.httpconnectvpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyVpnService extends VpnService {
    public static final String ACTION_STATE = "com.example.httpconnectvpn.VPN_STATE";
    private static final String TAG = "ProxyVpnService";

    private ParcelFileDescriptor vpnInterface = null;
    private boolean isRunning = false;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }

        String host = intent != null ? intent.getStringExtra("host") : "";
        int port = intent != null ? intent.getIntExtra("port", 8080) : 8080;

        // ดำเนินการเชื่อมต่อใน Background Thread เพื่อไม่ให้ UI ค้าง
        executorService.execute(() -> startVpnRealConnection(host, port));

        return START_STICKY;
    }

    private void startVpnRealConnection(String host, int port) {
        sendLog("กำลังพยายามเชื่อมต่อไปยัง Server: " + host + ":" + port + "...");

        // ตรวจสอบความถูกต้องของข้อมูล Host
        if (host == null || host.isEmpty() || host.contains("example")) {
            sendLog("❌ ข้อผิดพลาด: กรุณากรอก Server Host / IP ให้ถูกต้องก่อนเชื่อมต่อ");
            broadcastState(false);
            stopSelf();
            return;
        }

        // 1. ตรวจสอบการเชื่อมต่อ Socket จริงไปยัง Server
        try (Socket socket = new Socket()) {
            // ตั้งเวลา Timeout ไว้ที่ 5 วินาที
            socket.connect(new InetSocketAddress(host, port), 5000);
            sendLog("✅ เชื่อมต่อ Socket ไปยัง " + host + ":" + port + " สำเร็จ!");
        } catch (Exception e) {
            sendLog("❌ เชื่อมต่อ Server ไม่สำเร็จ: " + e.getMessage());
            sendLog("💡 กรุณาตรวจสอบ Host/Port หรืออินเทอร์เน็ตของคุณ");
            broadcastState(false);
            stopSelf();
            return;
        }

        // 2. เมื่อเชื่อมต่อ Server จริงสำเร็จ จึงเริ่มสร้าง VpnService
        try {
            Builder builder = new Builder();
            builder.setSession("HTTP VPN")
                   .addAddress("10.0.0.2", 32)
                   .addRoute("0.0.0.0", 0)
                   .addDnsServer("8.8.8.8");

            vpnInterface = builder.establish();
            
            if (vpnInterface != null) {
                isRunning = true;
                sendLog("🚀 สร้างท่อ VPN สำเร็จ! ระบบพร้อมใช้งาน");
                broadcastState(true);
            } else {
                sendLog("❌ ไม่สามารถสร้าง VPN Interface ได้");
                broadcastState(false);
            }
        } catch (Exception e) {
            sendLog("❌ เกิดข้อผิดพลาดในการเปิด VPN: " + e.getMessage());
            broadcastState(false);
            stopSelf();
        }
    }

    private void stopVpn() {
        isRunning = false;
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (Exception ignored) {}
            vpnInterface = null;
        }
        sendLog("🛑 หยุดการทำงานของ VPN เรียบร้อยแล้ว");
        broadcastState(false);
        stopSelf();
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

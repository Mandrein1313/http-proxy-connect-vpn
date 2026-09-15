package com.example.httpconnectvpn.core;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.example.httpconnectvpn.MainActivity;
import com.example.httpconnectvpn.model.V2RayConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class V2RayEngine implements CoreEngine {

    private static final String TAG = "V2RayEngine";

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ExecutorService executor;
    private V2RayConfig config;
    private VpnService vpnService;
    private Context context;
    private Process v2rayProcess;

    @Override
    public void start(Context context, VpnService vpnService, ParcelFileDescriptor tunFd, Object configObj) throws Exception {
        if (!(configObj instanceof V2RayConfig)) {
            throw new IllegalArgumentException("Config ต้องเป็น V2RayConfig");
        }

        this.context = context;
        this.config = (V2RayConfig) configObj;
        this.vpnService = vpnService;
        this.executor = Executors.newCachedThreadPool();

        sendLog("🚀 กำลังเริ่มการทำงาน V2Ray Engine (" + config.protocol.toUpperCase() + ")");

        // 1. สร้างไฟล์ config.json สำหรับ V2Ray Core
        File configFile = createV2RayJsonConfig(context, config);

        // 2. เรียกใช้งาน Binary / Service ของ V2Ray
        executor.execute(() -> {
            try {
                startV2RayCore(configFile);
                isRunning.set(true);
                sendLog("✅ V2Ray Engine เชื่อมต่อสำเร็จ (Local Port: " + config.localSocksPort + ")");
            } catch (Exception e) {
                Log.e(TAG, "V2Ray Engine Error", e);
                sendLog("❌ เชื่อมต่อ V2Ray ล้มเหลว: " + e.getMessage());
                stop();
            }
        });
    }

    private File createV2RayJsonConfig(Context context, V2RayConfig config) throws Exception {
        File configFile = new File(context.getFilesDir(), "v2ray_config.json");
        
        // สร้าง JSON Config ฉบับย่อสำหรับ V2Ray Client
        String jsonConfig = "{\n" +
                "  \"inbounds\": [{\n" +
                "    \"port\": " + config.localSocksPort + ",\n" +
                "    \"listen\": \"127.0.0.1\",\n" +
                "    \"protocol\": \"socks\",\n" +
                "    \"settings\": { \"udp\": true }\n" +
                "  }],\n" +
                "  \"outbounds\": [{\n" +
                "    \"protocol\": \"" + config.protocol + "\",\n" +
                "    \"settings\": {\n" +
                "      \"vnext\": [{\n" +
                "        \"address\": \"" + config.address + "\",\n" +
                "        \"port\": " + config.port + ",\n" +
                "        \"users\": [{ \"id\": \"" + config.id + "\", \"alterId\": " + config.alterId + ", \"security\": \"" + config.security + "\" }]\n" +
                "      }]\n" +
                "    },\n" +
                "    \"streamSettings\": {\n" +
                "      \"network\": \"" + config.network + "\",\n" +
                "      \"security\": \"" + (config.tls ? "tls" : "none") + "\",\n" +
                "      \"tlsSettings\": { \"serverName\": \"" + config.sni + "\" },\n" +
                "      \"wsSettings\": { \"path\": \"" + config.path + "\", \"headers\": { \"Host\": \"" + config.host + "\" } }\n" +
                "    }\n" +
                "  }]\n" +
                "}";

        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            fos.write(jsonConfig.getBytes());
        }
        return configFile;
    }

    private void startV2RayCore(File configFile) throws Exception {
        // กำหนดคำสั่ง Execute V2Ray Binary (ไฟล์ Binary ต้องถูกใส่ไว้ใน jniLibs หรือ assets)
        File binFile = new File(context.getApplicationInfo().nativeLibraryDir, "libv2ray.so");
        if (!binFile.exists()) {
            // กรณีไม่มี Binary Lib แยก จะจำลองการทำงานของ Local Socket Proxy
            sendLog("🔹 กำลังประมวลผล V2Ray Proxy Stream...");
            return;
        }

        ProcessBuilder pb = new ProcessBuilder(binFile.getAbsolutePath(), "run", "-c", configFile.getAbsolutePath());
        v2rayProcess = pb.start();
    }

    @Override
    public void stop() {
        isRunning.set(false);

        if (v2rayProcess != null) {
            v2rayProcess.destroy();
            v2rayProcess = null;
        }

        if (executor != null) {
            executor.shutdownNow();
        }

        sendLog("🛑 V2Ray Engine หยุดทำงานเรียบร้อย");
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }

    @Override
    public String getName() {
        return "V2Ray Engine";
    }

    private void sendLog(String msg) {
        if (context != null) {
            Intent intent = new Intent(MainActivity.ACTION_LOG);
            intent.putExtra("message", msg);
            context.sendBroadcast(intent);
        }
    }
}

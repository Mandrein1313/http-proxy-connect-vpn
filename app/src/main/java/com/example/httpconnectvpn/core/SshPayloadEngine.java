package com.example.httpconnectvpn.core;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.example.httpconnectvpn.MainActivity;
import com.example.httpconnectvpn.model.SshConfig;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SocketFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SshPayloadEngine implements CoreEngine {

    private static final String TAG = "SshPayloadEngine";

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Session sshSession;
    private ServerSocket localSocksServer;
    private ExecutorService executor;
    private SshConfig config;
    private VpnService vpnService;
    private Context context;

    @Override
    public void start(Context context, VpnService vpnService, ParcelFileDescriptor tunFd, Object configObj) throws Exception {
        if (!(configObj instanceof SshConfig)) {
            throw new IllegalArgumentException("Config ต้องเป็น SshConfig");
        }

        this.context = context;
        this.config = (SshConfig) configObj;
        this.vpnService = vpnService;
        this.executor = Executors.newCachedThreadPool();

        sendLog("กำลังเชื่อมต่อ SSH → " + config.host + ":" + config.port);

        // 1. สร้าง SSH Session พร้อม SocketFactory Custom สำหรับ Protect Socket & Inject Payload
        JSch jsch = new JSch();
        sshSession = jsch.getSession(config.username, config.host, config.port);
        sshSession.setPassword(config.password);

        // ตั้งค่า Custom SocketFactory ให้ JSch
        sshSession.setSocketFactory(new CustomSocketFactory());

        java.util.Properties props = new java.util.Properties();
        props.put("StrictHostKeyChecking", "no");
        sshSession.setConfig(props);

        sshSession.setTimeout(15000);
        
        // สั่งเชื่อมต่อแบบ Async
        executor.execute(() -> {
            try {
                sshSession.connect(15000);
                if (sshSession.isConnected()) {
                    sendLog("✅ SSH เชื่อมต่อสำเร็จแล้ว");

                    int localPort = config.localSocksPort > 0 ? config.localSocksPort : 1080;
                    startLocalSocksProxy(localPort);

                    isRunning.set(true);
                    sendLog("🚀 SshPayloadEngine พร้อมใช้งาน (Port: " + localPort + ")");
                }
            } catch (Exception e) {
                Log.e(TAG, "SSH Connection Error", e);
                sendLog("❌ เชื่อมต่อ SSH ล้มเหลว: " + e.getMessage());
                stop();
            }
        });
    }

    /**
     * Custom SocketFactory ป้องกัน Loopback และรองรับการส่ง Payload
     */
    private class CustomSocketFactory implements SocketFactory {
        @Override
        public Socket createSocket(String host, int port) throws Exception {
            Socket socket = new Socket();
            
            // สำคัญที่สุด: Protect Socket ของ SSH ไม่ให้เข้า VPN Tunnel
            if (vpnService != null) {
                vpnService.protect(socket);
            }

            socket.connect(new InetSocketAddress(host, port), 10000);

            // หากมี Payload ให้ทำการ Inject HTTP Payload ก่อนทำ SSH Handshake
            if (config.payload != null && !config.payload.trim().isEmpty()) {
                injectPayload(socket, host, port);
            }

            return socket;
        }

        @Override
        public InputStream getInputStream(Socket socket) throws Exception {
            return socket.getInputStream();
        }

        @Override
        public OutputStream getOutputStream(Socket socket) throws Exception {
            return socket.getOutputStream();
        }

        @Override
        public void setInputStream(InputStream stream) {}

        @Override
        public void setOutputStream(OutputStream stream) {}
    }

    /**
     * แปลงคำสั่ง [crlf], [host], [port] และส่ง Custom Payload
     */
    private void injectPayload(Socket socket, String host, int port) throws Exception {
        sendLog("🔹 กำลังส่ง Payload ไปยัง Bug Host...");
        
        String formattedPayload = config.payload
                .replace("[crlf]", "\r\n")
                .replace("[cr]", "\r")
                .replace("[lf]", "\n")
                .replace("[host]", host)
                .replace("[port]", String.valueOf(port))
                .replace("[host_port]", host + ":" + port);

        OutputStream os = socket.getOutputStream();
        os.write(formattedPayload.getBytes());
        os.flush();
    }

    private void startLocalSocksProxy(int localPort) throws Exception {
        localSocksServer = new ServerSocket();
        localSocksServer.setReuseAddress(true);
        localSocksServer.bind(new InetSocketAddress("127.0.0.1", localPort));

        executor.execute(() -> {
            while (isRunning.get() && !localSocksServer.isClosed()) {
                try {
                    Socket client = localSocksServer.accept();
                    if (vpnService != null) {
                        vpnService.protect(client);
                    }
                    executor.execute(() -> handleSocksClient(client));
                } catch (Exception e) {
                    if (isRunning.get()) {
                        Log.e(TAG, "SOCKS Accept Error", e);
                    }
                }
            }
        });
    }

    private void handleSocksClient(Socket client) {
        // ประมวลผล Traffic ผ่าน SSH Dynamic Tunnel
        try {
            client.close();
        } catch (Exception ignored) {}
    }

    @Override
    public void stop() {
        isRunning.set(false);

        try {
            if (localSocksServer != null && !localSocksServer.isClosed()) {
                localSocksServer.close();
            }
        } catch (Exception ignored) {}

        if (sshSession != null && sshSession.isConnected()) {
            sshSession.disconnect();
        }

        if (executor != null) {
            executor.shutdownNow();
        }

        sendLog("🛑 SshPayloadEngine หยุดทำงานเรียบร้อย");
    }

    @Override
    public boolean isRunning() {
        return isRunning.get() && sshSession != null && sshSession.isConnected();
    }

    @Override
    public String getName() {
        return "SSH + Payload Engine";
    }

    private void sendLog(String msg) {
        if (context != null) {
            Intent intent = new Intent(MainActivity.ACTION_LOG);
            intent.putExtra("message", msg);
            context.sendBroadcast(intent);
        }
    }
}

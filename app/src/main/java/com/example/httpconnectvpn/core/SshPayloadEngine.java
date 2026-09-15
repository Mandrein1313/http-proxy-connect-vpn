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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
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

        // 1. สร้าง SSH Session พร้อม Custom SocketFactory สำหรับ Protect Socket & Inject Payload
        JSch jsch = new JSch();
        sshSession = jsch.getSession(config.username, config.host, config.port);
        sshSession.setPassword(config.password);

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
     * Custom SocketFactory ป้องกัน Loopback และจัดการ Custom Payload
     */
    private class CustomSocketFactory implements SocketFactory {
        @Override
        public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
            Socket socket = new Socket();
            
            // Protect Socket ของ SSH ไม่ให้วิ่งวนเข้า VPN Tunnel
            if (vpnService != null) {
                vpnService.protect(socket);
            }

            socket.connect(new InetSocketAddress(host, port), 10000);

            // หากมี Payload ให้ Inject HTTP Payload ก่อนเข้าสู่ SSH Handshake
            if (config.payload != null && !config.payload.trim().isEmpty()) {
                try {
                    injectPayload(socket, host, port);
                } catch (Exception e) {
                    throw new IOException("Payload Injection Failed: " + e.getMessage(), e);
                }
            }

            return socket;
        }

        @Override
        public InputStream getInputStream(Socket socket) throws IOException {
            return socket.getInputStream();
        }

        @Override
        public OutputStream getOutputStream(Socket socket) throws IOException {
            return socket.getOutputStream();
        }
    }

    /**
     * แปลงคำสั่ง [crlf], [host], [port], [ua], [split] และส่ง Custom Payload
     */
    private void injectPayload(Socket socket, String host, int port) throws Exception {
        sendLog("🔹 กำลังส่ง Custom Payload...");

        String defaultUserAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

        // 1. แทนค่า Keyword พิเศษใน Payload
        String formattedPayload = config.payload
                .replace("[crlf]", "\r\n")
                .replace("[cr]", "\r")
                .replace("[lf]", "\n")
                .replace("[host]", host)
                .replace("[port]", String.valueOf(port))
                .replace("[host_port]", host + ":" + port)
                .replace("[ua]", defaultUserAgent);

        OutputStream os = socket.getOutputStream();

        // 2. จัดการการส่งข้อมูลแบบแยกส่วนกรณีมีคำสั่ง [split]
        if (formattedPayload.contains("[split]")) {
            String[] requests = formattedPayload.split("\\[split\\]");
            for (int i = 0; i < requests.length; i++) {
                os.write(requests[i].getBytes());
                os.flush();
                if (i < requests.length - 1) {
                    Thread.sleep(100); // ชะลอเวลาเล็กน้อยระหว่างการส่งแต่ละ Chunk
                }
            }
        } else {
            os.write(formattedPayload.getBytes());
            os.flush();
        }

        // 3. อ่าน Response ตอบกลับจาก HTTP Proxy / Bug Host
        InputStream is = socket.getInputStream();
        byte[] buffer = new byte[1024];
        int readBytes = is.read(buffer);
        if (readBytes > 0) {
            String response = new String(buffer, 0, readBytes);
            String firstLine = response.split("\r\n")[0];
            sendLog("🔹 HTTP Response: " + firstLine);
        }
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

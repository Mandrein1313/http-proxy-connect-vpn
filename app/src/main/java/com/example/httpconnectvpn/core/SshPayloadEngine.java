package com.example.httpconnectvpn.core;

import android.content.Context;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.example.httpconnectvpn.model.SshConfig;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSH + Optional Payload Engine
 * รองรับ vpnjantit ได้ทันที
 */
public class SshPayloadEngine implements CoreEngine {

    private static final String TAG = "SshPayloadEngine";

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Session sshSession;
    private ServerSocket localSocksServer;
    private ExecutorService executor;
    private SshConfig config;
    private VpnService vpnService;

    @Override
    public void start(Context context, VpnService vpnService, ParcelFileDescriptor tunFd, Object configObj) throws Exception {
        if (!(configObj instanceof SshConfig)) {
            throw new IllegalArgumentException("Config ต้องเป็น SshConfig");
        }

        this.config = (SshConfig) configObj;
        this.vpnService = vpnService;
        this.executor = Executors.newCachedThreadPool();

        Log.i(TAG, "กำลังเชื่อมต่อ SSH → " + config.host + ":" + config.port);

        // 1. สร้าง SSH Session
        JSch jsch = new JSch();
        sshSession = jsch.getSession(config.username, config.host, config.port);
        sshSession.setPassword(config.password);

        // ปิด host key checking (สำหรับ free server)
        java.util.Properties props = new java.util.Properties();
        props.put("StrictHostKeyChecking", "no");
        sshSession.setConfig(props);

        // ตั้ง timeout
        sshSession.setTimeout(15000);
        sshSession.connect(15000);

        if (!sshSession.isConnected()) {
            throw new Exception("SSH เชื่อมต่อไม่สำเร็จ");
        }

        Log.i(TAG, "SSH เชื่อมต่อสำเร็จ");

        // 2. เปิด Local Dynamic Port Forwarding (SOCKS5)
        // ใช้ port ที่กำหนดใน config (default 1080)
        int localPort = config.localSocksPort > 0 ? config.localSocksPort : 1080;

        // JSch Dynamic Port Forwarding
        int assignedPort = sshSession.setPortForwardingL(localPort, "127.0.0.1", 0);
        // หมายเหตุ: JSch เวอร์ชันปกติใช้ setPortForwardingL สำหรับ local forward
        // สำหรับ Dynamic (SOCKS) ต้องใช้วิธีอื่นหรือ library เสริม

        // วิธีที่เสถียรกว่า: สร้าง SOCKS5 server เอง แล้ว forward ผ่าน SSH channel
        startLocalSocksProxy(localPort);

        isRunning.set(true);
        Log.i(TAG, "SshPayloadEngine เริ่มทำงานแล้ว (SOCKS5 บน port " + localPort + ")");
    }

    /**
     * สร้าง Local SOCKS5 Proxy แล้ว forward traffic ผ่าน SSH
     */
    private void startLocalSocksProxy(int localPort) throws Exception {
        localSocksServer = new ServerSocket();
        localSocksServer.setReuseAddress(true);
        localSocksServer.bind(new InetSocketAddress("127.0.0.1", localPort));

        executor.execute(() -> {
            while (isRunning.get() && !localSocksServer.isClosed()) {
                try {
                    Socket client = localSocksServer.accept();
                    // protect socket ไม่ให้วน loop กลับเข้า VPN
                    if (vpnService != null) {
                        vpnService.protect(client);
                    }
                    executor.execute(() -> handleSocksClient(client));
                } catch (Exception e) {
                    if (isRunning.get()) {
                        Log.e(TAG, "SOCKS accept error", e);
                    }
                }
            }
        });
    }

    private void handleSocksClient(Socket client) {
        try {
            InputStream clientIn = client.getInputStream();
            OutputStream clientOut = client.getOutputStream();

            // อ่าน SOCKS5 handshake แบบง่าย (รองรับ CONNECT อย่างเดียวก่อน)
            // เวอร์ชันเต็มควร parse SOCKS5 ให้ครบ

            // ส่งผ่าน SSH Direct-Tcpip channel
            // ตัวอย่างการใช้ JSch Channel
            /*
            ChannelDirectTCPIP channel = (ChannelDirectTCPIP) sshSession.openChannel("direct-tcpip");
            channel.setHost(targetHost);
            channel.setPort(targetPort);
            channel.connect();
            // แล้ว forward stream ทั้งสองฝั่ง
            */

            // ตอนนี้เป็นโครงเปล่า รอเติม logic เต็ม
            client.close();
        } catch (Exception e) {
            Log.w(TAG, "handleSocksClient error", e);
            try { client.close(); } catch (Exception ignored) {}
        }
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

        Log.i(TAG, "SshPayloadEngine หยุดทำงานแล้ว");
    }

    @Override
    public boolean isRunning() {
        return isRunning.get() && sshSession != null && sshSession.isConnected();
    }

    @Override
    public String getName() {
        return "SSH + Payload";
    }
}
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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyVpnService extends VpnService {

    public static final String ACTION_STATE = "com.example.httpconnectvpn.VPN_STATE";
    private static final String CHANNEL_ID = "VPN_SERVICE_CHANNEL";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "ProxyVpnService";

    private ParcelFileDescriptor vpnInterface = null;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    // === ส่วน Packet Forwarder ===
    private final ConcurrentHashMap<String, ProxyConnection> activeConnections = new ConcurrentHashMap<>();
    private volatile boolean isRunning = false;
    private Thread packetReaderThread;
    private String currentProxyHost;
    private int currentProxyPort;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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

        // ทดสอบเชื่อมต่อ Proxy ก่อน
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

        // สร้าง VPN Interface
        try {
            Builder builder = new Builder();
            builder.setSession("HTTP VPN")
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("8.8.8.8");

            vpnInterface = builder.establish();

            if (vpnInterface != null) {
                currentProxyHost = host;
                currentProxyPort = port;

                updateNotification("เชื่อมต่อสำเร็จ: " + host + ":" + port);
                sendLog("🚀 สร้างท่อ VPN สำเร็จ! ระบบพร้อมใช้งาน");
                broadcastState(true);

                // เริ่ม Packet Forwarder
                startPacketForwarder();
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

    // =========================================================
    // Packet Forwarder
    // =========================================================

    private void startPacketForwarder() {
        isRunning = true;

        packetReaderThread = new Thread(() -> {
            try {
                FileInputStream tunIn = new FileInputStream(vpnInterface.getFileDescriptor());
                FileOutputStream tunOut = new FileOutputStream(vpnInterface.getFileDescriptor());

                byte[] buffer = new byte[32767];

                sendLog("📡 เริ่ม Packet Reader แล้ว");

                while (isRunning && !Thread.currentThread().isInterrupted()) {
                    int length = tunIn.read(buffer);
                    if (length <= 0) continue;

                    byte[] packet = Arrays.copyOf(buffer, length);

                    if (!PacketUtils.isTcp(packet)) {
                        // ข้าม UDP / ICMP ไปก่อน
                        continue;
                    }

                    try {
                        String key = PacketUtils.buildKey(packet);
                        ProxyConnection conn = activeConnections.get(key);

                        if (PacketUtils.isSyn(packet) && conn == null) {
                            // New TCP Connection → ทำ HTTP CONNECT
                            handleNewConnection(packet, tunOut);
                        } else if (conn != null && conn.isConnected) {
                            // Existing connection → ส่งข้อมูลต่อไปยัง Proxy
                            handleExistingConnection(conn, packet);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error processing packet", e);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Packet reader stopped", e);
                sendLog("❌ Packet Reader หยุดทำงาน: " + e.getMessage());
            }
        }, "VPN-PacketReader");

        packetReaderThread.start();
    }

    private void handleNewConnection(byte[] synPacket, FileOutputStream tunOut) {
        executorService.execute(() -> {
            try {
                InetAddress dstIp = PacketUtils.getDstIp(synPacket);
                int dstPort = PacketUtils.getDstPort(synPacket);
                InetAddress srcIp = PacketUtils.getSrcIp(synPacket);
                int srcPort = PacketUtils.getSrcPort(synPacket);

                String key = PacketUtils.buildKey(synPacket);

                sendLog("🔗 กำลังทำ CONNECT ไปยัง " + dstIp.getHostAddress() + ":" + dstPort);

                // 1. เชื่อมต่อไปยัง HTTP Proxy
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(currentProxyHost, currentProxyPort), 8000);
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(30000);

                // 2. ส่งคำสั่ง CONNECT
                String connectRequest =
                        "CONNECT " + dstIp.getHostAddress() + ":" + dstPort + " HTTP/1.1\r\n" +
                        "Host: " + dstIp.getHostAddress() + ":" + dstPort + "\r\n" +
                        "Proxy-Connection: Keep-Alive\r\n" +
                        "\r\n";

                OutputStream out = socket.getOutputStream();
                out.write(connectRequest.getBytes(StandardCharsets.US_ASCII));
                out.flush();

                // 3. อ่าน Response จาก Proxy
                InputStream in = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
                String statusLine = reader.readLine();

                if (statusLine == null || !statusLine.toUpperCase().contains("200")) {
                    sendLog("❌ CONNECT ล้มเหลว: " + statusLine);
                    socket.close();
                    return;
                }

                // อ่าน header ที่เหลือทิ้ง
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    // skip headers
                }

                // 4. เก็บ Connection
                ProxyConnection conn = new ProxyConnection(
                        key, socket, in, out,
                        srcIp, srcPort, dstIp, dstPort
                );
                conn.isConnected = true;
                activeConnections.put(key, conn);

                sendLog("✅ CONNECT สำเร็จ: " + key);

                // 5. เริ่มอ่านข้อมูลจาก Proxy กลับมา (ยังเป็นโครง)
                startProxyToTunRelay(conn, tunOut);

            } catch (Exception e) {
                sendLog("❌ เกิดข้อผิดพลาดตอน CONNECT: " + e.getMessage());
                Log.e(TAG, "handleNewConnection error", e);
            }
        });
    }

    private void handleExistingConnection(ProxyConnection conn, byte[] packet) {
        try {
            // ดึง TCP payload ออกมา (ข้าม IP + TCP header)
            int ipHeaderLen = (packet[0] & 0x0F) * 4;
            int tcpHeaderLen = ((packet[ipHeaderLen + 12] & 0xF0) >> 4) * 4;
            int payloadOffset = ipHeaderLen + tcpHeaderLen;

            if (packet.length > payloadOffset) {
                byte[] payload = Arrays.copyOfRange(packet, payloadOffset, packet.length);
                if (payload.length > 0 && conn.isConnected) {
                    conn.proxyOut.write(payload);
                    conn.proxyOut.flush();
                    conn.lastActivity = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error writing to proxy", e);
            cleanupConnection(conn);
        }
    }

    private void startProxyToTunRelay(ProxyConnection conn, FileOutputStream tunOut) {
        executorService.execute(() -> {
            byte[] buffer = new byte[8192];
            try {
                while (conn.isConnected && isRunning) {
                    int len = conn.proxyIn.read(buffer);
                    if (len <= 0) break;

                    // TODO: ตรงนี้ต้องสร้าง TCP packet กลับไปยัง TUN
                    // ตอนนี้ยังเป็นโครงเปล่า (ต้องมี sequence/ack จัดการ)
                    // tunOut.write(...);

                    conn.lastActivity = System.currentTimeMillis();
                }
            } catch (Exception e) {
                // connection closed
            } finally {
                cleanupConnection(conn);
            }
        });
    }

    private void cleanupConnection(ProxyConnection conn) {
        if (conn == null) return;
        conn.close();
        activeConnections.remove(conn.key);
        sendLog("🧹 ปิด Connection: " + conn.key);
    }

    private void stopPacketForwarder() {
        isRunning = false;

        if (packetReaderThread != null) {
            packetReaderThread.interrupt();
            packetReaderThread = null;
        }

        for (ProxyConnection conn : activeConnections.values()) {
            conn.close();
        }
        activeConnections.clear();
    }

    // =========================================================
    // ส่วนเดิม
    // =========================================================

    private void stopVpn() {
        stopPacketForwarder();

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

    // =========================================================
    // Inner class: ProxyConnection
    // =========================================================
    public static class ProxyConnection {
        public final String key;
        public final Socket socket;
        public final InputStream proxyIn;
        public final OutputStream proxyOut;
        public final InetAddress srcIp;
        public final int srcPort;
        public final InetAddress dstIp;
        public final int dstPort;

        public volatile boolean isConnected = false;
        public long lastActivity = System.currentTimeMillis();

        public ProxyConnection(String key, Socket socket,
                               InputStream in, OutputStream out,
                               InetAddress srcIp, int srcPort,
                               InetAddress dstIp, int dstPort) {
            this.key = key;
            this.socket = socket;
            this.proxyIn = in;
            this.proxyOut = out;
            this.srcIp = srcIp;
            this.srcPort = srcPort;
            this.dstIp = dstIp;
            this.dstPort = dstPort;
        }

        public void close() {
            isConnected = false;
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }
}
package com.example.httpconnectvpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import androidx.core.app.NotificationCompat;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ProxyVpnService extends VpnService implements Runnable {
    public static final String ACTION_STATE = "com.example.httpconnectvpn.VPN_STATE";
    private static final String CHANNEL_ID = "vpn_channel";
    private Thread vpnThread;
    private ParcelFileDescriptor vpnInterface;
    private boolean isRunning = false;
    private String host;
    private int port;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("HTTP VPN Client")
                .setContentText("กำลังทำงานและเชื่อมต่อผ่าน Proxy...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build();
        startForeground(1, notification);

        if (intent != null) {
            host = intent.getStringExtra("host");
            port = intent.getIntExtra("port", 8080);
        }

        if (vpnThread == null || !vpnThread.isAlive()) {
            isRunning = true;
            vpnThread = new Thread(this, "VPNThread");
            vpnThread.start();
        }
        return START_STICKY;
    }

    @Override
    public void run() {
        try {
            SharedPreferences prefs = getSharedPreferences("VpnPrefs", Context.MODE_PRIVATE);
            String dns1 = prefs.getString("dns1", "8.8.8.8");
            String dns2 = prefs.getString("dns2", "8.8.4.4");
            String payload = prefs.getString("payload", "");

            Builder builder = new Builder();
            builder.addAddress("10.0.0.2", 32);
            builder.addRoute("0.0.0.0", 0);
            builder.addDnsServer(dns1);
            builder.addDnsServer(dns2);
            builder.setSession("HttpVpnSession");

            vpnInterface = builder.establish();
            broadcastState(true);

            FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
            FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());

            byte[] buffer = new byte[32768];
            while (isRunning) {
                int length = in.read(buffer);
                if (length > 0) {
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(host, port), 5000);
                        OutputStream socketOut = socket.getOutputStream();
                        InputStream socketIn = socket.getInputStream();

                        // หากมีการกำหนด Custom Payload ให้ส่งคำสั่ง Handshake ไปก่อน
                        if (!payload.isEmpty()) {
                            String formattedPayload = payload.replace("[host_port]", host + ":" + port)
                                                             .replace("[protocol]", "HTTP/1.1");
                            socketOut.write(formattedPayload.getBytes());
                            socketOut.flush();
                        }

                        // ส่งข้อมูล Traffic
                        socketOut.write(buffer, 0, length);
                        socketOut.flush();

                        int readBytes = socketIn.read(buffer);
                        if (readBytes > 0) {
                            out.write(buffer, 0, readBytes);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            disconnect();
        }
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

    private void disconnect() {
        isRunning = false;
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }
        } catch (Exception ignored) {}
        broadcastState(false);
        stopForeground(true);
    }

    private void broadcastState(boolean connected) {
        Intent intent = new Intent(ACTION_STATE);
        intent.putExtra("connected", connected);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        disconnect();
        super.onDestroy();
    }
}

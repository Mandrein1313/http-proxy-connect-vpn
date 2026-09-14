package com.example.httpconnectvpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ProxyVpnService extends VpnService implements Runnable {
    public static final String ACTION_STATE = "com.example.httpconnectvpn.VPN_STATE";
    private Thread vpnThread;
    private ParcelFileDescriptor vpnInterface;
    private boolean isRunning = false;
    private String host;
    private int port;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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
            // 1. ตั้งค่า VPN Interface
            Builder builder = new Builder();
            builder.addAddress("10.0.0.2", 32);
            builder.addRoute("0.0.0.0", 0);
            builder.setSession("HttpVpnSession");

            vpnInterface = builder.establish();
            broadcastState(true);

            FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
            FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());

            byte[] buffer = new byte[32768];
            while (isRunning) {
                int length = in.read(buffer);
                if (length > 0) {
                    // ส่ง Traffic ต่อไปยัง Proxy Server (ทำ Socket Bridge)
                    try (Socket socket = new Socket(host, port)) {
                        OutputStream socketOut = socket.getOutputStream();
                        socketOut.write(buffer, 0, length);
                        socketOut.flush();
                        
                        InputStream socketIn = socket.getInputStream();
                        int readBytes = socketIn.read(buffer);
                        if (readBytes > 0) {
                            out.write(buffer, 0, readBytes);
                        }
                    } catch (Exception e) {
                        // Handle socket connection errors
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            disconnect();
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

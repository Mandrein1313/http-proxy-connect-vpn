package com.example.httpconnectvpn;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class ProxyVpnService extends VpnService {

    public static final String ACTION_STATE = "com.example.httpconnectvpn.VPN_STATE";
    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private Session jschSession;
    private boolean isRunning = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }

        startVpn();
        return START_STICKY;
    }

    private void sendLog(String msg) {
        Intent intent = new Intent(MainActivity.ACTION_LOG);
        intent.putExtra("message", msg);
        sendBroadcast(intent);
    }

    private void startVpn() {
        if (isRunning) return;
        isRunning = true;

        vpnThread = new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("VpnPrefs", MODE_PRIVATE);
                String sshHost = prefs.getString("ssh_host", "");
                int sshPort = prefs.getInt("ssh_port", 22);
                String sshUser = prefs.getString("ssh_user", "");
                String sshPass = prefs.getString("ssh_pass", "");
                String dns1 = prefs.getString("dns1", "8.8.8.8");
                String dns2 = prefs.getString("dns2", "8.8.4.4");

                // 1. ตั้งค่า VPN Interface
                Builder builder = new Builder();
                builder.setSession("HTTP Proxy VPN")
                        .addAddress("10.0.0.2", 24)
                        .addRoute("0.0.0.0", 0)
                        .addDnsServer(dns1)
                        .addDnsServer(dns2);

                vpnInterface = builder.establish();
                sendLog("[VPN] สร้าง VPN Interface สำเร็จ");

                // 2. ถ้ามีข้อมูล SSH ให้ทำการเชื่อมต่อ SSH Tunnel
                if (!sshHost.isEmpty() && !sshUser.isEmpty()) {
                    sendLog("[SSH] กำลังเชื่อมต่อ SSH ไปยัง " + sshHost + ":" + sshPort + "...");

                    JSch jsch = new JSch();
                    jschSession = jsch.getSession(sshUser, sshHost, sshPort);
                    jschSession.setPassword(sshPass);
                    jschSession.setConfig("StrictHostKeyChecking", "no");
                    jschSession.setTimeout(15000);
                    jschSession.connect();

                    // เปิด Dynamic Port Forwarding (SOCKS Proxy ภายในเครื่องที่ Port 1080)
                    jschSession.setPortForwardingL(1080, "127.0.0.1", 1080);
                    sendLog("[SSH] เชื่อมต่อ SSH สำเร็จ! (Tunnel Port: 1080)");
                } else {
                    sendLog("[SSH] ข้ามการเชื่อมต่อ SSH (ไม่ได้ระบุ Host/User)");
                }

                // บรอดแคสต์บอก UI ว่าเชื่อมต่อแล้ว
                Intent intent = new Intent(ACTION_STATE);
                intent.putExtra("connected", true);
                sendBroadcast(intent);

            } catch (Exception e) {
                sendLog("[Error] เกิดข้อผิดพลาด: " + e.getMessage());
                stopVpn();
            }
        });

        vpnThread.start();
    }

    private void stopVpn() {
        isRunning = false;
        try {
            if (jschSession != null && jschSession.isConnected()) {
                jschSession.disconnect();
                sendLog("[SSH] ตัดการเชื่อมต่อ SSH เรียบร้อย");
            }
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }
        } catch (Exception ignored) {}

        sendLog("[VPN] หยุดการทำงานเรียบร้อย");

        Intent intent = new Intent(ACTION_STATE);
        intent.putExtra("connected", false);
        sendBroadcast(intent);

        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}

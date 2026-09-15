package com.example.httpconnectvpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import java.io.IOException;

/**
 * Minimal VPN service scaffold. A production HTTP CONNECT implementation must
 * add a packet forwarder (TCP/IP stack) and an authenticated upstream relay.
 * This service intentionally does not add a default route, so it cannot blackhole traffic.
 */
public class ProxyVpnService extends VpnService {
    public static final String ACTION_STATE="com.example.httpconnectvpn.STATE";
    private ParcelFileDescriptor tun; private Thread worker;
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if (tun != null) return START_STICKY;
        String host=intent.getStringExtra("host"); int port=intent.getIntExtra("port",8080);
        try { tun=new Builder().setSession("AetherLink").setMtu(1500).addAddress("10.0.0.2",32).establish();
            broadcast(true,host,port); worker=new Thread(() -> { try { if(tun!=null) tun.getFileDescriptor().sync(); } catch(Exception ignored) {} }); worker.start();
        } catch (Exception e) { stopSelf(); }
        return START_STICKY;
    }
    private void broadcast(boolean state,String host,int port) { sendBroadcast(new Intent(ACTION_STATE).putExtra("connected",state).putExtra("host",host).putExtra("port",port)); }
    @Override public void onDestroy() { if(worker!=null) worker.interrupt(); if(tun!=null) try{tun.close();}catch(IOException ignored){}; broadcast(false,"",0); super.onDestroy(); }
    @Override public void onRevoke() { stopSelf(); super.onRevoke(); }
}

package com.example.httpconnectvpn;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class ProxyConnection {
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
        try { socket.close(); } catch (Exception ignored) {}
    }
}
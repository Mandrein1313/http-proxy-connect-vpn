package com.example.httpconnectvpn.model;

public class V2RayConfig {
    public String protocol = "vmess"; // vmess, vless, trojan, shadowsocks
    public String address;
    public int port = 443;
    public String id; // UUID สำหรับ vmess/vless หรือ Password สำหรับ trojan
    public int alterId = 0;
    public String security = "auto"; // auto, aes-128-gcm, chacha20-poly1305, none
    public String network = "ws"; // tcp, ws, grpc
    public String path = "/";
    public String host = "";
    public String sni = "";
    public boolean tls = true;
    public int localSocksPort = 10808;

    public V2RayConfig() {}
}

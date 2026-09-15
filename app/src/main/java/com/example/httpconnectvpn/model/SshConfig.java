package com.example.httpconnectvpn.model;

public class SshConfig {
    public String host;
    public int port = 22;
    public String username;
    public String password;

    // Optional
    public String payload = "";
    public String sni = "";
    public boolean useSsl = false;
    public boolean useWebsocket = false;
    public int localSocksPort = 1080;

    public SshConfig() {}

    public SshConfig(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }
}
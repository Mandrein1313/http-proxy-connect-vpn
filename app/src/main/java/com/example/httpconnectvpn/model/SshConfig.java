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

    // ชื่อ Network Profile (เช่น AIS Free Net, True Unlim, DTAC)
    public String profileName = "General Profile";

    public SshConfig() {}

    public SshConfig(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }
}

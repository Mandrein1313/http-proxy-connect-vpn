package com.example.httpconnectvpn;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class PacketUtils {

    public static boolean isTcp(byte[] packet) {
        if (packet == null || packet.length < 20) return false;
        return (packet[9] & 0xFF) == 6; // TCP
    }

    public static boolean isSyn(byte[] packet) {
        if (packet.length < 40) return false;
        int ipHeaderLen = (packet[0] & 0x0F) * 4;
        if (packet.length < ipHeaderLen + 14) return false;
        int flags = packet[ipHeaderLen + 13] & 0xFF;
        return (flags & 0x02) != 0; // SYN
    }

    public static InetAddress getSrcIp(byte[] packet) throws UnknownHostException {
        return InetAddress.getByAddress(Arrays.copyOfRange(packet, 12, 16));
    }

    public static InetAddress getDstIp(byte[] packet) throws UnknownHostException {
        return InetAddress.getByAddress(Arrays.copyOfRange(packet, 16, 20));
    }

    public static int getSrcPort(byte[] packet) {
        int ipHeaderLen = (packet[0] & 0x0F) * 4;
        return ((packet[ipHeaderLen] & 0xFF) << 8) | (packet[ipHeaderLen + 1] & 0xFF);
    }

    public static int getDstPort(byte[] packet) {
        int ipHeaderLen = (packet[0] & 0x0F) * 4;
        return ((packet[ipHeaderLen + 2] & 0xFF) << 8) | (packet[ipHeaderLen + 3] & 0xFF);
    }

    public static String buildKey(byte[] packet) throws UnknownHostException {
        return getSrcIp(packet).getHostAddress() + ":" + getSrcPort(packet) + "-" +
               getDstIp(packet).getHostAddress() + ":" + getDstPort(packet);
    }
}
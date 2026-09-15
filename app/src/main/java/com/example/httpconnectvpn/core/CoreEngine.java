package com.example.httpconnectvpn.core;

import android.content.Context;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

public interface CoreEngine {

    /**
     * เริ่มต้น Engine
     * @param context Context ของแอป
     * @param vpnService ตัว VpnService (ใช้ protect socket ได้)
     * @param tunFd FileDescriptor ของ TUN interface
     * @param config ข้อมูล config (SSH / V2Ray ฯลฯ)
     */
    void start(Context context, VpnService vpnService, ParcelFileDescriptor tunFd, Object config) throws Exception;

    /**
     * หยุด Engine
     */
    void stop();

    /**
     * ตรวจสอบว่า Engine กำลังทำงานอยู่หรือไม่
     */
    boolean isRunning();

    /**
     * ชื่อ Engine (ใช้แสดงใน Log / UI)
     */
    String getName();
}
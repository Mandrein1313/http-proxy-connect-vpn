# AetherLink — HTTP Connect VPN UI (Java/XML)

โปรเจกต์ตัวอย่าง Android ที่ทำหน้าจอโทนมืดตามภาพอ้างอิง ใช้ Java + XML และมี `VpnService` scaffold สำหรับขอสิทธิ์ VPN, แสดงสถานะ, กรอก HTTP proxy host/port และเตรียมต่อยอด packet forwarder

## สำคัญก่อนใช้งานจริง

โค้ดชุดนี้เป็น **UI + VPN interface scaffold** ไม่ใช่ HTTP CONNECT tunnel ที่สมบูรณ์ เพราะ Android `VpnService` ไม่ได้แปลงแพ็กเก็ต IP เป็น HTTP proxy ให้อัตโนมัติ การใช้งานจริงต้องเพิ่ม packet parser/TCP-IP stack, การเชื่อมต่อ upstream proxy, DNS handling, lifecycle/foreground notification, และการทดสอบด้านความปลอดภัยก่อนเผยแพร่ แอปตัวอย่างจึงไม่เพิ่ม default route เพื่อไม่ให้ทราฟฟิกของเครื่องถูก blackhole

## เปิดใน Android Studio

เปิดโฟลเดอร์นี้ด้วย Android Studio รุ่นที่รองรับ Android Gradle Plugin 8.6 และ JDK 17 จากนั้นกด Run บนอุปกรณ์ Android 6.0 ขึ้นไป เมื่อกดเชื่อมต่อครั้งแรก ระบบจะขอสิทธิ์ VPN

## GitHub Actions

ไฟล์ `.github/workflows/android.yml` จะ build `assembleDebug` และอัปโหลด artifact ชื่อ `AetherLink-debug-apk` ทุกครั้งที่ push ไป `main`/`master` หรือเปิด workflow เอง

## Build ในเครื่อง

```bash
gradle assembleDebug
```

ผลลัพธ์อยู่ที่ `app/build/outputs/apk/debug/app-debug.apk`

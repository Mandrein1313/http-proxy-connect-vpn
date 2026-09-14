package com.example.httpconnectvpn;

import android.app.Activity;
import android.content.*;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends Activity {
    private static final int VPN_REQUEST = 1001;
    private Button connectButton; private TextView statusText, proxyText;
    private EditText hostInput, portInput; private boolean connected = false;
    private final BroadcastReceiver receiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) {
        connected = i.getBooleanExtra("connected", false); updateUi();
    }};

    @Override public void onCreate(Bundle state) { 
        super.onCreate(state); 
        setContentView(R.layout.activity_main);

        View mainView = findViewById(R.id.mainRoot);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                
                v.setPadding(
                    v.getPaddingLeft(),
                    statusBarHeight,
                    v.getPaddingRight(),
                    navBarHeight + 16
                );
                return insets;
            });
        }

        connectButton=findViewById(R.id.connectButton); statusText=findViewById(R.id.statusText); proxyText=findViewById(R.id.proxyText);
        hostInput=findViewById(R.id.hostInput); portInput=findViewById(R.id.portInput);
        registerReceiver(receiver, new IntentFilter(ProxyVpnService.ACTION_STATE), RECEIVER_NOT_EXPORTED);
        connectButton.setOnClickListener(v -> toggleVpn()); updateUi();
    }
    
    private void toggleVpn() { if (connected) { stopService(new Intent(this, ProxyVpnService.class)); connected=false; updateUi(); return; }
        String host=hostInput.getText().toString().trim(); int port; try { port=Integer.parseInt(portInput.getText().toString().trim()); } catch(Exception e) { port=8080; }
        if (host.isEmpty() || port < 1 || port > 65535) { Toast.makeText(this,"กรุณาตรวจสอบ Proxy host และ port",Toast.LENGTH_SHORT).show(); return; }
        Intent prepare=VpnService.prepare(this); if (prepare != null) startActivityForResult(prepare, VPN_REQUEST); else startVpn(host,port);
    }
    @Override protected void onActivityResult(int r,int result,Intent data) { super.onActivityResult(r,result,data); if(r==VPN_REQUEST && result==RESULT_OK) startVpn(hostInput.getText().toString().trim(), Integer.parseInt(portInput.getText().toString().trim())); }
    private void startVpn(String host,int port) { Intent i=new Intent(this,ProxyVpnService.class).putExtra("host",host).putExtra("port",port); startService(i); connected=true; updateUi(); }
    private void updateUi() { connectButton.setText(connected?"ตัดการเชื่อมต่อ":"เชื่อมต่อ"); statusText.setText(connected?"กำลังเชื่อมต่อ":"ยังไม่เชื่อม"); proxyText.setText(hostInput.getText().toString()+":"+portInput.getText().toString()+" · HTTP"); }
    @Override protected void onDestroy() { unregisterReceiver(receiver); super.onDestroy(); }
}

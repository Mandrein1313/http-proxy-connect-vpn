package com.example.httpconnectvpn;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public class SettingsActivity extends AppCompatActivity {

    private EditText payloadInput, sniInput, dns1Input, dns2Input;
    private Button btnSave;
    private MaterialToolbar toolbar;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("VpnPrefs", Context.MODE_PRIVATE);

        toolbar = findViewById(R.id.toolbar);
        payloadInput = findViewById(R.id.payloadInput);
        sniInput = findViewById(R.id.sniInput);
        dns1Input = findViewById(R.id.dns1Input);
        dns2Input = findViewById(R.id.dns2Input);
        btnSave = findViewById(R.id.btnSave);

        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        // ดึงค่าเก่ามาแสดง
        payloadInput.setText(prefs.getString("payload", "CONNECT [host_port] [protocol]"));
        sniInput.setText(prefs.getString("sni", "m.facebook.com"));
        dns1Input.setText(prefs.getString("dns1", "8.8.8.8"));
        dns2Input.setText(prefs.getString("dns2", "8.8.4.4"));

        // กดบันทึก
        btnSave.setOnClickListener(v -> {
            prefs.edit()
                    .putString("payload", payloadInput.getText().toString().trim())
                    .putString("sni", sniInput.getText().toString().trim())
                    .putString("dns1", dns1Input.getText().toString().trim())
                    .putString("dns2", dns2Input.getText().toString().trim())
                    .apply();

            Toast.makeText(this, "บันทึกการตั้งค่าเรียบร้อยแล้ว", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}

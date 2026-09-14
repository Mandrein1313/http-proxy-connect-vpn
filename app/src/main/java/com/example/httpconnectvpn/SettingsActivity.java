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

    private EditText payloadInput, sniInput, dnsPrimary, dnsSecondary;
    private Button saveButton;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("VpnPrefs", Context.MODE_PRIVATE);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        payloadInput = findViewById(R.id.payloadInput);
        sniInput = findViewById(R.id.sniInput);
        dnsPrimary = findViewById(R.id.dnsPrimary);
        dnsSecondary = findViewById(R.id.dnsSecondary);
        saveButton = findViewById(R.id.saveButton);

        loadSettings();

        saveButton.setOnClickListener(v -> saveSettings());
    }

    private void loadSettings() {
        payloadInput.setText(prefs.getString("payload", "CONNECT [host_port] [protocol]\r\n\r\n"));
        sniInput.setText(prefs.getString("sni", ""));
        dnsPrimary.setText(prefs.getString("dns1", "8.8.8.8"));
        dnsSecondary.setText(prefs.getString("dns2", "8.8.4.4"));
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("payload", payloadInput.getText().toString());
        editor.putString("sni", sniInput.getText().toString());
        editor.putString("dns1", dnsPrimary.getText().toString());
        editor.putString("dns2", dnsSecondary.getText().toString());
        editor.apply();

        Toast.makeText(this, "บันทึกการตั้งค่าเรียบร้อยแล้ว", Toast.LENGTH_SHORT).show();
        finish();
    }
}

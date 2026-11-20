package com.khant.livetranslator;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 60, 50, 60);

        TextView title = new TextView(this);
        title.setText("Live Caption Translator");
        title.setTextSize(22);
        layout.addView(title);

        TextView langLabel = new TextView(this);
        langLabel.setText("\nဘာသာပြန်မည့် ဘာသာစကား ရွေးပါ:");
        layout.addView(langLabel);

        RadioGroup langGroup = new RadioGroup(this);
        RadioButton rbMy = new RadioButton(this);
        rbMy.setText("မြန်မာ (Myanmar)");
        rbMy.setId(1);
        rbMy.setChecked(true);
        RadioButton rbEn = new RadioButton(this);
        rbEn.setText("အင်္ဂလိပ် (English)");
        rbEn.setId(2);
        langGroup.addView(rbMy);
        langGroup.addView(rbEn);
        layout.addView(langGroup);

        TextView styleLabel = new TextView(this);
        styleLabel.setText("\nဘာသာပြန်ပုံစံ ရွေးပါ:");
        layout.addView(styleLabel);

        RadioGroup styleGroup = new RadioGroup(this);
        RadioButton rbNormal = new RadioButton(this);
        rbNormal.setText("ယဉ်ကျေး/ပုံမှန် (Normal)");
        rbNormal.setId(1);
        RadioButton rbSlang = new RadioButton(this);
        rbSlang.setText("ခပ်ကြမ်းကြမ်း/ရိုင်း (Slang)");
        rbSlang.setId(2);
        rbSlang.setChecked(true);
        styleGroup.addView(rbNormal);
        styleGroup.addView(rbSlang);
        layout.addView(styleGroup);

        Button startBtn = new Button(this);
        startBtn.setText("Floating Translator ဖွင့်မည်");
        startBtn.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, "Display over other apps ကို ဖွင့်ပေးပါ", Toast.LENGTH_LONG).show();
            } else {
                String lang = (langGroup.getCheckedRadioButtonId() == 1) ? "my" : "en";
                boolean isSlang = (styleGroup.getCheckedRadioButtonId() == 2);
                Intent serviceIntent = new Intent(this, FloatingService.class);
                serviceIntent.putExtra("lang", lang);
                serviceIntent.putExtra("slang", isSlang);
                startService(serviceIntent);
                Toast.makeText(this, "Floating Translator စတင်ပါပြီ", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        layout.addView(startBtn);

        Button stopBtn = new Button(this);
        stopBtn.setText("ပိတ်မည် (Stop)");
        stopBtn.setOnClickListener(v -> {
            stopService(new Intent(this, FloatingService.class));
            Toast.makeText(this, "ပိတ်လိုက်ပါပြီ", Toast.LENGTH_SHORT).show();
        });
        layout.addView(stopBtn);

        setContentView(layout);
    }
}

package com.mrcdrnzz.dailytracker;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.TextView;

public class PermissionsRationaleActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView text = new TextView(this);
        text.setText("Daily Tracker uses Health Connect only to display health information you choose to share, such as sleep, heart rate and active calories. Your health data stays on your device and is not uploaded by this app.");
        text.setTextColor(Color.WHITE);
        text.setTextSize(18);
        text.setPadding(48,48,48,48);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setBackgroundColor(Color.rgb(7,16,24));
        setContentView(text);
    }
}

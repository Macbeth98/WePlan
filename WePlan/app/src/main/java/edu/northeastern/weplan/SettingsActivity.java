package edu.northeastern.weplan;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

       LinearLayout layout = findViewById(R.id.settings_layout);

       TextView notificationSetting = new TextView(this);
       notificationSetting.setText("Enable Notifications");
       layout.addView(notificationSetting);

       Switch notificationSwitch = new Switch(this);
       layout.addView(notificationSwitch);

       Switch themeSwitch = new Switch(this);
       themeSwitch.setText("Dark Mode");
       themeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
           @Override
           public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
               // Will Add code to change the theme of the app
           }
       });
       layout.addView(themeSwitch);
       Spinner languageSpinner = new Spinner(this);
       String[] languages = {"English", "Spanish", "French", "German"};
       ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, languages);
       spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
       languageSpinner.setAdapter(spinnerAdapter);
       layout.addView(languageSpinner);
    }
}
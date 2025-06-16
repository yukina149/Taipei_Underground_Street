package com.example.cameraproject_2;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.Log;

import java.util.Locale;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        setLocale();
    }

    public void setLocale() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String language = prefs.getString("language", "zh");
        Log.d("MyApplication", "Setting locale to: " + language);

        Locale locale;
        switch (language) {
            case "en":
                locale = new Locale("en");
                break;
            case "ja":
                locale = new Locale("ja");
                break;
            default:
                locale = new Locale("zh");
                break;
        }

        Locale.setDefault(locale);
        Configuration config = new Configuration(getResources().getConfiguration());
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        Log.d("MyApplication", "Locale set to: " + locale.getLanguage() + ", current locale: " + getResources().getConfiguration().getLocales().get(0).getLanguage());
    }
}

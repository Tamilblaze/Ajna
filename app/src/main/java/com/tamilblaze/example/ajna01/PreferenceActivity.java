package com.tamilblaze.example.ajna01;

import android.os.Bundle;

/**
 * Created by tjaya on 9/26/2015.
 */
public class PreferenceActivity extends android.preference.PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
    }
}

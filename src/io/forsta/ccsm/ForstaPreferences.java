package io.forsta.ccsm;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Created by jlewis on 1/6/17.
 */

public class ForstaPreferences {
    private  static final String API_KEY                    = "api_key";
    private  static final String CCSM_DEBUG                    = "ccsm_debug";

    public static boolean isRegisteredForsta(Context context) {
        String key = ForstaPreferences.getStringPreference(context, API_KEY);
        if (key !=  "") {
            return true;
        }
        return false;
    }

    public static void setRegisteredForsta(Context context, String value) {
        setStringPreference(context, API_KEY, value);
    }

    public static String getRegisteredKey(Context context) {
        return getStringPreference(context, API_KEY);
    }

    public static void setCCSMDebug(Context context, boolean value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(CCSM_DEBUG, value);
    }

    public static boolean isCCSMDebug(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(CCSM_DEBUG, false);
    }

    private static void setStringPreference(Context context, String key, String value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(key, value).apply();
    }

    private static String getStringPreference(Context context, String key) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(key, "");
    }

}

package io.forsta.ccsm;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import org.json.JSONException;
import org.json.JSONObject;
import org.forsta.securesms.util.Base64;
import java.io.IOException;
import java.util.Date;

/**
 * Created by jlewis on 1/6/17.
 */

public class ForstaPreferences {
    private static final String API_KEY = "api_key";
    private static final String API_LAST_LOGIN = "last_login";
    private static final String FORSTA_SYNC_NUMBER = "forsta_sync_number";
    private static final String CCSM_DEBUG = "ccsm_debug";
    private static final String FORSTA_API_HOST = "forsta_api_url";

    public static void clearPreferences(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(API_KEY, "").putString(API_LAST_LOGIN, "").putBoolean(CCSM_DEBUG, false).apply();
    }

    public static boolean isRegisteredForsta(Context context) {
        return ForstaPreferences.getRegisteredKey(context) != "";
    }

    public static void setRegisteredForsta(Context context, String value) {
        setStringPreference(context, API_KEY, value);
    }

    public static String getRegisteredKey(Context context) {
        return getStringPreference(context, API_KEY);
    }

    public static Date getTokenExpireDate(Context context) {
        Date expireDate = new Date();
        String token = getStringPreference(context, API_KEY);
        String[] tokenParts = token.split("\\.");
        if (tokenParts.length == 3) {
            try {
                byte[] payload = Base64.decodeWithoutPadding(tokenParts[1]);
                String payloadString = new String(payload, "UTF-8");
                JSONObject obj = new JSONObject(payloadString);
                if (obj.has("exp")) {
                    int expire = obj.getInt("exp");
                    long expireTime = (long) expire * 1000;
                    expireDate = new Date(expireTime);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return expireDate;
    }

    public static void setCCSMDebug(Context context, boolean value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(CCSM_DEBUG, value).apply();
    }

    public static boolean isCCSMDebug(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(CCSM_DEBUG, false);
    }

    public static String getRegisteredDateTime(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(API_LAST_LOGIN, "");
    }

    public static void setRegisteredDateTime(Context context, String lastLogin) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(API_LAST_LOGIN, lastLogin).apply();
    }

    public static void setForstaSyncNumber(Context context, String number) {
        setStringPreference(context, FORSTA_SYNC_NUMBER, number);
    }

    public static String getForstaSyncNumber(Context context) {
        return getStringPreference(context, FORSTA_SYNC_NUMBER);
    }

    public static void setForstaApiHost(Context context, String host) {
        setStringPreference(context, FORSTA_API_HOST, host);
    }

    public static String getForstaApiHost(Context context) {
        return getStringPreference(context, FORSTA_API_HOST);
    }

    private static void setStringPreference(Context context, String key, String value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(key, value).apply();
    }

    private static String getStringPreference(Context context, String key) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(key, "");
    }

}

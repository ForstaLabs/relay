package io.forsta.ccsm;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.ArrayMap;
import android.util.Pair;

import com.h6ah4i.android.compat.utils.SharedPreferencesJsonStringSetWrapperUtils;

import org.json.JSONException;
import org.json.JSONObject;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.api.ForstaJWT;
import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.util.Base64;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Created by jlewis on 1/6/17.
 */

public class ForstaPreferences {
  private static final String API_KEY = "api_key";
  private static final String API_LAST_LOGIN = "last_login";
  private static final String FORSTA_SYNC_NUMBER = "forsta_sync_number";
  private static final String FORSTA_API_HOST = "forsta_api_url";
  private static final String FORSTA_LOGIN_PENDING = "forsta_login_pending";
  private static final String FORSTA_ORG_NAME = "forsta_org_name";
  private static final String FORSTA_USER_NAME = "forsta_user_name";
  private static final String FORSTA_CONTACT_SYNC = "forsta_contact_sync_time";
  private static final String FORSTA_USER = "forsta_user";
  private static final String CCSM_DEBUG = "ccsm_debug";

  private static final Pair<String, String> CONFIG_PROD = new Pair("https://ccsm-api.forsta.io", "+17017328733");
  private static final Pair<String, String> CONFIG_STAGE = new Pair("https://ccsm-stage-api.forsta.io", "+17017328732");
  private static final Pair<String, String> CONFIG_DEV = new Pair("https://ccsm-dev-api.forsta.io", "+17017328731");

  public static void clearPreferences(Context context) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().putString(API_KEY, "")
        .putString(API_LAST_LOGIN, "")
        .putString(FORSTA_SYNC_NUMBER, "")
        .putString(FORSTA_API_HOST, "")
        .putBoolean(FORSTA_LOGIN_PENDING, false)
        .putString(FORSTA_ORG_NAME, "")
        .putString(FORSTA_USER_NAME, "")
        .putString(FORSTA_USER, "")
        .putBoolean(CCSM_DEBUG, false)
        .putLong(FORSTA_CONTACT_SYNC, -1l)
        .apply();
  }

  public static void clearLogin(Context context) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().putString(API_KEY, "")
        .putString(API_LAST_LOGIN, "")
        .putString(FORSTA_USER, "")
        .putBoolean(FORSTA_LOGIN_PENDING, false)
        .apply();
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
    String token = getStringPreference(context, API_KEY);
    ForstaJWT jwt = new ForstaJWT(token);
    return jwt.getExpireDate();
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
    return PreferenceManager.getDefaultSharedPreferences(context).getString(FORSTA_SYNC_NUMBER, BuildConfig.FORSTA_SYNC_NUMBER);
  }

  public static void setForstaLoginPending(Context context, boolean pending) {
    setBooleanPreference(context, FORSTA_LOGIN_PENDING, pending);
  }

  public static boolean getForstaLoginPending(Context context) {
    return getBooleanPreference(context, FORSTA_LOGIN_PENDING);
  }

  public static void setForstaBuild(Context context, String build) {
    if (build.equals("dev")) {
      setForstaApiHost(context, CONFIG_DEV.first);
      setForstaSyncNumber(context, CONFIG_DEV.second);
    } else if (build.equals("stage")) {
      setForstaApiHost(context, CONFIG_STAGE.first);
      setForstaSyncNumber(context, CONFIG_STAGE.second);
    } else {
      setForstaApiHost(context, CONFIG_PROD.first);
      setForstaSyncNumber(context, CONFIG_PROD.second);
    }
  }

  public static Pair<String, String> getForstaBuild(Context context) {
    Pair<String, String> config = new Pair<>(getForstaApiHost(context), getForstaSyncNumber(context));
    return config;
  }

  public static void setForstaApiHost(Context context, String host) {
    setStringPreference(context, FORSTA_API_HOST, host);
  }

  public static String getForstaApiHost(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(FORSTA_API_HOST, BuildConfig.FORSTA_API_URL);
  }

  public static void setForstaUsername(Context context, String userName) {
    setStringPreference(context, FORSTA_USER_NAME, userName);
  }

  public static String getForstaUsername(Context context) {
    return getStringPreference(context, FORSTA_USER_NAME);
  }

  public static void setForstaOrgName(Context context, String orgName) {
    setStringPreference(context, FORSTA_ORG_NAME, orgName);
  }

  public static String getForstaOrgName(Context context) {
    return getStringPreference(context, FORSTA_ORG_NAME);
  }

  public static long getForstaContactSync(Context context) {
    return getLongPreference(context, FORSTA_CONTACT_SYNC);
  }

  public static void setForstaContactSync(Context context, long dateTime) {
    setLongPreference(context, FORSTA_CONTACT_SYNC, dateTime);
  }

  public static String getForstaUser(Context context) {
    return getStringPreference(context, FORSTA_USER);
  }

  public static void setForstaUser(Context context, String json) {
    setStringPreference(context, FORSTA_USER, json);
  }

  private static void setStringPreference(Context context, String key, String value) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().putString(key, value).apply();
  }

  private static String getStringPreference(Context context, String key) {
    return PreferenceManager.getDefaultSharedPreferences(context).getString(key, "");
  }

  private static void setBooleanPreference(Context context, String key, boolean value) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().putBoolean(key, value).apply();
  }

  private static boolean getBooleanPreference(Context context, String key) {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, false);
  }

  private static void setLongPreference(Context context, String key, long value) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().putLong(key, value).apply();
  }

  private static long getLongPreference(Context context, String key) {
    return PreferenceManager.getDefaultSharedPreferences(context).getLong(key, -1l);
  }
}

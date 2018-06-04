package io.forsta.ccsm;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import io.forsta.ccsm.api.model.ForstaJWT;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.securesms.util.TextSecurePreferences;

import java.util.Date;

/**
 * Created by jlewis on 1/6/17.
 */

public class ForstaPreferences {
  private static final String API_KEY = "api_key";
  private static final String API_LAST_LOGIN = "last_login";
  private static final String FORSTA_LOGIN_PENDING = "forsta_login_pending";
  private static final String FORSTA_ORG_NAME = "forsta_org_name";
  private static final String FORSTA_ORG = "forsta_org";
  private static final String FORSTA_USER_NAME = "forsta_user_name";
  private static final String FORSTA_CONTACT_SYNC = "forsta_contact_sync_time";
  private static final String FORSTA_USER = "forsta_user";
  private static final String CCSM_DEBUG = "ccsm_debug";
  private static final String FORSTA_OTR = "forsta_otr";

  public static void clearPreferences(Context context) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().putString(API_KEY, "")
        .putString(API_LAST_LOGIN, "")
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
        .putBoolean(FORSTA_LOGIN_PENDING, false)
        .apply();
  }

  public static boolean isRegisteredForsta(Context context) {
    return ForstaPreferences.getRegisteredKey(context) != "";
  }

  public static void setRegisteredForsta(Context context, String value) {
    setStringPreference(context, API_KEY, value);
    ForstaJWT jwt = new ForstaJWT(value);
    TextSecurePreferences.setLocalNumber(context, jwt.getUid());
  }

  public static String getRegisteredKey(Context context) {
    return getStringPreference(context, API_KEY);
  }

  public static Date getTokenExpireDate(Context context) {
    String token = getStringPreference(context, API_KEY);
    ForstaJWT jwt = new ForstaJWT(token);
    return jwt.getExpireDate();
  }

  public static String getUserId(Context context) {
    String token = getStringPreference(context, API_KEY);
    ForstaJWT jwt = new ForstaJWT(token);
    return jwt.getUid();
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

  public static void setForstaLoginPending(Context context, boolean pending) {
    setBooleanPreference(context, FORSTA_LOGIN_PENDING, pending);
  }

  public static boolean getForstaLoginPending(Context context) {
    return getBooleanPreference(context, FORSTA_LOGIN_PENDING);
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

  public static void setForstaOrg(Context context, String json) {
    setStringPreference(context, FORSTA_ORG, json);
  }

  public static String getForstaOrg(Context context) {
    return getStringPreference(context, FORSTA_ORG);
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

  public static boolean getOffTheRecord(Context context) {
    return getBooleanPreference(context, FORSTA_OTR);
  }

  public static void setOffTheRecord(Context context, boolean value) {
    setBooleanPreference(context, FORSTA_OTR, value);
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

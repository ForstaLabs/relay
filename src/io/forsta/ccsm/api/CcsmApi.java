package io.forsta.ccsm.api;

import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import io.forsta.ccsm.LoginActivity;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.ccsm.database.model.ForstaGroup;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.securesms.BuildConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.securesms.R;
import io.forsta.securesms.contacts.ContactsDatabase;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.database.TextSecureDirectory;
import io.forsta.securesms.groups.GroupManager;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.DirectoryHelper;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.Util;
import io.forsta.ccsm.util.NetworkUtils;

/**
 * Created by jlewis on 1/18/17.
 */

public class CcsmApi {
  private static final String TAG = CcsmApi.class.getSimpleName();
  private static final String API_URL = BuildConfig.FORSTA_API_URL;
  private static final String API_TOKEN_REFRESH = API_URL + "/v1/api-token-refresh/";
  private static final String API_LOGIN = API_URL + "/v1/login/";
  private static final String API_USER = API_URL + "/v1/user/";
  private static final String API_USER_PICK = API_URL + "/v1/user-pick/";
  private static final String API_TAG = API_URL + "/v1/tag/";
  private static final String API_USER_TAG = API_URL + "/v1/usertag/";
  private static final String API_ORG = API_URL + "/v1/org/";
  private static final String API_SEND_TOKEN = API_URL + "/v1/login/send/";
  private static final String API_AUTH_TOKEN = API_URL + "/v1/login/authtoken/";
  private static final long EXPIRE_REFRESH_DELTA = 7L;

  private CcsmApi() {
  }

  public static JSONObject forstaLogin(Context context, String username, String password, String authToken) {
    JSONObject result = new JSONObject();
    try {
      JSONObject obj = new JSONObject();
      if (!authToken.equals("")) {
//        String token = authToken.contains(":") ? authToken : username + ":" + authToken;
//        obj.put("authtoken", token);
        obj.put("authtoken", authToken);
        result = NetworkUtils.apiFetch(NetworkUtils.RequestMethod.POST, null, API_AUTH_TOKEN, obj);
      } else {
        obj.put("username", username);
        obj.put("password", password);
        result = NetworkUtils.apiFetch(NetworkUtils.RequestMethod.POST, null, API_LOGIN, obj);
      }

      if (result.has("token")) {
        Log.d(TAG, "Login Success. Token Received.");

        String token = result.getString("token");
        JSONObject user = result.getJSONObject("user");
        String lastLogin = user.getString("last_login");
        // Write token and last login to local preferences.
        ForstaPreferences.setRegisteredForsta(context, token);
        ForstaPreferences.setRegisteredDateTime(context, lastLogin);
        ForstaPreferences.setForstaLoginPending(context, false);
      }
    } catch (JSONException e) {
      e.printStackTrace();
      Log.d(TAG, "JSON Exception.");
    }
    return result;
  }

  // TODO Is there a reason to ever refresh the token.
  public static boolean tokenNeedsRefresh(Context context) {
    Date expireDate = ForstaPreferences.getTokenExpireDate(context);
    if (expireDate == null) {
      return false;
    }
    Date current = new Date();
    long expiresIn = (expireDate.getTime() - current.getTime()) / (1000 * 60 * 60 * 24);
    long expireDelta = EXPIRE_REFRESH_DELTA;
    boolean expired = expiresIn < expireDelta;

    Log.d(TAG, "Token expires in: " + expiresIn);
    return expired;
  }

  public static JSONObject forstaRefreshToken(Context context) {
    String authKey = ForstaPreferences.getRegisteredKey(context);
    JSONObject result = new JSONObject();
    try {
      JSONObject obj = new JSONObject();
      obj.put("token", ForstaPreferences.getRegisteredKey(context));

      result = NetworkUtils.apiFetch(NetworkUtils.RequestMethod.POST, authKey, API_TOKEN_REFRESH, obj);
      if (result.has("token")) {
        Log.d(TAG, "Token refresh. New token issued.");
        String token = result.getString("token");
        ForstaPreferences.setRegisteredForsta(context, token);
      } else {
        Log.d(TAG, "Token refresh failed.");
        ForstaPreferences.setRegisteredForsta(context, "");
      }
    } catch (Exception e) {
      e.printStackTrace();
      Log.d(TAG, "forstaRefreshToken failed");
    }
    return result;
  }

  public static JSONObject forstaSendToken(String org, String username) {
    JSONObject result = NetworkUtils.apiFetch(NetworkUtils.RequestMethod.GET, null, API_SEND_TOKEN + org + "/" + username + "/", null);
    return result;
  }

  public static void syncForstaContacts(Context context, MasterSecret masterSecret) {
    // TODO handle error response here. On 401 do we do nothing, or redirect to LoginActivity?
    JSONObject response = getUsers(context);
    if (isErrorResponse(response)) {
      Log.d(TAG, "Bad response from API");
      return;
    }
    List<ForstaUser> forstaContacts = parseUsers(context, response);
    createSystemContacts(context, forstaContacts);
    syncForstaContactsDb(context, forstaContacts);
    try {
      DirectoryHelper.refreshDirectory(context, masterSecret);
    } catch (IOException e) {
      e.printStackTrace();
    }
    CcsmApi.syncForstaGroups(context, masterSecret);
    ForstaPreferences.setForstaContactSync(context, new Date().getTime());
  }

  public static boolean checkForstaAuth(Context context) {
    JSONObject response = getForstaOrg(context);
    return isErrorResponse(response);
  }

  // TODO These should all be private. They are exposed right now for the debug dashboard.
  public static JSONObject getForstaOrg(Context context) {
    String authKey = ForstaPreferences.getRegisteredKey(context);
    return NetworkUtils.apiFetch(NetworkUtils.RequestMethod.GET, authKey, API_ORG, null);
  }

  public static JSONObject getUsers(Context context) {
    String authKey = ForstaPreferences.getRegisteredKey(context);
    return NetworkUtils.apiFetch(NetworkUtils.RequestMethod.GET, authKey, API_USER_PICK, null);
  }

  public static JSONObject getTags(Context context) {
    String authKey = ForstaPreferences.getRegisteredKey(context);
    return NetworkUtils.apiFetch(NetworkUtils.RequestMethod.GET, authKey, API_TAG, null);
  }

  public static String parseLoginToken(String authtoken) {
    if (authtoken.contains("/")) {
      String[] parts = authtoken.split("/");
      authtoken = parts[parts.length - 1];
    }
    return authtoken;
  }

  public static List<ForstaUser> parseUsers(Context context, JSONObject jsonObject) {
    List<ForstaUser> users = new ArrayList<>();
    // TODO Temporary to remove duplicates returning from API
    Set<String> forstaUids = new HashSet<>();

    try {
      JSONArray results = jsonObject.getJSONArray("results");
      for (int i = 0; i < results.length(); i++) {
        JSONObject user = results.getJSONObject(i);
        if (user.getBoolean("is_active")) {
          ForstaUser forstaUser = new ForstaUser(user);
          // Temporary to remove duplicates returning from API
          if (forstaUids.contains(forstaUser.uid)) {
            Log.d(TAG, "Duplicate user entry");
            continue;
          }
          forstaUids.add(forstaUser.uid);
          users.add(forstaUser);
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "parseUsers exception: " + e.getMessage());
      e.printStackTrace();
    }
    return users;
  }

  public static List<ForstaGroup> parseTagGroups(JSONObject jsonObject) {
    List<ForstaGroup> groups = new ArrayList<>();

    try {
      JSONArray results = jsonObject.getJSONArray("results");
      for (int i = 0; i < results.length(); i++) {
        JSONObject result = results.getJSONObject(i);
        JSONArray users = result.getJSONArray("users");
        // TODO Right now, not getting groups with no members. Leaves only.
        Set<String> members = new HashSet<>();
        boolean isGroup = false;
        for (int j = 0; j < users.length(); j++) {
          JSONObject userObj = users.getJSONObject(j);
          String association = userObj.getString("association_type");

          if (association.equals("MEMBEROF")) {
            isGroup = true;
            JSONObject user = userObj.getJSONObject("user");
            String userId = user.getString("id");
            String primaryPhone = user.getString("phone");
            members.add(primaryPhone);
          }
        }
        if (isGroup) {
          ForstaGroup group = new ForstaGroup(result);
          group.addMembers(members);
          groups.add(group);
        }
      }
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return groups;
  }

  private static Set<String> getSystemContacts(Context context) {
    Set<String> results = new HashSet<>();
    String[] projection = new String[]{
        ContactsContract.CommonDataKinds.Phone._ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.TYPE,
        ContactsContract.CommonDataKinds.Phone.LABEL
    };
    String sort = ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";

    Cursor cursor = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, sort);
    while (cursor.moveToNext()) {
      String number = cursor.getString(cursor.getColumnIndex("data1"));
      try {
        String e164number = Util.canonicalizeNumber(context, number);
        results.add(e164number);
      } catch (InvalidNumberException e) {
        e.printStackTrace();
      }
    }
    cursor.close();
    return results;
  }

  private static void createSystemContacts(Context context, List<ForstaUser> contacts) {
    try {
      Set<String> systemContacts = getSystemContacts(context);
      ArrayList<ContentProviderOperation> ops = new ArrayList<>();
      Optional<Account> account = DirectoryHelper.getOrCreateAccount(context);

      for (ForstaUser user : contacts) {
        try {
          String e164number = Util.canonicalizeNumber(context, user.phone);
          // Create contact if it doesn't exist, but don't try to update.
          if (!systemContacts.contains(e164number)) {
            ContactsDatabase.createForstaPhoneContact(context, ops, account.get(), e164number, user.name);
          } // TODO Update system contacts?
            // else ContactsDatabase.updateContact(context, ops, account.get(), e164number, user.name)
        } catch (InvalidNumberException e) {
          e.printStackTrace();
        }
      }
      context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
    } catch (Exception e) {
      Log.e(TAG, "syncContacts exception: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private static void syncForstaContactsDb(Context context, List<ForstaUser> contacts) {
    ContactDb forstaDb = DbFactory.getContactDb(context);
    forstaDb.updateUsers(contacts);
    forstaDb.close();
  }

  private static void syncForstaGroups(Context context, MasterSecret masterSecret) {
    JSONObject response = getTags(context);
    List<ForstaGroup> groups = parseTagGroups(response);
    GroupDatabase db = DatabaseFactory.getGroupDatabase(context);
    TextSecureDirectory dir = TextSecureDirectory.getInstance(context);
    List<String> activeNumbers = dir.getActiveNumbers();
    db.updateGroups(groups, activeNumbers);
  }

  private static boolean isErrorResponse(JSONObject response) {
    if (response.has("error")) {
      try {
        String error = response.getString("error");
        if (error.equals("401")) {
          return true;
        }
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
    return false;
  }
}

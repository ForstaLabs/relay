// vim: ts=2:sw=2:expandtab
package io.forsta.ccsm.api;

import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import io.forsta.ccsm.api.model.ForstaDistribution;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.ccsm.database.model.ForstaOrg;
import io.forsta.ccsm.database.model.ForstaTag;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.ccsm.util.InvalidUserException;
import io.forsta.securesms.BuildConfig;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.securesms.contacts.ContactsDatabase;
import io.forsta.securesms.database.CanonicalAddressDatabase;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.database.TextSecureDirectory;
import io.forsta.securesms.recipients.Recipient;
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
  private static final String API_TOKEN_REFRESH = "/v1/api-token-refresh/";
  private static final String API_LOGIN = "/v1/login/";
  private static final String API_JOIN = "/v1/join/";
  private static final String API_USER = "/v1/user/";
  private static final String API_USER_PICK = "/v1/user-pick/";
  private static final String API_TAG = "/v1/tag/";
  private static final String API_TAG_PICK = "/v1/tag-pick/";
  private static final String API_USER_TAG = "/v1/usertag/";
  private static final String API_ORG = "/v1/org/";
  private static final String API_DIRECTORY_USER = "/v1/directory/user/";
  private static final String API_DIRECTORY_DOMAIN = "/v1/directory/org/";
  private static final String API_SEND_TOKEN = "/v1/login/send/";
  private static final String API_PROVISION_ACCOUNT = "/v1/provision/account/";
  private static final String API_PROVISION_REQUEST = "/v1/provision/request/";
  private static final String API_USER_RESET_PASSWORD = "/v1/password/reset/";
  private static final long EXPIRE_REFRESH_DELTA = 7L;

  private CcsmApi() {
  }

  public static boolean hasDevices(Context context) {
    String host = BuildConfig.FORSTA_API_URL;
    String authKey = ForstaPreferences.getRegisteredKey(context);
    JSONObject response = NetworkUtils.apiFetch("GET", authKey, host + API_PROVISION_ACCOUNT, null);
    if (response.has("devices")) {
      try {
        JSONArray devices = response.getJSONArray("devices");
        if (devices.length() > 0) {
          return true;
        }
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
    return false;
  }

  public static void provisionRequest(Context context, String uuid, String pubKey) {
    try {
      JSONObject obj = new JSONObject();
      obj.put("uuid", uuid);
      obj.put("key", pubKey);
      fetchResource(context, "POST", API_PROVISION_REQUEST, obj);
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }

  public static JSONObject provisionAccount(Context context, JSONObject obj) throws Exception {
    return hardFetchResource(context, "PUT", API_PROVISION_ACCOUNT, obj);
  }

  public static JSONObject accountJoin(JSONObject jsonObject) {
    return NetworkUtils.apiFetch("POST", null, BuildConfig.FORSTA_API_URL + "" + API_JOIN, jsonObject);
  }

  public static JSONObject forstaLogin(Context context, JSONObject authObject) {
    String host = BuildConfig.FORSTA_API_URL;
    JSONObject result = NetworkUtils.apiFetch("POST", null, host + API_LOGIN, authObject);
    return result;
  }

  public static JSONObject forstaSendToken(Context context, String org, String username) {
    String host = BuildConfig.FORSTA_API_URL;
    JSONObject result = NetworkUtils.apiFetch("GET", null, host + API_SEND_TOKEN + org + "/" + username + "/", null);
    return result;
  }

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
    JSONObject result = new JSONObject();
    try {
      JSONObject obj = new JSONObject();
      obj.put("token", ForstaPreferences.getRegisteredKey(context));
      result = fetchResource(context, "POST", API_TOKEN_REFRESH, obj);
      if (result.has("token")) {
        Log.w(TAG, "Token refresh. New token issued.");
        String token = result.getString("token");
        ForstaPreferences.setRegisteredForsta(context, token);
      }
    } catch (Exception e) {
      e.printStackTrace();
      Log.e(TAG, "forstaRefreshToken failed");
    }
    return result;
  }

  public static JSONObject resetPassword(Context context, String tag, String org) {
    String host = BuildConfig.FORSTA_API_URL;
    ForstaUser localAccount = ForstaUser.getLocalForstaUser(context);
    JSONObject resetBody = new JSONObject();
    try {
      resetBody.put("fq_tag", "@" + tag + ":" + org);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    JSONObject result = NetworkUtils.apiFetch("POST", null, host + API_USER_RESET_PASSWORD, resetBody);
    return result;
  }

  public static void syncForstaContacts(Context context) {
    syncForstaContacts(context, false);
  }

  public static void syncForstaContacts(Context context, boolean removeInvalidUsers) {
    try {
      ForstaOrg org = ForstaOrg.getLocalForstaOrg(context);
      if (org.getSlug().equals("public") || org.getSlug().equals("forsta")) {
        List<String> addresses = DatabaseFactory.getThreadDatabase(context).getAllRecipients();
        List<ForstaUser> threadContacts = new ArrayList<>();
        if (addresses.size() > 0) {
          JSONObject threadUsers = getUserDirectory(context, addresses);
          threadContacts = CcsmApi.parseUsers(context, threadUsers);
        }
        List<ForstaUser> knownLocalContacts = getKnownLocalContacts(context);
        for (ForstaUser user : knownLocalContacts) {
          if (!threadContacts.contains(user)) {
            threadContacts.add(user);
          }
        }
        syncForstaContactsDb(context, threadContacts, removeInvalidUsers);
      } else {
        JSONObject orgUsers = getOrgUsers(context);
        List<ForstaUser> orgContacts = parseUsers(context, orgUsers);
        List<String> addresses = DatabaseFactory.getThreadDatabase(context).getAllRecipients();
        for (ForstaUser user : orgContacts) {
          if (addresses.contains(user.getUid())) {
            addresses.remove(user.getUid());
          }
        }
        if (addresses.size() > 0) {
          JSONObject threadUsers = getUserDirectory(context, addresses);
          List<ForstaUser> threadContacts = CcsmApi.parseUsers(context, threadUsers);
          orgContacts.addAll(threadContacts);
        }
        syncForstaContactsDb(context, orgContacts, removeInvalidUsers);
        CcsmApi.syncOrgTags(context);
      }
      ForstaPreferences.setForstaContactSync(context, new Date().getTime());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void syncForstaContacts(Context context, List<String> addresses) {
    JSONObject response = getUserDirectory(context, addresses);
    List<ForstaUser> forstaContacts = parseUsers(context, response);
    syncForstaContactsDb(context, forstaContacts, false);
  }

  private static List<ForstaUser> getKnownLocalContacts(Context context) {
    Set<String> systemNumbers = new HashSet<>();
    Cursor cursor = null;
    try {
      cursor = DatabaseFactory.getContactsDatabase(context).querySystemContacts("");
      while (cursor != null && cursor.moveToNext()) {
        String number = cursor.getString(cursor.getColumnIndex(ContactsDatabase.NUMBER_COLUMN));
        try {
          number = Util.canonicalizeNumberE164(number);
          systemNumbers.add(number);
        } catch (InvalidNumberException e) {
          e.printStackTrace();
        }
      }
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }

    return getForstaUsersByPhone(context, systemNumbers);
  }

  private static void syncForstaContactsDb(Context context, List<ForstaUser> contacts, boolean removeExisting) {
    DbFactory.getContactDb(context).updateUsers(contacts, removeExisting);
  }

  private static void syncOrgTags(Context context) {
    JSONObject response = getTags(context);
    List<ForstaTag> groups = parseTagGroups(response);
    GroupDatabase db = DatabaseFactory.getGroupDatabase(context);
    TextSecureDirectory dir = TextSecureDirectory.getInstance(context);
    List<String> activeNumbers = dir.getActiveNumbers();
    db.updateGroups(groups, activeNumbers);
  }

  private static JSONObject getUsersByPhone(Context context, Set<String> phoneNumbers) {
    String query = "";
    try {
      query = "?phone_in=" + URLEncoder.encode(TextUtils.join(",", phoneNumbers), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return fetchResource(context, "GET", API_DIRECTORY_USER + query);
  }

  private static JSONObject getUsersByEmail(Context context, Set<String> emailAddresses) {
    String query = "";
    try {
      query = "?email_in=" + URLEncoder.encode(TextUtils.join(",", emailAddresses), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return fetchResource(context, "GET", API_DIRECTORY_USER + query);
  }

  public static JSONObject getUserDirectory(Context context, List<String> addresses) {
    String addressesString = TextUtils.join(",", addresses);
    String query = "";
    if (!TextUtils.isEmpty(addressesString)) {
      query = "?id_in=" + addressesString;
    }
    return fetchResource(context, "GET", API_DIRECTORY_USER + query);
  }

  public static JSONObject getOrgDirectory(Context context) {
    return fetchResource(context, "GET", API_DIRECTORY_DOMAIN);
  }

  public static JSONObject getOrg(Context context) {
    ForstaUser localAccount = ForstaUser.getLocalForstaUser(context);
    if (localAccount == null) {
      return null;
    }
    return getOrg(context, localAccount.org_id);
  }

  public static JSONObject getOrg(Context context, String id) {
    return fetchResource(context, "GET", API_ORG + id + "/");
  }

  public static JSONObject getOrgUsers(Context context) {
    return fetchResource(context, "GET", API_USER);
  }

  public static JSONObject getForstaUser(Context context) {
    return fetchResource(context, "GET", API_USER + TextSecurePreferences.getLocalNumber(context) + "/");
  }

  public static JSONObject getTags(Context context) {
    return fetchResource(context, "GET", API_TAG);
  }

  public static JSONObject getUserPick(Context context) {
    return fetchResource(context, "GET", API_USER_PICK);
  }

  public static JSONObject getTagPick(Context context) {
    return fetchResource(context, "GET", API_TAG_PICK);
  }

  public static JSONObject getDistribution(Context context, String expression) {
    JSONObject jsonObject = new JSONObject();
    JSONObject response = new JSONObject();
    try {
      jsonObject.put("expression", expression);
      String urlEncoded = TextUtils.isEmpty(expression) ? "" : URLEncoder.encode(expression, "UTF-8");
      response = fetchResource(context, "GET", API_DIRECTORY_USER + "?expression=" + urlEncoded);
    } catch (JSONException e) {
      e.printStackTrace();
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return response;
  }

  public static JSONObject searchUserDirectory(Context context, String searchText) {
    JSONObject response = new JSONObject();
    try {
      String urlEncoded = TextUtils.isEmpty(searchText) ? "" : URLEncoder.encode(searchText, "UTF-8");
      response = fetchResource(context, "GET", API_DIRECTORY_USER + "?q=" + urlEncoded);
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return response;
  }

  private static JSONObject fetchResource(Context context, String method, String urn) {
    return fetchResource(context, method, urn, null);
  }

  private static JSONObject fetchResource(Context context, String method, String urn, JSONObject body) {
    String baseUrl = BuildConfig.FORSTA_API_URL;
    String authKey = ForstaPreferences.getRegisteredKey(context);
    return NetworkUtils.apiFetch(method, authKey, baseUrl + urn, body);
  }

  private static JSONObject hardFetchResource(Context context, String method, String urn, JSONObject body) throws Exception {
    String baseUrl = BuildConfig.FORSTA_API_URL;
    String authKey = ForstaPreferences.getRegisteredKey(context);
    return NetworkUtils.apiHardFetch(method, authKey, baseUrl + urn, body);
  }

  // Helper methods and mapper functions. Move these.
  public static String parseLoginToken(String authtoken) {
    if (authtoken.contains("/")) {
      String[] parts = authtoken.split("/");
      authtoken = parts[parts.length - 1];
    }
    return authtoken;
  }

  public static List<ForstaUser> parseUsers(Context context, JSONObject jsonObject) {
    List<ForstaUser> users = new ArrayList<>();
    try {
      JSONArray results = jsonObject.getJSONArray("results");
      for (int i = 0; i < results.length(); i++) {
        try {
          JSONObject user = results.getJSONObject(i);
          ForstaUser forstaUser = new ForstaUser(user);
          users.add(forstaUser);
        } catch (Exception e) {
          Log.e(TAG, "parseUsers exception: " + e.getMessage());
        }
      }
    } catch (JSONException e) {
      Log.e(TAG, "No results array.");
    }
    return users;
  }

  public static List<ForstaTag> parseTagGroups(JSONObject jsonObject) {
    List<ForstaTag> groups = new ArrayList<>();

    try {
      JSONArray results = jsonObject.getJSONArray("results");
      for (int i = 0; i < results.length(); i++) {
        JSONObject result = results.getJSONObject(i);
        JSONArray users = result.getJSONArray("users");
        // TODO Right now, not getting tags with no members. Leaves only.
        Set<String> members = new HashSet<>();
        boolean isGroup = false;
        for (int j = 0; j < users.length(); j++) {
          JSONObject userObj = users.getJSONObject(j);
          String association = userObj.getString("association_type");

          if (association.equals("MEMBEROF")) {
            isGroup = true;
            JSONObject user = userObj.getJSONObject("user");
            String userId = user.getString("id");
            members.add(userId);
          }
        }
        if (isGroup) {
          ForstaTag group = new ForstaTag(result);
          group.addMembers(members);
          groups.add(group);
        }
      }
    } catch (JSONException e) {
      Log.e(TAG, "parseTagGroups exception");
      e.printStackTrace();
    }
    return groups;
  }

  public static ForstaDistribution getMessageDistribution(Context context, String expression) {
    JSONObject response = getDistribution(context, expression);
    return ForstaDistribution.fromJson(response);
  }

  public static List<ForstaUser> getForstaUsersByPhone(Context context, Set<String> phoneNumbers) {
    JSONObject jsonObject = getUsersByPhone(context, phoneNumbers);
    return parseUsers(context, jsonObject);
  }

  public static List<ForstaUser> getForstaUsersByEmail(Context context, Set<String> emailAddresses) {
    JSONObject jsonObject = getUsersByEmail(context, emailAddresses);
    return parseUsers(context, jsonObject);
  }

  private static boolean isUnauthorizedResponse(JSONObject response) {
    if (response == null) {
      return true;
    }
    if (response.has("error")) {
      try {
        String error = response.getString("error");
        Log.e(TAG, error);
        if (error.contains("401")) {
          Log.e(TAG, "CCSM API Unauthorized.");
          return true;
        }
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
    return false;
  }

  // XXX obsolete XXX
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


}

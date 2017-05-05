package io.forsta.ccsm.api;

import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.securesms.BuildConfig;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import io.forsta.util.NetworkUtils;

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
  // Remove these. It is already in ContactsDatabase. Needs to move when contact methods are moved.
  private static final String SYNC = "__TS";
  private static final String CONTACT_MIMETYPE = "vnd.android.cursor.item/vnd.io.forsta.securesms.contact";
  private static final long CONTACT_SYNC_INTERVAL = 1000l * 60 * 60 * 12;

  private CcsmApi() {
  }

  public static JSONObject forstaLogin(Context context, String authToken) {
    return forstaLogin(context, "", "", authToken);
  }

  public static JSONObject forstaLogin(Context context, String username, String password, String authToken) {
    JSONObject result = new JSONObject();
    try {
      JSONObject obj = new JSONObject();
      if (!authToken.equals("")) {
        String token = authToken.contains(":") ? authToken : username + ":" + authToken;
        obj.put("authtoken", token);
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

  // These should all be private. They are exposed right now
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
    // Temporary to remove duplicates returning from API
    Set<String> forstaUids = new HashSet<>();

    try {
      JSONArray results = jsonObject.getJSONArray("results");
      for (int i = 0; i < results.length(); i++) {
        JSONObject user = results.getJSONObject(i);
        if (user.getBoolean("is_active")) {
          ForstaUser forstaUser = new ForstaUser(user);
          // Temporary to remove duplicates returning from API
          if (forstaUids.contains(forstaUser.uid)) {
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
        // Right now, not getting groups with no members. Leaves only.
        Map<String, String> members = new HashMap<>();
        boolean isGroup = false;
        for (int j = 0; j < users.length(); j++) {
          JSONObject userObj = users.getJSONObject(j);
          String association = userObj.getString("association_type");

          if (association.equals("MEMBEROF")) {
            isGroup = true;
            JSONObject user = userObj.getJSONObject("user");
            String userId = user.getString("id");
            String primaryPhone = user.getString("phone");
            members.put(userId, primaryPhone);
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

  private static Set<String> getGroupIds(Context context) {
    Set<String> groupIds = new HashSet<>();
    GroupDatabase groupDb = DatabaseFactory.getGroupDatabase(context);
    GroupDatabase.Reader reader = groupDb.getGroups();
    GroupDatabase.GroupRecord record;
    GroupDatabase.GroupRecord existing = null;
    while ((record = reader.getNext()) != null) {
      groupIds.add(record.getEncodedId());
    }
    reader.close();
    return groupIds;
  }

  private static Set<Recipient> getActiveRecipients(Context context, List<String> groupNumbers, List<String> activeNumbers) {
    for (int i = 0; i < groupNumbers.size(); i++) {
      String number = groupNumbers.get(i);
      if (!activeNumbers.contains(number)) {
        groupNumbers.remove(number);
      }
    }
    groupNumbers.remove(BuildConfig.FORSTA_SYNC_NUMBER);
    Recipients recipients = RecipientFactory.getRecipientsFromStrings(context, groupNumbers, false);
    Set<Recipient> members = new HashSet<>(recipients.getRecipientsList());
    return members;
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

  public static void createSystemContacts(Context context, List<ForstaUser> contacts) {
    try {
      Set<String> systemContacts = getSystemContacts(context);
      ArrayList<ContentProviderOperation> ops = new ArrayList<>();
      Optional<Account> account = DirectoryHelper.getOrCreateAccount(context);

      for (ForstaUser user : contacts) {
        try {
          String e164number = Util.canonicalizeNumber(context, user.phone);
          // Create contact if it doesn't exist, but don't try to update.
          if (!systemContacts.contains(e164number)) {
            createForstaPhoneContact(context, ops, account.get(), e164number, user.name);
          } // else updateContact(context, ops, account.get(), e164number, user.name)
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

  public static void syncForstaContacts(Context context) {
    JSONObject users = getUsers(context);
    List<ForstaUser> forstaContacts = parseUsers(context, users);
    createSystemContacts(context, forstaContacts);
    syncForstaContactsDb(context, forstaContacts);
  }

  private static void syncForstaContactsDb(Context context, List<ForstaUser> contacts) {
    ContactDb forstaDb = DbFactory.getContactDb(context);
    forstaDb.updateUsers(contacts);
    forstaDb.close();
  }

  public static void syncForstaGroups(Context context, MasterSecret masterSecret) {
    try {
      JSONObject jsonObject = getTags(context);
      List<ForstaGroup> groups = parseTagGroups(jsonObject);

      // Get existing groups and active numbers
      Set<String> groupIds = getGroupIds(context);
      TextSecureDirectory dir = TextSecureDirectory.getInstance(context);
      List<String> activeNumbers = dir.getActiveNumbers();

      for (ForstaGroup group : groups) {
        String id = group.getEncodedId();
        List<String> groupNumbers = new ArrayList<>(group.getGroupNumbers());
        Set<Recipient> members = getActiveRecipients(context, groupNumbers, activeNumbers);
        String thisNumber = TextSecurePreferences.getLocalNumber(context);

        // For now. No groups are created unless you are a member and the group has more than one other member.
        if (members.size() > 1 && groupNumbers.contains(thisNumber)) {
          if (!groupIds.contains(id)) {
            GroupManager.createForstaGroup(context, masterSecret, group, members, null, group.description);
          } else {
            GroupManager.updateForstaGroup(context, masterSecret, group.id.getBytes(), members, null, group.description);
          }
          groupIds.remove(id);
        }
      }
      GroupManager.removeForstaGroups(context, groupIds);

    } catch (InvalidNumberException e) {
      Log.e(TAG, "syncForstaGroups Invalid Number exception");
      e.printStackTrace();
    } catch (Exception e) {
      Log.e(TAG, "syncForstaGroups exception: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public static boolean refreshContacts(Context context, MasterSecret masterSecret) {
    long lastSync = ForstaPreferences.getForstaContactSync(context);
    if (lastSync != -1) {
      long now = System.currentTimeMillis();
      long diff = now-lastSync;
      long updateDiff = CONTACT_SYNC_INTERVAL;
      boolean shouldUpdate = diff > updateDiff;
      return shouldUpdate;
    }
    return true;
  }

  // These can be moved to the ContactsDatabase after testing.
  private static void updateContact(Context context, List<ContentProviderOperation> ops, Account account, String number, String name) {
    ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
        .withSelection(ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?", new String[] { number })
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
        .build());
  }

  public static void removeAllContacts(Context context) {
    ArrayList<ContentProviderOperation> operations        = new ArrayList<>();
    Uri uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
        .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, "Forsta")
        .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, "io.forsta.securesms")
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();

    operations.add(ContentProviderOperation.newDelete(uri)
        .withYieldAllowed(true)
        .build());
    try {
      context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, operations);
    } catch (RemoteException e) {
      e.printStackTrace();
    } catch (OperationApplicationException e) {
      e.printStackTrace();
    }
  }

  private static void createForstaPhoneContact(Context context, List<ContentProviderOperation> operations, Account account, String e164number, String name) {
    int index = operations.size();

    Uri dataUri = ContactsContract.Data.CONTENT_URI.buildUpon()
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
        .build();

    operations.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
        .withValue(ContactsContract.RawContacts.SYNC1, e164number)
        .withValue(ContactsContract.RawContacts.SYNC4, String.valueOf(true))
        .build());

    operations.add(ContentProviderOperation.newInsert(dataUri)
        .withValueBackReference(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID, index)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, e164number)
        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_OTHER)
        .withValue(ContactsContract.Data.SYNC2, SYNC)
        .build());

    operations.add(ContentProviderOperation.newInsert(dataUri)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index)
        .withValue(ContactsContract.Data.MIMETYPE, CONTACT_MIMETYPE)
        .withValue(ContactsContract.Data.DATA1, e164number)
        .withValue(ContactsContract.Data.DATA2, context.getString(R.string.app_name))
        .withValue(ContactsContract.Data.DATA3, context.getString(R.string.ContactsDatabase_message_s, e164number))
        .withYieldAllowed(true)
        .build());

    operations.add(ContentProviderOperation.newInsert(dataUri)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
        .build());
  }

  private static void createPhoneContact(Context context, List<ContentProviderOperation> ops, Account account, String number, String name, String username) {
    int index = ops.size();

    ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
        .build());

    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, "Forsta-" + name)
        .build());

    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
        .build());
  }
}

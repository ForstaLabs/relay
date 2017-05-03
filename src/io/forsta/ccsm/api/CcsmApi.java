package io.forsta.ccsm.api;

import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
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

  private static final String CONTACT_MIMETYPE = "vnd.android.cursor.item/vnd.io.forsta.securesms.contact";

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

  // These should all be private.
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
    TextSecureDirectory dir = TextSecureDirectory.getInstance(context);
    List<String> activeNumbers = dir.getActiveNumbers();
    List<ForstaUser> users = new ArrayList<>();

    try {
      JSONArray results = jsonObject.getJSONArray("results");
      for (int i = 0; i < results.length(); i++) {
        JSONObject user = results.getJSONObject(i);
        if (user.getBoolean("is_active")) {
          ForstaUser forstaUser = new ForstaUser(user);
          if (activeNumbers.contains(forstaUser.phone)) {
            forstaUser.tsRegistered = true;
          }
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

  public static void syncForstaGroups(Context context, MasterSecret masterSecret) {
    try {
      // Get groups from API
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

        // For now. No groups are created unless you are a member and the group has more than one other member.
        if (members.size() > 1 && groupNumbers.contains(TextSecurePreferences.getLocalNumber(context))) {
          if (!groupIds.contains(id)) {
            GroupManager.createForstaGroup(context, masterSecret, group, members, null, group.description);
          } else {
            GroupManager.updateForstaGroup(context, masterSecret, group.id.getBytes(), members, null, group.description);
          }
        }
      }
    } catch (InvalidNumberException e) {
      Log.e(TAG, "syncForstaGroups Invalid Number exception");
      e.printStackTrace();
    } catch (Exception e) {
      Log.e(TAG, "syncForstaGroups exception: " + e.getMessage());
      e.printStackTrace();
    }
  }

  // This is for syncing a local Forsta ContactDb, separate from the ContactsContract system database.
  public static void syncForstaContactsLocal(Context context, List<ForstaUser> forstaUsers) {
    ContactDb db = DbFactory.getContactDb(context);
    Map<String, String> uids = db.getUids();
    TextSecureDirectory dir = TextSecureDirectory.getInstance(context);
    List<String> activeNumbers = dir.getActiveNumbers();
    Set<String> forstaUids = new HashSet<>();

    for (ForstaUser item : forstaUsers) {
      try {
        String e164number = Util.canonicalizeNumber(context, item.phone);

        if (forstaUids.contains(item.id)) {
          // Temporary fix to remove duplicates coming from API.
          continue;
        }
        if (!uids.containsKey(item.uid)) {
          ContentValues values = new ContentValues();
          values.put(ContactDb.UID, item.uid);
          values.put(ContactDb.NAME, item.name);
          values.put(ContactDb.ORGID, item.orgId);
          values.put(ContactDb.NUMBER, item.phone);
          values.put(ContactDb.USERNAME, item.username);
          values.put(ContactDb.DATE, new Date().toString());
          values.put(ContactDb.TSREGISTERED, activeNumbers.contains(e164number) ? 1 : 0);
          item.id = String.valueOf(db.add(values));
        } else {
          // Update the user's registered status and name
          ContentValues values = new ContentValues();
          values.put(ContactDb.NAME, item.name);
          values.put(ContactDb.TSREGISTERED, activeNumbers.contains(e164number) ? 1 : 0);
          db.update(uids.get(item.uid), values);
        }
      } catch (InvalidNumberException e) {
        e.printStackTrace();
      } catch (Exception e) {
        e.printStackTrace();
      }
      // Remove users who are no longer in the API result set.
      forstaUids.add(item.uid);
    }

    for (String entry : uids.keySet()) {
      if (!forstaUids.contains(entry)) {
        db.remove(entry);
      }
    }

    db.close();
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

  public static void syncForstaContacts(Context context) {
    try {
      JSONObject users = getUsers(context);
      List<ForstaUser> forstaContacts = parseUsers(context, users);

      Set<String> systemContacts = getSystemContacts(context);
      ArrayList<ContentProviderOperation> ops = new ArrayList<>();
      Optional<Account> account = DirectoryHelper.getOrCreateAccount(context);
      Set<String> forstaUids = new HashSet<>();

      for (ForstaUser user : forstaContacts) {
        // Temporary fix because of duplicates returning from API.
        if (forstaUids.contains(user.uid)) {
          continue;
        }
        forstaUids.add(user.uid);
        try {
          String e164number = Util.canonicalizeNumber(context, user.phone);
          // Create contact if it doesn't exist, but don't try to update.
          if (!systemContacts.contains(e164number)) {
            createPhoneContact(context, ops, account.get(), e164number, user.name, user.username);
          }
        } catch (InvalidNumberException e) {
          e.printStackTrace();
        }
      }
      context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);

      ContactDb forstaDb = DbFactory.getContactDb(context);
      forstaDb.updateUsers(forstaContacts);
      // Now remove entries that are no longer valid.
      Map<String, String> uids = forstaDb.getUids();
      for (String uid : uids.keySet()) {
        if (!forstaUids.contains(uid)) {
          forstaDb.removeByUid(uid);
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "syncContacts exception: " + e.getMessage());
      e.printStackTrace();
    }
  }

  // These can be moved to the ContactsDatabase after testing.
  private static void updateContact(Context context, List<ContentProviderOperation> ops, Account account, String number, String name, String username) {
    ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
        .withSelection(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME_ALTERNATIVE + " = ?", new String[] { username })
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, "Forsta-" + name)
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

  private static void createPhoneContact(Context context, List<ContentProviderOperation> ops, Account account, String number, String name, String username) {
    int index = ops.size();

    Uri dataUri = ContactsContract.Data.CONTENT_URI.buildUpon()
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
        .build();

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

  private static void createContact(Context context, List<ContentProviderOperation> ops, Account account, String number, String name, String username) {
    int index = ops.size();
    Uri dataUri = ContactsContract.Data.CONTENT_URI.buildUpon()
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
        .build();

    ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
        .withValue(ContactsContract.RawContacts.SYNC1, number)
        .withValue(ContactsContract.RawContacts.SYNC4, String.valueOf(false))
        .build());

    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, "Forsta-" + name)
        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME_ALTERNATIVE, username)
        .build());

    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
        .build());

    ops.add(ContentProviderOperation.newInsert(dataUri)
        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index)
        .withValue(ContactsContract.Data.MIMETYPE, CONTACT_MIMETYPE)
        .withValue(ContactsContract.Data.DATA1, number)
        .withValue(ContactsContract.Data.DATA2, context.getString(R.string.app_name))
        .withValue(ContactsContract.Data.DATA3, context.getString(R.string.ContactsDatabase_message_s, number))
        .withYieldAllowed(true)
        .build());
  }
}

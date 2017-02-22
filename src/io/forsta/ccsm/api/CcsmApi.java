package io.forsta.ccsm.api;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import io.forsta.securesms.BuildConfig;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.securesms.contacts.ContactsDatabase;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.util.Base64;
import io.forsta.util.NetworkUtils;

/**
 * Created by jlewis on 1/18/17.
 */

public class CcsmApi {
    private static final String TAG = CcsmApi.class.getSimpleName();
    private static final String API_URL = BuildConfig.FORSTA_API_URL;
    private static final String API_TOKEN_REFRESH = API_URL + "/v1/api-token-refresh";
    private static final String API_LOGIN = API_URL + "/v1/login";
    private static final String API_USER = API_URL + "/v1/user";
    private static final String API_TAG = API_URL + "/v1/tag";
    private static final String API_ORG = API_URL + "/v1/org";
    private static final String API_SEND_TOKEN = API_URL + "/v1/login/send";
    private static final String API_AUTH_TOKEN = API_URL + "/v1/login/authtoken";
    private static final long EXPIRE_REFRESH_DELTA = 7L;

    private CcsmApi() { }

    public static JSONObject getContacts(Context context) {
        String authKey = ForstaPreferences.getRegisteredKey(context);
        return NetworkUtils.apiFetch(NetworkUtils.RequestMethod.GET, authKey, API_TAG, null);
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
        long expiresIn = (expireDate.getTime() - current.getTime())/(1000 * 60 * 60 * 24);
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
        JSONObject result = NetworkUtils.apiFetch(NetworkUtils.RequestMethod.GET, null, API_SEND_TOKEN + "/" + org + "/" + username + "/", null);
        return result;
    }

    public static String parseLoginToken(String authtoken) {
        if (authtoken.contains("/")) {
            String[] parts = authtoken.split("/");
            authtoken = parts[parts.length-1];
        }
        return authtoken;
    }

    public static Map<String, String> getContacts(JSONObject jsonObject) {
        Map<String, String> contacts = new HashMap<>();
        try {
            JSONArray results = jsonObject.getJSONArray("results");
            for (int i=0; i<results.length(); i++) {
                JSONObject obj = results.getJSONObject(i);
                JSONArray users = obj.getJSONArray("users");
                if (users.length() > 0) {
                    for (int j=0; j<users.length(); j++) {
                        JSONObject user = users.getJSONObject(j).getJSONObject("user");
                        if (user.has("primary_phone")) {
                            String name = user.getString("first_name") + " " + user.getString("last_name");
                            contacts.put(user.getString("primary_phone"), name);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return contacts;
    }

    public static void syncForstaContacts(Context context, JSONObject tags) {
        Map<String, String> contacts = getContacts(tags);
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        ContactsDatabase db = DatabaseFactory.getContactsDatabase(context);
        Cursor cursor = db.querySystemContacts(null);

        if (cursor != null) {
            while(cursor.moveToNext()) {
                // get each contact. If there is not an entry for Forsta user, add it.
                // Then check to see if user is registered with the TextSecure server.
                // if they are registered, update the contact.
            }
            cursor.close();
        }


        for (Map.Entry<String, String> entry : contacts.entrySet()) {
            updateContactsDb(ops, entry.getKey(), entry.getValue());
        }
        try{
            ContentProviderResult[] results = context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private static void updateContactsDb(List<ContentProviderOperation> ops, String number, String name) {
        int index = ops.size();
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build());

        // first and last names
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, "Forsta-" + name)
                .build());

        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index)
                .withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build());
    }
}

package io.forsta.ccsm.api;

import android.content.Context;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import io.forsta.securesms.BuildConfig;
import java.util.Date;
import io.forsta.ccsm.ForstaPreferences;
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
    private static final String API_SEND_TOKEN = API_URL + "/v1/login/send";
    private static final String API_AUTH_TOKEN = API_URL + "/v1/login/authtoken";
    private static final long EXPIRE_REFRESH_DELTA = 7L;

    private CcsmApi() { }

    public static JSONObject getContacts(Context context) {
        String authKey = ForstaPreferences.getRegisteredKey(context);
        return NetworkUtils.apiFetch(NetworkUtils.RequestMethod.GET, authKey, API_USER, null);
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
}

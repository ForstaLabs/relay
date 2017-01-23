package io.forsta.ccsm.api;

import android.content.Context;
import android.text.SpannableString;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.ccsm.LoginActivity;
import io.forsta.util.NetworkUtils;

/**
 * Created by jlewis on 1/18/17.
 */

public class CcsmApi {
    private static final String TAG = CcsmApi.class.getSimpleName();
    private static final String API_URL = "https://ccsm-dev-api.forsta.io/v1/";
    private static final String API_URL_LOCAL = "http://192.168.1.29:8000/v1/";

    private CcsmApi() {

    }

    public static JSONObject getContacts(Context context) {
        return apiGet(context, "user");
    }

    public static JSONObject forstaLogin(Context context, String username, String password) {
        JSONObject result = new JSONObject();
        try {
            JSONObject obj = new JSONObject();
            obj.put("email", username);
            obj.put("username", username);
            obj.put("password", password);
            result = apiPost(context, "login", obj, false);
            if (result.has("token")) {
                String token = result.getString("token");
                JSONObject user = result.getJSONObject("user");
                String lastLogin = user.getString("last_login");
                // Write token to local preferences.
                ForstaPreferences.setRegisteredForsta(context, token);
                ForstaPreferences.setRegisteredDateTime(context, lastLogin);
                // Store last login for token refresh.
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Log.d(TAG, "JSON Exception.");
        }
        return result;
    }

    public static JSONObject forstaRefreshToken(Context context) {
        JSONObject result = new JSONObject();
        try {
            JSONObject obj = new JSONObject();
            obj.put("token", ForstaPreferences.getRegisteredKey(context));
            result = apiPost(context, "api-token-refresh", obj, false);
            if (result.has("token")) {
                String token = result.getString("token");
                String existingToken = ForstaPreferences.getRegisteredKey(context);
                if (existingToken.equals(token)) {
                    Log.d(TAG, "Token refresh. Not expired");
                } else {
                    Log.d(TAG, "Token refresh. New token issued.");
                    ForstaPreferences.setRegisteredForsta(context, result.getString("token"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "forstaRefreshToken failed");
        }
        return result;
    }

    private static JSONObject apiGet(Context context, String path) {
        String authKey = ForstaPreferences.getRegisteredKey(context);
        // TODO If no JWT token. Log error or do something.
        HttpURLConnection conn = null;
        JSONObject result = new JSONObject(); //Or null?
        try {
            path = !path.endsWith("/") ? path + "/" : path;
            URL url = new URL(API_URL + path);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "JWT " + authKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            String strResult = readResult(conn.getInputStream());
            result = new JSONObject(strResult);
            Log.d(TAG, result.toString());

        } catch (MalformedURLException e) {
            e.printStackTrace();
            Log.e(TAG, "Bad URL.");
        } catch (ConnectException e) {
            Log.e(TAG, "Connect Exception.");
            Log.d(TAG, e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "IO Exception.");
            Log.d(TAG, e.getMessage());
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
            Log.d(TAG, "JSON Exception.");
        } finally {
            if (conn != null)
                conn.disconnect();
        }
        return result;
    }

    private static JSONObject apiPost(Context context, String path, JSONObject data, boolean requireAuth) {
        String authKey = ForstaPreferences.getRegisteredKey(context);
        JSONObject result = null;
        HttpURLConnection conn = null;
        try {

            URL url = new URL(API_URL + fixApiPath(path));

            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            if (requireAuth) {
                conn.setRequestProperty("Authorization", "JWT " + authKey);
            }
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            DataOutputStream out = new DataOutputStream(conn.getOutputStream());
            out.writeBytes(data.toString());
            out.close();

            result = new JSONObject(readResult(conn.getInputStream()));
            Log.d(TAG, result.toString());

        } catch (MalformedURLException e) {
            e.printStackTrace();
            Log.d(TAG, "Bad URL.");
        } catch (ConnectException e) {
            Log.d(TAG, "Connect Exception.");
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "IO Exception.");
            Log.d(TAG, e.getMessage());
        } catch (JSONException e) {
            e.printStackTrace();
            Log.d(TAG, "JSON Exception.");
        } finally {
            if (conn != null)
                conn.disconnect();
        }
        return result;
    }

    private static String fixApiPath(String path) {
        return !path.endsWith("/") ? path + "/" : path;
    }

    public static JSONObject sendMessageToServer(Context context, SmsMessageRecord message) {
        JSONObject jsonObj = messageToJSONObject(message);
        return apiPost(context, "message", jsonObj, true);
    }

    private static String readResult(InputStream input) {
        BufferedReader br = new BufferedReader(new InputStreamReader(input));
        String line = null;
        StringBuilder sb = new StringBuilder();
        try {
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    private static JSONObject messageToJSONObject(SmsMessageRecord message) {
        SpannableString body = message.getDisplayBody();
        Recipient recipient = message.getIndividualRecipient();
        Recipients recipients = message.getRecipients();
        Recipient primary = recipients.getPrimaryRecipient();

        JSONObject obj = new JSONObject();
        try {
            String recipientNumber = String.valueOf(primary.getNumber());
            String recipientDeviceId = String.valueOf(primary.getRecipientId());
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            Phonenumber.PhoneNumber phoneNumber = phoneUtil.parse(recipientNumber, "US");
            String numberString = phoneUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
            Date sent = new Date(message.getDateSent());
            SimpleDateFormat fmtOut = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
            String dateString = fmtOut.format(sent);
            Log.d(TAG, dateString);
            Log.d(TAG, numberString);
            obj.put("destination_number", numberString);
            obj.put("destination_device_id", recipientDeviceId);
            obj.put("source_number", "+12085143367");
            obj.put("source_device_id", "0");
            obj.put("relay", "");
            obj.put("msg_type", "PLAINTEXT");
            obj.put("body", body.toString());
            obj.put("time_sent", dateString);
            obj.put("likes", 0);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (NumberParseException e) {
            e.printStackTrace();
        }
        return obj;
    }
}

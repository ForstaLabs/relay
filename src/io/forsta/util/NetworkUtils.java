package io.forsta.util;

import android.content.Context;
import android.text.SpannableString;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.json.JSONException;
import org.json.JSONObject;
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

/**
 * Created by jlewis on 12/13/16.
 */

public class NetworkUtils {
    private static final String TAG = NetworkUtils.class.getSimpleName();
    // TODO get this host address from ccsm-api and store in local preferences.
    private static final String API_URL = "https://ccsm-dev-api.forsta.io/v1/";
    private static final String API_URL_LOCAL = "http://192.168.1.29:8000/v1/";

    public static JSONObject ccsmLogin(String username, String password) {
        HttpURLConnection conn = null;
        JSONObject result = new JSONObject(); //Or null?
        try {
            URL url = new URL(API_URL + "login/");

            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            JSONObject obj = new JSONObject();
            obj.put("email", username);
            obj.put("username", username);
            obj.put("password", password);
            DataOutputStream out = new DataOutputStream(conn.getOutputStream());
            out.writeBytes(obj.toString());
            out.close();
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

    public static JSONObject getApiData(Context context) {
        String authKey = ForstaPreferences.getRegisteredKey(context);
        // TODO If no JWT token. Log error or do something.
        HttpURLConnection conn = null;
        JSONObject result = new JSONObject(); //Or null?
        try {
            URL url = new URL(API_URL + "message/");

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "JWT " + authKey);

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

    public static JSONObject sendToServer(Context context, SmsMessageRecord message) {
        String authKey = ForstaPreferences.getRegisteredKey(context);
        JSONObject result = null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(API_URL + "message/");

            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "JWT " + authKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            JSONObject obj = messageJSONObject(message);
            DataOutputStream out = new DataOutputStream(conn.getOutputStream());
            out.writeBytes(obj.toString());
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

    private static JSONObject messageJSONObject(SmsMessageRecord message) {
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

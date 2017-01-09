package io.forsta.util;

import android.telephony.TelephonyManager;
import android.text.SpannableString;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Created by jlewis on 12/13/16.
 */

public class NetworkUtils {
    private static final String DEBUG_TAG = "FORSTA-RELAY";
    private static final String API_KEY_LOCAL = "Token 28c545ff7a9fc96a3ab9ad679fc506fadd393bcd";
    private static final String API_KEY = "Token 6dd6bb83729ff8a36e61200cef8281e5c1906b3e";
    private static final String API_URL = "https://ccsm-dev-api.forsta.io/v1/message/";
    private static final String API_URL_LOCAL = "http://192.168.1.29:8000/api-token-auth/";

    public static JSONObject ccsmLogin(String username, String password) {
        HttpURLConnection conn = null;
        JSONObject result = new JSONObject(); //Or null?
        try {
            URL url = new URL(API_URL_LOCAL);

            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            JSONObject obj = new JSONObject();
            obj.put("username", username);
            obj.put("password", password);
            DataOutputStream out = new DataOutputStream(conn.getOutputStream());
            out.writeBytes(obj.toString());
            out.close();
            String strResult = readResult(conn.getInputStream());
            result = new JSONObject(strResult);
            Log.d(DEBUG_TAG, result.toString());

        } catch (MalformedURLException e) {
            e.printStackTrace();
            Log.e(DEBUG_TAG, "Bad URL.");
        } catch (ConnectException e) {
            Log.e(DEBUG_TAG, "Connect Exception.");
            Log.d(DEBUG_TAG, e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(DEBUG_TAG, "IO Exception.");
            Log.d(DEBUG_TAG, e.getMessage());
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
            Log.d(DEBUG_TAG, "JSON Exception.");
        } finally {
            if (conn != null)
                conn.disconnect();
        }
        return result;
    }

    public static JSONObject sendToServer(SmsMessageRecord message) {
        JSONObject result = null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(API_URL);

            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", API_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            JSONObject obj = messageJSONObject(message);
            DataOutputStream out = new DataOutputStream(conn.getOutputStream());
            out.writeBytes(obj.toString());
            out.close();

            result = new JSONObject(readResult(conn.getInputStream()));
            Log.d(DEBUG_TAG, result.toString());

        } catch (MalformedURLException e) {
            e.printStackTrace();
            Log.d(DEBUG_TAG, "Bad URL.");
        } catch (ConnectException e) {
            Log.d(DEBUG_TAG, "Connect Exception.");
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(DEBUG_TAG, "IO Exception.");
            Log.d(DEBUG_TAG, e.getMessage());
        } catch (JSONException e) {
            e.printStackTrace();
            Log.d(DEBUG_TAG, "JSON Exception.");
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
            Log.d(DEBUG_TAG, dateString);
            Log.d(DEBUG_TAG, numberString);
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

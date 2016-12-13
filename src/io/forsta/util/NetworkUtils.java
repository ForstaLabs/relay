package io.forsta.util;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * Created by jlewis on 12/13/16.
 */

public class NetworkUtils {


    public static JSONObject sendToSuper(OutgoingTextMessage message, long threadId, long nmessageId, Recipients recipients) {
        final String DEBUG_TAG = "MESSAGE HIJACKER";
        String serverUrl = "http://192.168.1.29:8000/relay/";
        String duplicate = message.getMessageBody();
        JSONObject result = null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(serverUrl);

            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            // Data to POST
            JSONObject obj = createJSONObject(message, threadId, nmessageId, recipients);
            DataOutputStream out = new DataOutputStream(conn.getOutputStream());
            out.writeBytes(obj.toString());
            out.close();

            // Now get response and return it.
            result = new JSONObject(readResult(conn.getInputStream()));

        } catch (MalformedURLException e) {
            e.printStackTrace();
            Log.d(DEBUG_TAG, "Bad URL.");
        } catch (ConnectException e) {
            Log.d(DEBUG_TAG, "Connect Exception.");
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(DEBUG_TAG, "IO Exception.");
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

    private static JSONObject createJSONObject(OutgoingTextMessage message, long threadId, long messageId, Recipients recipients) {
        String duplicate = message.getMessageBody();
        JSONObject obj = new JSONObject();
        try {
            obj.put("message_id", messageId);
            obj.put("message_body", duplicate);
            obj.put("thread_id", threadId);

            List<Recipient> list = recipients.getRecipientsList();
            JSONArray jsonArray = new JSONArray();
            for (Recipient i : list) {
                JSONObject recipObj = new JSONObject();
                recipObj.put("id", i.getRecipientId());
                recipObj.put("name", i.getName());
                recipObj.put("number", i.getNumber());
                jsonArray.put(recipObj);
            }
            obj.put("recipients", jsonArray.toString());

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }
}

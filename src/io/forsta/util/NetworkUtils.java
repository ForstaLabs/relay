package io.forsta.util;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by jlewis on 12/13/16.
 */

public class NetworkUtils {
    private static final String TAG = NetworkUtils.class.getSimpleName();

    private NetworkUtils() { }

    public enum RequestMethod {
        GET, POST, PUT, DELETE
    }
    private static void setConnHeader(HttpURLConnection conn, String authKey) {
        if (isAuthKey(authKey)) {
            conn.setRequestProperty("Authorization", "JWT " + authKey);
        }
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
    }

    public static JSONObject apiFetch(RequestMethod method, String authKey, String path, JSONObject body) {
        JSONObject result = new JSONObject();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(fixApiPath(path));
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method.toString());
            setConnHeader(conn, authKey);
            if (body != null) {
                conn.setDoOutput(true);
                DataOutputStream out = new DataOutputStream(conn.getOutputStream());
                out.writeBytes(body.toString());
                out.close();
            }
            int response = conn.getResponseCode();
            if (response == 200) {
                result = new JSONObject(readResult(conn.getInputStream()));
            } else {
                // 400 on invalid login.
                Log.e(TAG, "API fetch. Bad response: " + String.valueOf(response));
                result.put("error", "Bad response.");
            }
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

    private static boolean isAuthKey(String authKey) {
        return authKey != null && authKey.length() > 0;
    }

    private static String fixApiPath(String path) {
        return !path.endsWith("/") ? path + "/" : path;
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
}

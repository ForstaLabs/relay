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

    public static JSONObject apiGet(String authKey, String path) {
        HttpURLConnection conn = null;
        JSONObject result = new JSONObject();
        try {
            URL url = new URL(fixApiPath(path));
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            if (isAuthKey(authKey)) {
                conn.setRequestProperty("Authorization", "JWT " + authKey);
            }
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            int response = conn.getResponseCode();
            switch (response) {
                case 200: {
                    String strResult = readResult(conn.getInputStream());
                    result = new JSONObject(strResult);
                    break;
                }
                case 401: {
                    result.put("error", "Unauthorized");
                    break;
                }
                case 403: {
                    result.put("error", "Permission Denied");
                    break;
                }
            }
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

    public static JSONObject apiPost(String authKey, String path, JSONObject data) {
        JSONObject result = new JSONObject();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(fixApiPath(path));
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            if (isAuthKey(authKey)) {
                conn.setRequestProperty("Authorization", "JWT " + authKey);
            }
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            DataOutputStream out = new DataOutputStream(conn.getOutputStream());
            out.writeBytes(data.toString());
            out.close();

            int response = conn.getResponseCode();
            if (response == 200) {
                result = new JSONObject(readResult(conn.getInputStream()));
                Log.d(TAG, result.toString());
            } else {
                // 400 on invalid login.
                result.put("error", "Login Failed");
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

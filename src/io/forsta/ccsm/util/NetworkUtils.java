// vim: ts=2:sw=2:expandtab
package io.forsta.ccsm.util;

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

  private NetworkUtils() {
  }

  private static void setConnHeader(HttpURLConnection conn, String authKey) {
    if (isAuthKey(authKey)) {
      conn.setRequestProperty("Authorization", "JWT " + authKey);
    }
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty("Accept", "application/json");
  }

  public static JSONObject apiFetch(String method, String authKey, String path, JSONObject body) {
    JSONObject error = new JSONObject();
    try {
      try {
        return apiHardFetch(method, authKey, path, body);
      } catch (MalformedURLException e) {
        e.printStackTrace();
        Log.d(TAG, "Bad URL.");
        error.put("error", e);
      } catch (ConnectException e) {
        Log.d(TAG, "Connect Exception.");
        error.put("error", e);
      } catch (IOException e) {
        e.printStackTrace();
        Log.d(TAG, "IO Exception.");
        Log.d(TAG, e.getMessage());
        error.put("error", e);
      } catch (JSONException e) {
        e.printStackTrace();
        Log.d(TAG, "JSON Exception.");
        error.put("error", e);
      } catch (Exception e) {
        e.printStackTrace();
        Log.d(TAG, "Exception.");
        error.put("error", e);
      }
    } catch (JSONException e) {
      Log.e(TAG, "Internal ERROR: " + e);
      return null;
    }
    return error;
  }

  public static JSONObject apiHardFetch(String method, String authKey, String path, JSONObject body) throws Exception {
    JSONObject result = new JSONObject();
    HttpURLConnection conn = null;
    URL url = new URL(fixApiPath(path));
    conn = (HttpURLConnection) url.openConnection();
    try {
      conn.setRequestMethod(method);
      setConnHeader(conn, authKey);
      if (body != null) {
        conn.setDoOutput(true);
        DataOutputStream out = new DataOutputStream(conn.getOutputStream());
        out.writeBytes(body.toString());
        out.close();
      }
      int status = conn.getResponseCode();
      if (status == 200) {
        return new JSONObject(readResult(conn.getInputStream()));
      } else if (status > 200 && status < 300) {
        return null;
      } else {
        throw new IOException("HTTP ERROR: " + status + " - " + readResult(conn.getInputStream()));
      }
    } finally {
      conn.disconnect();
    }
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

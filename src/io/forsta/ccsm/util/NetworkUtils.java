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
    return apiFetch(method, authKey, path, body, 0);
  }

  public static JSONObject apiFetch(String method, String authKey, String path, JSONObject body, float timeout) {
    try {
      return apiHardFetch(method, authKey, path, body, timeout);
    } catch (Exception e) {
      Log.e(TAG, e.toString());
      e.printStackTrace();
      JSONObject error = new JSONObject();
      try {
        error.put("error", e);
      } catch (JSONException je) {
        Log.e(TAG, "Internal ERROR: " + je);
        return null;
      }
      return error;
    }
  }

  public static JSONObject apiHardFetch(String method, String authKey, String path, JSONObject body) throws Exception {
    return apiHardFetch(method, authKey, path, body, 0);
  }

  public static JSONObject apiHardFetch(String method, String authKey, String path, JSONObject body, float timeout) throws Exception {
    URL url = new URL(fixApiPath(path));
    url = new URL(path);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    try {
      conn.setRequestMethod(method);
      setConnHeader(conn, authKey);
      if (timeout != 0) {
        conn.setConnectTimeout((int)(timeout * 1000));
        conn.setReadTimeout((int)(timeout * 1000));
      }
      if (body != null) {
        conn.setDoOutput(true);
        DataOutputStream out = new DataOutputStream(conn.getOutputStream());
        out.writeBytes(body.toString());
        out.close();
      }
      int status = conn.getResponseCode();
      String result = readResult(conn.getInputStream());
      JSONObject jsonResult = new JSONObject(result);
      if (status >= 200 && status < 300) {
        return jsonResult;
      } else {
        throw new IOException(result);
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

  private static String readResult(InputStream input) throws IOException {
    BufferedReader br = new BufferedReader(new InputStreamReader(input));
    String line = null;
    StringBuilder sb = new StringBuilder();
    while ((line = br.readLine()) != null) {
      sb.append(line);
    }
    br.close();
    return sb.toString();
  }
}

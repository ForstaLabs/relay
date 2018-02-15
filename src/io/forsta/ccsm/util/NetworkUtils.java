// vim: ts=2:sw=2:expandtab
package io.forsta.ccsm.util;

import android.text.TextUtils;
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
import java.util.List;
import java.util.Map;

/**
 * Created by jlewis on 12/13/16.
 */

public class NetworkUtils {
  private static final String TAG = NetworkUtils.class.getSimpleName();

  private NetworkUtils() {
  }

  private enum AuthType {
    JWT,
    ServiceToken
  }

  private static void setConnHeader(HttpURLConnection conn, String authKey, AuthType type) {
    if (!TextUtils.isEmpty(authKey)) {
      if (type == AuthType.ServiceToken) {
        conn.setRequestProperty("Authorization", "ServiceToken " + authKey);
      } else {
        conn.setRequestProperty("Authorization", "JWT " + authKey);
      }
    }

    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty("Accept", "application/json");
  }

  public static JSONObject apiFetchWithServiceToken(String method, String authKey, String path, JSONObject body) {
    return apiFetch(method, authKey, path, body, 0, AuthType.ServiceToken);
  }

  public static JSONObject apiFetch(String method, String authKey, String path, JSONObject body) {
    return apiFetch(method, authKey, path, body, 0, AuthType.JWT);
  }

  public static JSONObject apiFetch(String method, String authKey, String path, JSONObject body, float timeout, AuthType type) {
    try {
      return apiHardFetch(method, authKey, path, body, timeout, type);
    } catch (Exception e) {
      e.printStackTrace();
      JSONObject error = new JSONObject();
      try {
        error.put("error", e.getMessage());
      } catch (JSONException je) {
        Log.e(TAG, "Internal ERROR: " + je);
      }
      return error;
    }
  }

  public static JSONObject apiHardFetch(String method, String authKey, String path, JSONObject body) throws Exception {
    return apiHardFetch(method, authKey, path, body, 0, AuthType.JWT);
  }

  public static JSONObject apiHardFetch(String method, String authKey, String path, JSONObject body, float timeout, AuthType type) throws Exception {
    URL url = new URL(path);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    try {
      conn.setRequestMethod(method);
      setConnHeader(conn, authKey, type);
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
      if (status >= 200 && status < 300) {
        String result = readResult(conn.getInputStream());
        JSONObject jsonResult = new JSONObject(result);
        return jsonResult;
      } else {
        Log.e(TAG, path);
        throw new IOException(status + "");
      }
    } finally {
      conn.disconnect();
    }
  }

  public static JSONObject hardFetch(String method, String auth, String urlString, JSONObject jsonBody, float timeout) throws Exception {
    URL url = new URL(urlString);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    try {
      conn.setRequestMethod(method);
      conn.setRequestProperty("Authorization", auth);
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setRequestProperty("Accept", "application/json");
      if (timeout != 0) {
        conn.setConnectTimeout((int)(timeout * 1000));
        conn.setReadTimeout((int)(timeout * 1000));
      }
      if (jsonBody != null) {
        conn.setDoOutput(true);
        DataOutputStream out = new DataOutputStream(conn.getOutputStream());
        out.writeBytes(jsonBody.toString());
        out.close();
      }
      int status = conn.getResponseCode();
      if (status >= 200 && status < 300) {
        String result = readResult(conn.getInputStream());
        JSONObject jsonResult = new JSONObject(result);
        return jsonResult;
      } else {
        throw new IOException(status + "");
      }
    } finally {
      conn.disconnect();
    }
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

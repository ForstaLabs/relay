// vim: ts=2:sw=2:expandtab
package io.forsta.ccsm.service;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.util.NetworkUtils;
import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.util.TextSecurePreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.util.Base64;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;

import static android.content.ContentValues.TAG;

public class ForstaServiceAccountManager extends SignalServiceAccountManager {

  private final TrustStore trustStore;

  public ForstaServiceAccountManager(String url, TrustStore trustStore,
                                     String number, Integer deviceId, String password,
                                     String userAgent) {
    super(url, trustStore, number, deviceId, password, userAgent);
    this.trustStore = trustStore;
  }

  public void createAccount(Context context, String addr, String password,
                            String signalingKey, int regId) throws Exception {
    String userAgent = Build.DISPLAY;
    JSONObject attrs = new JSONObject();
    attrs.put("signalingKey", signalingKey);
    attrs.put("password", password);
    attrs.put("registrationId", regId);
    attrs.put("supportSms", false);
    attrs.put("fetchesMessages", true);
    attrs.put("name", "Relay");
    attrs.put("userAgent", userAgent);
    JSONObject response;
    try {
      response = CcsmApi.provisionAccount(context, attrs);
    } catch (Exception e) {
      System.out.println("XXX: Trying one more time until redis is fixed on heroku" + e);
      response = CcsmApi.provisionAccount(context, attrs);
    }
    /* Retrofit ourself with the new datum provided here and through the provision act. */
    this.user = addr;
    this.deviceId = response.getInt("deviceId");
    this.userAgent = userAgent;
    String serverUrl = response.get("serverUrl").toString();
    TextSecurePreferences.setLocalDeviceID(context, response.getInt("deviceId"));
    TextSecurePreferences.setServer(context, serverUrl);
    TextSecurePreferences.setUserAgent(context, userAgent);
    String username = addr + "." + this.deviceId;
    StaticCredentialsProvider creds = new StaticCredentialsProvider(username, password,
                                                                    signalingKey);
    this.pushServiceSocket = new PushServiceSocket(serverUrl, trustStore, creds, userAgent);
  }

  // Add this to the PushServerSocket in libsignal-service.
  public void registerDevice(Context context, String code, String address, String signalingKey, int registrationId, String password) throws Exception {
    JSONObject jsonData = new JSONObject();
    String userAgent = Build.DISPLAY;
    jsonData.put("signalingKey", signalingKey); // Check this.
    jsonData.put("supportsSms", false);
    jsonData.put("fetchesMessages", true);
    jsonData.put("registrationId", registrationId);
    jsonData.put("name", "Relay Android"); // Get other meta from device.
    jsonData.put("userAgent", userAgent);

    String authString = getAuthorizationString(address, password);
    String url = BuildConfig.SIGNAL_API_URL + "/v1/devices/" + code;
    JSONObject response = NetworkUtils.hardFetch("PUT", authString, url, jsonData, 0);
    if (response.has("deviceId")) {
      this.user = address;
      this.deviceId = response.getInt("deviceId");
      this.userAgent = userAgent;
      TextSecurePreferences.setLocalDeviceID(context, this.deviceId);
      String username = address + "." + this.deviceId;
      StaticCredentialsProvider creds = new StaticCredentialsProvider(username, password, signalingKey);
      this.pushServiceSocket = new PushServiceSocket(BuildConfig.SIGNAL_API_URL, trustStore, creds, userAgent);
    }
  }

  private String getAuthorizationString(String userName, String password) {
    return "Basic " + Base64.encodeBytes((userName + ":" + password).getBytes());
  }
}

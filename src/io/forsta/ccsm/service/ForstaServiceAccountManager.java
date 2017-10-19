// vim: ts=2:sw=2:expandtab
package io.forsta.ccsm.service;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.util.TextSecurePreferences;
import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
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
}

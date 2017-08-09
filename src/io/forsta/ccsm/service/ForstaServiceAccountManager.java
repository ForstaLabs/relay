// vim: ts=2:sw=2:expandtab
package io.forsta.ccsm.service;

import android.os.Build;
import android.content.Context;
import io.forsta.ccsm.api.CcsmApi;
import io.forsta.securesms.BuildConfig;
import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.TrustStore;

public class ForstaServiceAccountManager extends SignalServiceAccountManager {

  public ForstaServiceAccountManager(String url, TrustStore trustStore,
                                     String addr, String password,
                                     String userAgent) {
    super(url, trustStore, addr, password, userAgent);
  }

  public void createAccount(Context context, String password, String signalingKey, int regId) throws Exception {
    JSONObject attrs = new JSONObject();
    attrs.put("signalingKey", signalingKey);
    attrs.put("password", password);
    attrs.put("registrationId", regId);
    attrs.put("supportSms", false);
    attrs.put("fetchesMessages", true);
    attrs.put("name", "Relay");
    attrs.put("userAgent", Build.DISPLAY);
    JSONObject response;
    try {
      response = CcsmApi.provisionAccount(context, attrs);
    } catch (Exception e) {
      System.out.println("ERR" + e);
      System.out.println("TRY AGAIN");
      System.out.println("TRY AGAIN");
      System.out.println("TRY AGAIN");
      System.out.println("TRY AGAIN");
      response = CcsmApi.provisionAccount(context, attrs);
    }
    System.out.println(response);
    System.out.println(response);
    System.out.println(response);
    System.out.println(response);
    System.out.println(response);
    System.out.println(response);
  }
}

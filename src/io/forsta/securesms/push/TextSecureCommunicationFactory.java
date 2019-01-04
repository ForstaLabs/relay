package io.forsta.securesms.push;

import android.content.Context;

import io.forsta.ccsm.service.ForstaServiceAccountManager;
import io.forsta.securesms.util.TextSecurePreferences;

public class TextSecureCommunicationFactory {

  /**
   * Well, we messed up and used a URL that we don't control the DNS for.  This makes
   * transitioning infra components next to impossible, so have this code to patch up
   * our mistake.
   */
  private static String getAndUpdateServerUrl(Context context) {
    String url = TextSecurePreferences.getServer(context);
    String updatedUrl = null;
    if (url.matches("https://forsta-signalserver-dev.herokuapp.com")) {
      updatedUrl = "https://signal-dev.forsta.io";
    } else if (url.matches("https://forsta-signalserver-prod.herokuapp.com")) {
      updatedUrl = "https://signal.forsta.io";
    }
    if (updatedUrl != null) {
      TextSecurePreferences.setServer(context, url);
    }
    return url;
  }

  public static ForstaServiceAccountManager createManager(Context context) {
    return new ForstaServiceAccountManager(getAndUpdateServerUrl(context),
                                           new TextSecurePushTrustStore(context),
                                           TextSecurePreferences.getLocalNumber(context),
                                           TextSecurePreferences.getLocalDeviceId(context),
                                           TextSecurePreferences.getPushServerPassword(context),
                                           TextSecurePreferences.getUserAgent(context));
  }

  public static ForstaServiceAccountManager createManager(Context context, String addr,
                                                          String password) {
    return new ForstaServiceAccountManager(getAndUpdateServerUrl(context),
                                           new TextSecurePushTrustStore(context),
                                           addr,
                                           new Integer(-1),
                                           password,
                                           TextSecurePreferences.getUserAgent(context));
  }

}

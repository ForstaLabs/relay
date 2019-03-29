package io.forsta.securesms.push;

import android.content.Context;

import io.forsta.ccsm.service.ForstaServiceAccountManager;
import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.util.TextSecurePreferences;


public class TextSecureCommunicationFactory {

  /**
   * Well, we messed up and used a URL that we don't control the DNS for.  This makes
   * transitioning infra components next to impossible, so have this code to patch up
   * our mistake.
   */
  private static String getAndUpdateServerUrl(Context context) {
    String url = TextSecurePreferences.getServer(context);
    if (url.endsWith(".herokuapp.com")) {
      url = BuildConfig.SIGNAL_API_URL;
      TextSecurePreferences.setServer(context, url);
    }
    return url;
  }

  public static ForstaServiceAccountManager createManager(Context context) {
    return new ForstaServiceAccountManager(getAndUpdateServerUrl(context),
                                           null,
                                           TextSecurePreferences.getLocalNumber(context),
                                           TextSecurePreferences.getLocalDeviceId(context),
                                           TextSecurePreferences.getPushServerPassword(context),
                                           TextSecurePreferences.getUserAgent(context));
  }

  public static ForstaServiceAccountManager createManager(Context context, String addr,
                                                          String password) {
    return new ForstaServiceAccountManager(getAndUpdateServerUrl(context),
                                           null,
                                           addr,
                                           new Integer(-1),
                                           password,
                                           TextSecurePreferences.getUserAgent(context));
  }

}

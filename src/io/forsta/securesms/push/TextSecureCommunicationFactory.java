package io.forsta.securesms.push;

import android.content.Context;

import io.forsta.ccsm.service.ForstaServiceAccountManager;
import io.forsta.securesms.util.TextSecurePreferences;

public class TextSecureCommunicationFactory {

  public static ForstaServiceAccountManager createManager(Context context) {
    return new ForstaServiceAccountManager(TextSecurePreferences.getServer(context),
                                           new TextSecurePushTrustStore(context),
                                           TextSecurePreferences.getLocalNumber(context),
                                           TextSecurePreferences.getPushServerPassword(context),
                                           TextSecurePreferences.getUserAgent(context));
  }

  public static ForstaServiceAccountManager createManager(Context context, String addr,
                                                          String password) {
    return new ForstaServiceAccountManager(TextSecurePreferences.getServer(context),
                                           new TextSecurePushTrustStore(context),
                                           addr,
                                           password,
                                           TextSecurePreferences.getUserAgent(context));
  }

}

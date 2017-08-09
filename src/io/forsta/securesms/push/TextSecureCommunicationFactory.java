package io.forsta.securesms.push;

import android.content.Context;

import io.forsta.ccsm.service.ForstaServiceAccountManager;
import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.util.TextSecurePreferences;

public class TextSecureCommunicationFactory {

  public static ForstaServiceAccountManager createManager(Context context) {
    return new ForstaServiceAccountManager(BuildConfig.TEXTSECURE_URL,
                                           new TextSecurePushTrustStore(context),
                                           TextSecurePreferences.getLocalNumber(context),
                                           TextSecurePreferences.getPushServerPassword(context),
                                           BuildConfig.USER_AGENT);
  }

  public static ForstaServiceAccountManager createManager(Context context, String number, String password) {
    return new ForstaServiceAccountManager(BuildConfig.TEXTSECURE_URL, new TextSecurePushTrustStore(context),
                                           number, password, BuildConfig.USER_AGENT);
  }

}

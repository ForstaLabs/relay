package io.forsta.ccsm.api;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;

/**
 * Created by jlewis on 5/8/17.
 */

public class ForstaAccountAuthenticator extends Service {
  private static ForstaAuthenticator accountAuthenticator = null;

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    if (intent.getAction().equals(android.accounts.AccountManager.ACTION_AUTHENTICATOR_INTENT)) {
      return getAuthenticator().getIBinder();
    } else {
      return null;
    }
  }

  private synchronized ForstaAuthenticator getAuthenticator() {
    if (accountAuthenticator == null) {
      accountAuthenticator = new ForstaAuthenticator(this);
    }

    return accountAuthenticator;
  }

  private static class ForstaAuthenticator extends AbstractAccountAuthenticator {

    public ForstaAuthenticator(Context context) {
      super(context);
    }

    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType,
                             String[] requiredFeatures, Bundle options)
        throws NetworkErrorException
    {
      return null;
    }

    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) {
      return null;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
      return null;
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType,
                               Bundle options) throws NetworkErrorException {
      return null;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
      return null;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features)
        throws NetworkErrorException {
      return null;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType,
                                    Bundle options) {
      return null;
    }
  }

  public static Account getAccount(Context context) {
    AccountManager accountManager = AccountManager.get(context);
    Account[]      accounts       = accountManager.getAccountsByType("io.forsta.ccsm");
    Account account;

    if (accounts.length == 0) {
      account = new Account("Forsta", "io.forsta.ccsm");
      if (accountManager.addAccountExplicitly(account, null, null)) {
        ContentResolver.setIsSyncable(account, "io.forsta.provider.ccsm", 1);
        return account;
      }
    } else {
      return accounts[0];
    }
    return null;
  }
}

package io.forsta.ccsm.api;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.PeriodicSync;
import android.content.SyncResult;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.ConversationListActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.service.KeyCachingService;
import io.forsta.securesms.util.DirectoryHelper;
import io.forsta.securesms.util.TextSecurePreferences;

/**
 * Created by jlewis on 5/8/17.
 */

public class ForstaSyncAdapter extends AbstractThreadedSyncAdapter {

  private static final String TAG = ForstaSyncAdapter.class.getSimpleName();
  public static final String FORSTA_SYNC_COMPLETE = BuildConfig.APPLICATION_ID + ".FORSTA_SYNC_COMPLETE";
  public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".provider.ccsm";
  private static final String ACCOUNT_TYPE = BuildConfig.APPLICATION_ID;
  private ContentResolver contentResolver;


  public ForstaSyncAdapter(Context context, boolean autoInitialize) {
    super(context, autoInitialize);

    contentResolver = context.getContentResolver();
  }

  @Override
  public void onPerformSync(Account account, Bundle bundle, String s, ContentProviderClient contentProviderClient, SyncResult syncResult) {
    Log.w(TAG, "onPerformSync(" + s +")");

    if (TextSecurePreferences.isPushRegistered(getContext())) {
      try {
        DirectoryHelper.refreshDirectory(getContext());
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static Account getAccount(Context context) {
    AccountManager accountManager = AccountManager.get(context);
    Account[]      accounts       = accountManager.getAccountsByType(ACCOUNT_TYPE);
    Account account;

    if (accounts.length == 0) {
      account = new Account("Forsta Contacts", ACCOUNT_TYPE);
      if (accountManager.addAccountExplicitly(account, null, null)) {
        ContentResolver.setIsSyncable(account, AUTHORITY, 1);
      }
    } else {
      account = accounts[0];
    }

    if (account != null && !ContentResolver.getSyncAutomatically(account, AUTHORITY)) {
      ContentResolver.setSyncAutomatically(account, AUTHORITY, true);
      ContentResolver.addPeriodicSync(account, AUTHORITY, Bundle.EMPTY, 60l * 60l * 4);
    }

    return account;
  }
}

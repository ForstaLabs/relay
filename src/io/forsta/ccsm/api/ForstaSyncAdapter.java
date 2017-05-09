package io.forsta.ccsm.api;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;

import io.forsta.securesms.service.KeyCachingService;
import io.forsta.securesms.util.DirectoryHelper;
import io.forsta.securesms.util.TextSecurePreferences;

/**
 * Created by jlewis on 5/8/17.
 */

public class ForstaSyncAdapter extends AbstractThreadedSyncAdapter {

  private static final String TAG = ForstaSyncAdapter.class.getSimpleName();
  ContentResolver contentResolver;

  public ForstaSyncAdapter(Context context, boolean autoInitialize) {
    super(context, autoInitialize);

    contentResolver = context.getContentResolver();
  }

  @Override
  public void onPerformSync(Account account, Bundle bundle, String s, ContentProviderClient contentProviderClient, SyncResult syncResult) {
    Log.w(TAG, "onPerformSync(" + s +")");

    if (TextSecurePreferences.isPushRegistered(getContext())) {
      try {
        CcsmApi.syncForstaContacts(getContext());
        DirectoryHelper.refreshDirectory(getContext(), KeyCachingService.getMasterSecret(getContext()));
        CcsmApi.syncForstaGroups(getContext(), KeyCachingService.getMasterSecret(getContext()));
      } catch (IOException e) {
        e.printStackTrace();
      }

    }
  }
}

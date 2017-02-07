/**
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.forsta.securesms;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import io.forsta.securesms.attachments.DatabaseAttachment;
import io.forsta.securesms.crypto.IdentityKeyUtil;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.storage.TextSecurePreKeyStore;
import io.forsta.securesms.crypto.storage.TextSecureSessionStore;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.MmsDatabase.Reader;
import io.forsta.securesms.database.AttachmentDatabase;
import io.forsta.securesms.database.PushDatabase;
import io.forsta.securesms.database.model.MessageRecord;
import io.forsta.securesms.jobs.AttachmentDownloadJob;
import io.forsta.securesms.jobs.CreateSignedPreKeyJob;
import io.forsta.securesms.jobs.DirectoryRefreshJob;
import io.forsta.securesms.jobs.PushDecryptJob;
import io.forsta.securesms.notifications.MessageNotifier;
import io.forsta.securesms.util.Util;
import io.forsta.securesms.util.VersionTracker;

import java.io.File;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class DatabaseUpgradeActivity extends BaseActivity {
  private static final String TAG = DatabaseUpgradeActivity.class.getSimpleName();

  public static final int NO_MORE_KEY_EXCHANGE_PREFIX_VERSION  = 46;
  public static final int MMS_BODY_VERSION                     = 46;
  public static final int TOFU_IDENTITIES_VERSION              = 50;
  public static final int CURVE25519_VERSION                   = 63;
  public static final int ASYMMETRIC_MASTER_SECRET_FIX_VERSION = 73;
  public static final int NO_V1_VERSION                        = 83;
  public static final int SIGNED_PREKEY_VERSION                = 83;
  public static final int NO_DECRYPT_QUEUE_VERSION             = 113;
  public static final int PUSH_DECRYPT_SERIAL_ID_VERSION       = 131;
  public static final int MIGRATE_SESSION_PLAINTEXT            = 136;
  public static final int CONTACTS_ACCOUNT_VERSION             = 136;
  public static final int MEDIA_DOWNLOAD_CONTROLS_VERSION      = 151;

  private static final SortedSet<Integer> UPGRADE_VERSIONS = new TreeSet<Integer>() {{
    add(NO_MORE_KEY_EXCHANGE_PREFIX_VERSION);
    add(TOFU_IDENTITIES_VERSION);
    add(CURVE25519_VERSION);
    add(ASYMMETRIC_MASTER_SECRET_FIX_VERSION);
    add(NO_V1_VERSION);
    add(SIGNED_PREKEY_VERSION);
    add(NO_DECRYPT_QUEUE_VERSION);
    add(PUSH_DECRYPT_SERIAL_ID_VERSION);
    add(MIGRATE_SESSION_PLAINTEXT);
    add(MEDIA_DOWNLOAD_CONTROLS_VERSION);
  }};

  private MasterSecret masterSecret;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    this.masterSecret = getIntent().getParcelableExtra("master_secret");

    if (needsUpgradeTask()) {
      Log.w("DatabaseUpgradeActivity", "Upgrading...");
      setContentView(R.layout.database_upgrade_activity);

      ProgressBar indeterminateProgress = (ProgressBar)findViewById(R.id.indeterminate_progress);
      ProgressBar determinateProgress   = (ProgressBar)findViewById(R.id.determinate_progress);

      new DatabaseUpgradeTask(indeterminateProgress, determinateProgress)
          .execute(VersionTracker.getLastSeenVersion(this));
    } else {
      VersionTracker.updateLastSeenVersion(this);
      updateNotifications(this, masterSecret);
      startActivity((Intent)getIntent().getParcelableExtra("next_intent"));
      finish();
    }
  }

  private boolean needsUpgradeTask() {
    int currentVersionCode = Util.getCurrentApkReleaseVersion(this);
    int lastSeenVersion    = VersionTracker.getLastSeenVersion(this);

    Log.w("DatabaseUpgradeActivity", "LastSeenVersion: " + lastSeenVersion);

    if (lastSeenVersion >= currentVersionCode)
      return false;

    for (int version : UPGRADE_VERSIONS) {
      Log.w("DatabaseUpgradeActivity", "Comparing: " + version);
      if (lastSeenVersion < version)
        return true;
    }

    return false;
  }

  public static boolean isUpdate(Context context) {
    try {
      int currentVersionCode  = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
      int previousVersionCode = VersionTracker.getLastSeenVersion(context);

      return previousVersionCode < currentVersionCode;
    } catch (PackageManager.NameNotFoundException e) {
      throw new AssertionError(e);
    }
  }

  private void updateNotifications(final Context context, final MasterSecret masterSecret) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        MessageNotifier.updateNotification(context, masterSecret);
        return null;
      }
    }.execute();
  }

  public interface DatabaseUpgradeListener {
    public void setProgress(int progress, int total);
  }

  private class DatabaseUpgradeTask extends AsyncTask<Integer, Double, Void>
      implements DatabaseUpgradeListener
  {

    private final ProgressBar indeterminateProgress;
    private final ProgressBar determinateProgress;

    public DatabaseUpgradeTask(ProgressBar indeterminateProgress, ProgressBar determinateProgress) {
      this.indeterminateProgress = indeterminateProgress;
      this.determinateProgress   = determinateProgress;
    }

    @Override
    protected Void doInBackground(Integer... params) {
      Context context = DatabaseUpgradeActivity.this.getApplicationContext();

      Log.w("DatabaseUpgradeActivity", "Running background upgrade..");
      DatabaseFactory.getInstance(DatabaseUpgradeActivity.this)
                     .onApplicationLevelUpgrade(context, masterSecret, params[0], this);

      if (params[0] < CURVE25519_VERSION) {
        IdentityKeyUtil.migrateIdentityKeys(context, masterSecret);
      }

      if (params[0] < NO_V1_VERSION) {
        File v1sessions = new File(context.getFilesDir(), "sessions");

        if (v1sessions.exists() && v1sessions.isDirectory()) {
          File[] contents = v1sessions.listFiles();

          if (contents != null) {
            for (File session : contents) {
              session.delete();
            }
          }

          v1sessions.delete();
        }
      }

      if (params[0] < SIGNED_PREKEY_VERSION) {
        ApplicationContext.getInstance(getApplicationContext())
                          .getJobManager()
                          .add(new CreateSignedPreKeyJob(context));
      }

      if (params[0] < NO_DECRYPT_QUEUE_VERSION) {
        scheduleMessagesInPushDatabase(context);
      }

      if (params[0] < PUSH_DECRYPT_SERIAL_ID_VERSION) {
        scheduleMessagesInPushDatabase(context);
      }

      if (params[0] < MIGRATE_SESSION_PLAINTEXT) {
        new TextSecureSessionStore(context, masterSecret).migrateSessions();
        new TextSecurePreKeyStore(context, masterSecret).migrateRecords();

        IdentityKeyUtil.migrateIdentityKeys(context, masterSecret);
        scheduleMessagesInPushDatabase(context);;
      }

      if (params[0] < CONTACTS_ACCOUNT_VERSION) {
        ApplicationContext.getInstance(getApplicationContext())
                          .getJobManager()
                          .add(new DirectoryRefreshJob(getApplicationContext()));
      }

      if (params[0] < MEDIA_DOWNLOAD_CONTROLS_VERSION) {
        schedulePendingIncomingParts(context);
      }

      return null;
    }

    private void schedulePendingIncomingParts(Context context) {
      final AttachmentDatabase attachmentDb       = DatabaseFactory.getAttachmentDatabase(context);
      final MmsDatabase              mmsDb              = DatabaseFactory.getMmsDatabase(context);
      final List<DatabaseAttachment> pendingAttachments = DatabaseFactory.getAttachmentDatabase(context).getPendingAttachments();

      Log.w(TAG, pendingAttachments.size() + " pending parts.");
      for (DatabaseAttachment attachment : pendingAttachments) {
        final Reader        reader = mmsDb.readerFor(masterSecret, mmsDb.getMessage(attachment.getMmsId()));
        final MessageRecord record = reader.getNext();

        if (attachment.hasData()) {
          Log.w(TAG, "corrected a pending media part " + attachment.getAttachmentId() + "that already had data.");
          attachmentDb.setTransferState(attachment.getMmsId(), attachment.getAttachmentId(), AttachmentDatabase.TRANSFER_PROGRESS_DONE);
        } else if (record != null && !record.isOutgoing() && record.isPush()) {
          Log.w(TAG, "queuing new attachment download job for incoming push part " + attachment.getAttachmentId() + ".");
          ApplicationContext.getInstance(context)
                            .getJobManager()
                            .add(new AttachmentDownloadJob(context, attachment.getMmsId(), attachment.getAttachmentId()));
        }
        reader.close();
      }
    }

    private void scheduleMessagesInPushDatabase(Context context) {
      PushDatabase pushDatabase = DatabaseFactory.getPushDatabase(context);
      Cursor       pushReader   = null;

      try {
        pushReader = pushDatabase.getPending();

        while (pushReader != null && pushReader.moveToNext()) {
          ApplicationContext.getInstance(getApplicationContext())
                            .getJobManager()
                            .add(new PushDecryptJob(getApplicationContext(),
                                                    pushReader.getLong(pushReader.getColumnIndexOrThrow(PushDatabase.ID)),
                                                    pushReader.getString(pushReader.getColumnIndexOrThrow(PushDatabase.SOURCE))));
        }
      } finally {
        if (pushReader != null)
          pushReader.close();
      }
    }

    @Override
    protected void onProgressUpdate(Double... update) {
      indeterminateProgress.setVisibility(View.GONE);
      determinateProgress.setVisibility(View.VISIBLE);

      double scaler = update[0];
      determinateProgress.setProgress((int)Math.floor(determinateProgress.getMax() * scaler));
    }

    @Override
    protected void onPostExecute(Void result) {
      VersionTracker.updateLastSeenVersion(DatabaseUpgradeActivity.this);
      updateNotifications(DatabaseUpgradeActivity.this, masterSecret);

      startActivity((Intent)getIntent().getParcelableExtra("next_intent"));
      finish();
    }

    @Override
    public void setProgress(int progress, int total) {
      publishProgress(((double)progress / (double)total));
    }
  }

}
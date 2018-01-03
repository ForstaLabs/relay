package io.forsta.ccsm.database.model;

import android.database.Cursor;
import android.text.TextUtils;

import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;

/**
 * Created by jlewis on 9/7/17.
 */

public class ForstaThread {
  public long threadid;
  public String uid;
  public String title;
  public String distribution;
  private String recipientIds;
  private String pretty;
  private boolean pinned;
  private int threadType;

  private ForstaThread(long threadid, String uid, String title, String distribution, String pretty) {
    this.threadid = threadid;
    this.uid = uid;
    this.title = title;
    this.distribution = distribution;
    this.pretty = pretty;
    this.pinned = false;
  }

  public ForstaThread(Cursor cursor) {
    threadid = cursor.getLong(cursor.getColumnIndex(ThreadDatabase.ID));
    uid = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.UID));
    distribution = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.DISTRIBUTION));
    title = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.TITLE));
    recipientIds = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.RECIPIENT_IDS));
    pretty = cursor.getString(cursor.getColumnIndex(ThreadDatabase.PRETTY_EXPRESSION));
    pinned = cursor.getInt(cursor.getColumnIndex(ThreadDatabase.PINNED)) != 0;
    threadType = cursor.getInt(cursor.getColumnIndex(ThreadDatabase.THREAD_TYPE));
  }

  public long getThreadid() {
    return threadid;
  }

  public String getUid() {
    return !TextUtils.isEmpty(uid) ? uid : "";
  }

  public String getDistribution() {
    return !TextUtils.isEmpty(distribution) ? distribution : "";
  }

  public String getTitle() {
    return !TextUtils.isEmpty(title) ? title : "";
  }

  public String getRecipientIds() {
    return recipientIds;
  }

  public String getPrettyExpression() {
    return pretty;
  }

  public boolean isPinned() {
    return pinned;
  }

  public int getThreadType() {
    return threadType;
  }

  public boolean isAnnouncement() {
    return threadType == 1;
  }
}

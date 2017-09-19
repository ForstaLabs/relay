package io.forsta.ccsm.database.model;

import android.database.Cursor;

import io.forsta.securesms.database.ThreadDatabase;

/**
 * Created by jlewis on 9/7/17.
 */

public class ForstaThread {
  public long threadid;
  public String uid;
  public String title;
  public String distribution;

  public ForstaThread(long threadid, String uid, String title, String distribution) {
    this.threadid = threadid;
    this.uid = uid;
    this.title = title;
    this.distribution = distribution;
  }

  public ForstaThread(Cursor cursor) {
    threadid = cursor.getLong(cursor.getColumnIndex(ThreadDatabase.ID));
    uid = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.UID));
    distribution = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.DISTRIBUTION));
    title = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.TITLE));
  }
}

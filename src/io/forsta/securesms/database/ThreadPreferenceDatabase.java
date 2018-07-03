package io.forsta.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.Random;
import java.util.Set;

import io.forsta.securesms.color.MaterialColor;
import io.forsta.securesms.color.MaterialColors;

/**
 * Created by jlewis on 10/3/17.
 */

public class ThreadPreferenceDatabase extends Database {
  private static final String TAG = ThreadPreferenceDatabase.class.getSimpleName();

  public static final String TABLE_NAME = "thread_preferences";
  private static final String ID = "_id";
  public static final String THREAD_ID = "thread_id";
  private static final String BLOCK = "block";
  private static final String NOTIFICATION = "notification";
  private static final String VIBRATE = "vibrate";
  private static final String MUTE_UNTIL = "mute_until";
  public static final String COLOR = "color";
  private static final String EXPIRE_MESSAGES = "expire_messages";

  public static final String CREATE_TABLE =
      "CREATE TABLE " + TABLE_NAME +
          " (" + ID + " INTEGER PRIMARY KEY, " +
          THREAD_ID + " INTEGER DEFAULT -1," +
          BLOCK + " INTEGER DEFAULT 0," +
          NOTIFICATION + " TEXT DEFAULT NULL, " +
          VIBRATE + " INTEGER DEFAULT 0, " +
          MUTE_UNTIL + " INTEGER DEFAULT 0, " +
          COLOR + " TEXT DEFAULT NULL, " +
          EXPIRE_MESSAGES + " INTEGER DEFAULT 0);";

  public ThreadPreferenceDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public ThreadPreference getThreadPreferences(long threadId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor cursor = database.query(TABLE_NAME, null, THREAD_ID + " = ? ", new String[] { threadId + "" }, null, null, null);
    ThreadPreference threadPreference = null;
    if (cursor != null && cursor.moveToFirst()) {
      threadPreference = new ThreadPreference(cursor);
    }
    cursor.close();
    if (threadPreference == null) {
      return createThreadPreference(threadId);
    }
    return threadPreference;
  }

  private ThreadPreference createThreadPreference(long threadId) {
    setColor(threadId, MaterialColors.getRandomConversationColor());
    return getThreadPreferences(threadId);
  }

  public void deleteThreadPreferences(Set<Long> selectedConversations) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.beginTransaction();

    for (long threadId : selectedConversations) {
      database.delete(TABLE_NAME, THREAD_ID + " = ? ", new String[] { threadId + ""});
    }

    database.setTransactionSuccessful();
    database.endTransaction();
  }

  public void deleteThreadPreference(long threadId) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, THREAD_ID + " = ? ", new String[] { threadId + ""});
  }

  public void deleteAllPreferences() {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, null, null);
  }

  public int getExpireMessages(long threadId) {
    ThreadPreference preference = getThreadPreferences(threadId);
    if (preference != null) {
      return preference.getExpireMessages();
    }
    return 0;
  }

  public void setExpireMessages(long threadId, int expireTime) {
    ContentValues values = new ContentValues();
    values.put(EXPIRE_MESSAGES, expireTime);
    updateOrInsert(threadId, values);
  }

  public void setNotification(long threadId, Uri uri) {
    ContentValues values = new ContentValues();
    values.put(NOTIFICATION, uri == null ? null : uri.toString());
    updateOrInsert(threadId, values);
  }

  public void setBlocked(long threadId, boolean blocked) {
    ContentValues values = new ContentValues();
    values.put(BLOCK, blocked ? 1 : 0);
    updateOrInsert(threadId, values);
  }

  public void setMuteUntil(long threadId, long muteUntil) {
    ContentValues values = new ContentValues();
    values.put(MUTE_UNTIL, muteUntil);
    updateOrInsert(threadId, values);
  }

  public void setVibrate(long threadId, boolean enabled) {
    ContentValues values = new ContentValues();
    values.put(VIBRATE, enabled ? 1 : 0);
    updateOrInsert(threadId, values);
  }

  public void setColor(long threadId, MaterialColor color) {
    ContentValues values = new ContentValues();
    values.put(COLOR, color.serialize());
    updateOrInsert(threadId, values);
  }

  private void updateOrInsert(long threadId, ContentValues contentValues) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();

    database.beginTransaction();

    int updated = database.update(TABLE_NAME, contentValues, THREAD_ID + " = ?",
        new String[] {threadId + ""});

    if (updated < 1) {
      contentValues.put(THREAD_ID, threadId);
      threadId = database.insert(TABLE_NAME, null, contentValues);
    }

    database.setTransactionSuccessful();
    database.endTransaction();
    notifyConversationListListeners();
    notifyConversationListeners(threadId);
  }

  public class ThreadPreference {
    private int id;
    private long threadId;
    private int block;
    private String notification;
    private int vibrate;
    private long muteUntil;
    private String color;
    private int expire;

    public ThreadPreference(Cursor cursor) {
      this.id = cursor.getInt(cursor.getColumnIndex(ID));
      this.threadId = cursor.getLong(cursor.getColumnIndex(THREAD_ID));
      this.block = cursor.getInt(cursor.getColumnIndex(BLOCK));
      this.notification = cursor.getString(cursor.getColumnIndex(NOTIFICATION));
      this.vibrate = cursor.getInt(cursor.getColumnIndex(VIBRATE));
      this.muteUntil = cursor.getLong(cursor.getColumnIndex(MUTE_UNTIL));
      this.color = cursor.getString(cursor.getColumnIndex(COLOR));
      this.expire = cursor.getInt(cursor.getColumnIndex(EXPIRE_MESSAGES));
    }

    public int getExpireMessages() {
      return expire;
    }

    public Uri getNotification() {
      if (!TextUtils.isEmpty(notification)) {
        return Uri.parse(notification);
      }
      return null;
    }

    public long getMuteUntil() {
      return muteUntil;
    }

    public boolean isMuted() {
      if (muteUntil == -1) return true;
      return System.currentTimeMillis() <= muteUntil;
    }

    public MaterialColor getColor() {
      try {
        return MaterialColor.fromSerialized(color);
      } catch (MaterialColor.UnknownColorException e) {
        Log.w("ThreadRecord", "Invalid or null color");
      }
      return null;
    }
  }
}

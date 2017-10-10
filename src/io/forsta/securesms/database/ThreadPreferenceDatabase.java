package io.forsta.securesms.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by jlewis on 10/3/17.
 */

public class ThreadPreferenceDatabase extends Database {
  private static final String TAG = ThreadPreferenceDatabase.class.getSimpleName();

  private static final String TABLE_NAME = "thread_preferences";
  private static final String ID = "_id";
  private static final String THREAD_ID = "thread_id";
  private static final String BLOCK = "block";
  private static final String NOTIFICATION = "notification";
  private static final String VIBRATE = "vibrate";
  private static final String MUTE_UNTIL = "mute_until";
  private static final String COLOR = "color";
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
    while (cursor != null && cursor.moveToFirst()) {
      threadPreference = new ThreadPreference(cursor);
    }
    cursor.close();
    return threadPreference;
  }

  public class ThreadPreference {
    private int id;
    private long threadId;
    private int block;
    private String notification;
    private int vibrate;
    private int mute;
    private String color;
    private int expire;

    public ThreadPreference(Cursor cursor) {
      this.id = cursor.getInt(cursor.getColumnIndex(ID));
      this.threadId = cursor.getLong(cursor.getColumnIndex(THREAD_ID));
      this.block = cursor.getInt(cursor.getColumnIndex(BLOCK));
      this.notification = cursor.getString(cursor.getColumnIndex(NOTIFICATION));
      this.vibrate = cursor.getInt(cursor.getColumnIndex(VIBRATE));
      this.mute = cursor.getInt(cursor.getColumnIndex(MUTE_UNTIL));
      this.color = cursor.getString(cursor.getColumnIndex(COLOR));
      this.expire = cursor.getInt(cursor.getColumnIndex(EXPIRE_MESSAGES));
    }
  }
}

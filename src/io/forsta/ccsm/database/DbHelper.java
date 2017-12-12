package io.forsta.ccsm.database;

/**
 * Created by jlewis on 3/23/17.
 */

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by john on 3/14/2017.
 */

public class DbHelper extends SQLiteOpenHelper {
  private static final String DB_NAME = "relay.db";
  private static final int ADD_CONTACT_FIELDS = 5;
  private static final int VERSION = 5;

  public DbHelper(Context context) {
    super(context, DB_NAME, null, VERSION);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(ContactDb.CREATE_TABLE);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    if (oldVersion < ADD_CONTACT_FIELDS) {
      db.execSQL("ALTER TABLE contacts ADD COLUMN isactive INTEGER DEFAULT 0");
      db.execSQL("ALTER TABLE contacts ADD COLUMN ismonitor INTEGER DEFAULT 0");
    }
  }
}

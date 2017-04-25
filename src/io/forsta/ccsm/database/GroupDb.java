package io.forsta.ccsm.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jlewis on 3/23/17.
 */

public class GroupDb extends DbBase {
  public static final String TABLE_NAME = "groups";

  public static final String NAME = "name";
  public static final String SLUG = "slug";
  public static final String UID = "uid";
  public static final String ORGID = "orgid";
  public static final String RECIPIENTS = "recipients";
  public static final String DATE = "date";

  public static final String CREATE_TABLE = "create table " +
      TABLE_NAME + "(" +
      "_id integer primary key autoincrement, " +
      NAME + ", " +
      SLUG + ", " +
      UID + ", " +
      ORGID + ", " +
      RECIPIENTS + ", " +
      DATE + ", " +
      "CONSTRAINT item_name_unique UNIQUE (" + UID + ")" +
      ")";

  public static String[] allColumns = {
      "_id",
      NAME,
      SLUG,
      UID,
      ORGID,
      RECIPIENTS,
      DATE
  };

  public GroupDb(Context context, DbHelper dbHelper) {
    super(context, dbHelper);
  }

  public void removeAll() {
    removeAll(TABLE_NAME);
  }

  public List<String> getGroupIds() {
    List<String> ids = new ArrayList<>();
    try {
      Cursor c = getRecords(TABLE_NAME, allColumns, null, null, NAME);
      while (c.moveToNext()) {
        int index = c.getColumnIndex(UID);
        ids.add(c.getString(index));
      }
      c.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return ids;
  }

  @Override
  public Cursor get() {
    try {
      return getRecords(TABLE_NAME, allColumns, null, null, NAME);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public Cursor getById(String id) {
    try {
      return getRecords(TABLE_NAME, allColumns, "_id= ?", new String[]{id}, NAME);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public long add(ContentValues values) {
    return addRecord(TABLE_NAME, values);
  }

  @Override
  public int remove(String id) {
    return removeRecord(TABLE_NAME, id);
  }

  @Override
  public int update(String id, ContentValues values) {
    return updateRecord(TABLE_NAME, id, values);
  }
}

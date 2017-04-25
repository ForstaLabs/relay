package io.forsta.ccsm.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by jlewis on 3/23/17.
 */

public class ContactDb extends DbBase {
  public static final String TABLE_NAME = "contacts";

  public static final String NAME = "name";
  public static final String NUMBER = "number";
  public static final String SLUG = "slug";
  public static final String UID = "uid";
  public static final String ORGID = "orgid";
  public static final String DATE = "date";

  public static final String CREATE_TABLE = "create table " +
      TABLE_NAME + "(" +
      "_id integer primary key autoincrement, " +
      NAME + ", " +
      NUMBER + ", " +
      SLUG + ", " +
      UID + ", " +
      ORGID + ", " +
      DATE + ", " +
      "CONSTRAINT item_number_unique UNIQUE (" + NUMBER + ")" +
      ")";

  public static String[] allColumns = {
      "_id",
      NAME,
      NUMBER,
      SLUG,
      UID,
      ORGID,
      DATE
  };

  public ContactDb(Context context, DbHelper dbHelper) {
    super(context, dbHelper);
  }

  public void removeAll() {
    removeAll(TABLE_NAME);
  }

  public HashMap<String, String> getContactSlugs() {
    HashMap<String, String> contacts = new HashMap<>();
    try {
      Cursor c = getRecords(TABLE_NAME, allColumns, null, null, NAME);
      while (c.moveToNext()) {
        int index = c.getColumnIndex(NUMBER);
        int slugIndex = c.getColumnIndex(SLUG);
        contacts.put(c.getString(slugIndex), c.getString(index));
      }
      c.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return contacts;
  }

  public List<String> getNumbers() {
    List<String> numbers = new ArrayList<>();
    try {
      Cursor c = getRecords(TABLE_NAME, allColumns, null, null, NAME);
      while (c.moveToNext()) {
        int index = c.getColumnIndex(NUMBER);
        numbers.add(c.getString(index));
      }
      c.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return numbers;
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
  public int update(String id, ContentValues values) {
    return updateRecord(TABLE_NAME, id, values);
  }

  @Override
  public int remove(String id) {
    return removeRecord(TABLE_NAME, id);
  }
}

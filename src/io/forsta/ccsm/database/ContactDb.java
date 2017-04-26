package io.forsta.ccsm.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.forsta.ccsm.api.ForstaUser;

/**
 * Created by jlewis on 3/23/17.
 */

public class ContactDb extends DbBase {
  public static final String TABLE_NAME = "contacts";

  public static final String FIRSTNAME = "firstname";
  public static final String LASTNAME = "lastname";
  public static final String MIDDLENAME = "middlename";
  public static final String EMAIL = "email";
  public static final String NUMBER = "number";
  public static final String USERNAME = "username";
  public static final String UID = "uid";
  public static final String ORGID = "orgid";
  public static final String DATE = "date";
  public static final String TSREGISTERED = "tsregistered";

  public static final String CREATE_TABLE = "create table " +
      TABLE_NAME + "(" +
      "_id integer primary key autoincrement, " +
      FIRSTNAME + ", " +
      MIDDLENAME + ", " +
      LASTNAME + ", " +
      EMAIL + ", " +
      NUMBER + ", " +
      USERNAME + ", " +
      UID + ", " +
      ORGID + ", " +
      DATE + ", " +
      TSREGISTERED + " integer default 0, " +
      "CONSTRAINT item_number_unique UNIQUE (" + NUMBER + ")" +
      ")";

  public static String[] allColumns = {
      "_id",
      FIRSTNAME,
      MIDDLENAME,
      LASTNAME,
      EMAIL,
      NUMBER,
      USERNAME,
      UID,
      ORGID,
      DATE,
      TSREGISTERED
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
      Cursor c = getRecords(TABLE_NAME, allColumns, null, null, USERNAME);
      while (c.moveToNext()) {
        int index = c.getColumnIndex(NUMBER);
        int slugIndex = c.getColumnIndex(USERNAME);
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
      Cursor c = getRecords(TABLE_NAME, allColumns, null, null, NUMBER);
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

  public List<String> getIds() {
    List<String> ids = new ArrayList<>();
    try {
      Cursor c = getRecords(TABLE_NAME, allColumns, null, null, UID);
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

  public List<ForstaUser> getUsers() {
    List<ForstaUser> users = new ArrayList<>();
    try {
      Cursor c = getRecords(TABLE_NAME, allColumns, null, null, LASTNAME);
      while (c.moveToNext()) {
        ForstaUser user = new ForstaUser(c);
        users.add(user);
      }
      c.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return users;
  }

  @Override
  public Cursor get() {
    try {
      return getRecords(TABLE_NAME, allColumns, null, null, LASTNAME);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public Cursor getById(String id) {
    try {
      return getRecords(TABLE_NAME, allColumns, "_id= ?", new String[]{id}, LASTNAME);
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

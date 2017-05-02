package io.forsta.ccsm.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.whispersystems.signalservice.api.push.ContactTokenDetails;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.forsta.ccsm.api.ForstaUser;

/**
 * Created by jlewis on 3/23/17.
 */

public class ContactDb extends DbBase {
  public static final String TABLE_NAME = "contacts";

  public static final String ID = "_id";
  public static final String NAME = "name";
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
      NAME + ", " +
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
      ID,
      NAME,
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

  public Map<String, String> getIds() {
    Map<String, String> ids = new HashMap<>();
//    List<String> ids = new ArrayList<>();
    try {
      Cursor c = getRecords(TABLE_NAME, allColumns, null, null, UID);
      while (c.moveToNext()) {
        ids.put(c.getString(c.getColumnIndex(UID)), c.getString(c.getColumnIndex(ID)));
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
      Cursor c = getRecords(TABLE_NAME, allColumns, null, null, NAME);
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

  public List<String> updateForstaContacts(List<ContactTokenDetails> activeTokens) {
    for (ContactTokenDetails details : activeTokens) {
      SQLiteDatabase db = mDbHelper.getWritableDatabase();
      ContentValues values = new ContentValues();
      values.put(TSREGISTERED, 1);
      db.update(TABLE_NAME, values, NUMBER + "=?", new String[] { details.getNumber() });
    }
    return null;
  }

  public long addUser(ForstaUser user) {
    ContentValues values = new ContentValues();
    values.put(ContactDb.UID, user.uid);
    values.put(ContactDb.NAME, user.name);
    values.put(ContactDb.ORGID, user.orgId);
    values.put(ContactDb.NUMBER, user.phone);
    values.put(ContactDb.USERNAME, user.username);
    values.put(ContactDb.DATE, new Date().toString());
    values.put(ContactDb.TSREGISTERED, user.tsRegistered);
    return add(values);
  }

  public int updateUser(String id, ForstaUser user) {
    ContentValues values = new ContentValues();
    values.put(ContactDb.NAME, user.name);
    values.put(ContactDb.ORGID, user.orgId);
    values.put(ContactDb.NUMBER, user.phone);
    values.put(ContactDb.USERNAME, user.username);
    values.put(ContactDb.TSREGISTERED, user.tsRegistered);
    return update(id, values);
  }

  public Cursor getActiveRecipients() {
    try {
      return getRecords(TABLE_NAME, allColumns, TSREGISTERED + "=1", null, NAME);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public Cursor getInactiveRecipients() {
    try {
      return getRecords(TABLE_NAME, allColumns, TSREGISTERED + "=0", null, NAME);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public Set<String> getEligibleNumbers() {
    Set<String> numbers = new HashSet<>();
    Cursor cursor = get();
    while (cursor !=null && cursor.moveToNext()) {
      numbers.add(cursor.getString(cursor.getColumnIndex(NUMBER)));
    }
    cursor.close();
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

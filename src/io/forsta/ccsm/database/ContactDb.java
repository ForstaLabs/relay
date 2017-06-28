package io.forsta.ccsm.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import org.whispersystems.signalservice.api.push.ContactTokenDetails;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.forsta.ccsm.database.model.ForstaRecipient;
import io.forsta.ccsm.database.model.ForstaUser;

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
  public static final String TAGID = "tagid";
  public static final String SLUG = "slug";
  public static final String ORGID = "orgid";
  public static final String DATE = "date";
  public static final String TSREGISTERED = "tsregistered";

  public static final String CREATE_TABLE = "create table " +
      TABLE_NAME + "(" +
      ID + " integer primary key autoincrement, " +
      NAME + ", " +
      EMAIL + ", " +
      NUMBER + ", " +
      USERNAME + ", " +
      UID + ", " +
      TAGID + ", " +
      SLUG + ", " +
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
      TAGID,
      SLUG,
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
      Cursor c = getRecords(TABLE_NAME, allColumns, TSREGISTERED + "=1", null, USERNAME);
      while (c.moveToNext()) {
        contacts.put(c.getString(c.getColumnIndex(USERNAME)), c.getString(c.getColumnIndex(NUMBER)));
      }
      c.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return contacts;
  }

  public HashMap<String, ForstaRecipient> getContactRecipients() {
    HashMap<String, ForstaRecipient> contacts = new HashMap<>();
    try {
      Cursor c = getRecords(TABLE_NAME, allColumns, TSREGISTERED + "=1", null, USERNAME);
      while (c.moveToNext()) {
        ForstaRecipient recipient = new ForstaRecipient(c.getString(c.getColumnIndex(ContactDb.NAME)), c.getString(c.getColumnIndex(ContactDb.NUMBER)), c.getString(c.getColumnIndex(ContactDb.USERNAME)), c.getString(c.getColumnIndex(ContactDb.UID)), c.getString(c.getColumnIndex(ContactDb.ORGID)));
        contacts.put(c.getString(c.getColumnIndex(USERNAME)), recipient);
      }
      c.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return contacts;
  }

  public Set<String> getNumbers() {
    Set<String> numbers = new HashSet<>();
    try {
      Cursor c = getRecords(TABLE_NAME, allColumns, null, null, NUMBER);
      while (c.moveToNext()) {
        numbers.add(c.getString(c.getColumnIndex(NUMBER)));
      }
      c.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return numbers;
  }

  public Map<String, String> getUids() {
    Map<String, String> ids = new HashMap<>();
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

  public List<ForstaRecipient> getRecipients() {
    List<ForstaRecipient> recipients = new ArrayList<>();
    try {
      Cursor c = getRecords(TABLE_NAME, allColumns, TSREGISTERED + "=1", null, NAME);
      while (c.moveToNext()) {
        ForstaRecipient recipient = new ForstaRecipient(c.getString(c.getColumnIndex(ContactDb.NAME)), c.getString(c.getColumnIndex(ContactDb.NUMBER)), c.getString(c.getColumnIndex(ContactDb.USERNAME)), c.getString(c.getColumnIndex(ContactDb.UID)), c.getString(c.getColumnIndex(ContactDb.ORGID)));
        recipients.add(recipient);
      }
      c.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return recipients;
  }

  public void updateUsers(List<ForstaUser> users) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();
    Set<String> forstaUids = new HashSet<>();
    Map<String, String> uids = getUids();

    db.beginTransaction();
    try {
      for (ForstaUser user : users) {
        forstaUids.add(user.uid);
        ContentValues values = new ContentValues();
        values.put(ContactDb.UID, user.uid);
        values.put(ContactDb.TAGID, user.tag_id);
        values.put(ContactDb.SLUG, user.slug);
        values.put(ContactDb.UID, user.uid);
        values.put(ContactDb.NAME, user.name);
        values.put(ContactDb.ORGID, user.org_id);
        values.put(ContactDb.NUMBER, user.phone);
        values.put(ContactDb.USERNAME, user.username);
        values.put(ContactDb.TSREGISTERED, user.tsRegistered);
        if (uids.containsKey(user.uid)) {
          String id = uids.get(user.uid);
          db.update(TABLE_NAME, values, ID + "=?", new String[] { id });
        } else {
          db.insert(TABLE_NAME, null, values);
        }
        uids.remove(user.uid);
      }
      db.setTransactionSuccessful();
    }
    finally {
      db.endTransaction();
    }
    db.beginTransaction();
    try {
      // Now remove entries that are no longer valid.
      for (String uid : uids.keySet()) {
        db.delete(TABLE_NAME, UID + "=?", new String[] { uid });
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    db.close();
  }

  public void updateUser(ForstaUser user) {
    ContentValues values = new ContentValues();
    values.put(NAME, user.name);
    values.put(NUMBER, user.phone);
    values.put(TSREGISTERED, user.tsRegistered);
    SQLiteDatabase db = mDbHelper.getWritableDatabase();
    db.update(TABLE_NAME, values, ID + "=?", new String[] { user.id });
  }

  public void setActiveForstaNumbers(List<ContactTokenDetails> activeTokens) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();
    // This could be done with a update TABLE_NAME set TSREGISTERED = 1 where number in (1,2,3)
    for (ContactTokenDetails token : activeTokens) {
      String number = token.getNumber();
      ContentValues values = new ContentValues();
      values.put(TSREGISTERED, true);
      db.update(TABLE_NAME, values, NUMBER + "=?", new String[] { number });
    }
    db.close();
  }

  public Cursor getActiveRecipients() {
    try {
      return getRecords(TABLE_NAME, allColumns, TSREGISTERED + "=1", null, NAME);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public Cursor filterActiveRecipients(String slugPart) {
    try {
      String selection = TSREGISTERED + " = 1";
      if (slugPart.length() > 0) {
        selection = selection + " AND " + SLUG + " LIKE '" + slugPart + "%'";
      }
      return getRecords(TABLE_NAME, allColumns, selection, null, NAME);
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

  public List<ForstaRecipient> getRecipientsFromNumbers(List<String> numbers) {
    List<ForstaRecipient> recipients = new ArrayList<>();

    String query = "";
    String queryNumbers = TextUtils.join("','", numbers);
    query = NUMBER + " IN ('" + queryNumbers + "')";
    try {
      Cursor c = getRecords(TABLE_NAME, allColumns, query, null, NAME);
      while (c.moveToNext()) {
        ForstaRecipient recipient = new ForstaRecipient(c.getString(c.getColumnIndex(ContactDb.NAME)), c.getString(c.getColumnIndex(ContactDb.NUMBER)), c.getString(c.getColumnIndex(ContactDb.USERNAME)), c.getString(c.getColumnIndex(ContactDb.UID)), c.getString(c.getColumnIndex(ContactDb.ORGID)));
        recipients.add(recipient);
      }
      c.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return recipients;
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

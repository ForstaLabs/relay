package io.forsta.ccsm.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;

import org.whispersystems.signalservice.api.push.ContactTokenDetails;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.securesms.recipients.RecipientFactory;

/**
 * Created by jlewis on 3/23/17.
 */

public class ContactDb extends DbBase {
  public static final String TAG = ContactDb.class.getSimpleName();

  public static final String TABLE_NAME = "contacts";
  public static final String ID = "_id";
  public static final String NAME = "name";
  public static final String EMAIL = "email";
  public static final String NUMBER = "number";
  public static final String USERNAME = "username";
  public static final String AVATAR = "avatar";
  public static final String UID = "uid";
  public static final String TAGID = "tagid";
  public static final String SLUG = "slug";
  public static final String ORGID = "orgid";
  public static final String ORGSLUG = "org_slug";
  public static final String DATE = "date";
  public static final String TSREGISTERED = "tsregistered";
  public static final String ISACTIVE = "isactive";
  public static final String ISMONITOR = "ismonitor";
  public static final String USERTYPE = "type";

  public static final String CREATE_TABLE = "create table " +
      TABLE_NAME + "(" +
      ID + " integer primary key autoincrement, " +
      NAME + ", " +
      EMAIL + ", " +
      NUMBER + ", " +
      USERNAME + ", " +
      AVATAR + ", " +
      UID + ", " +
      TAGID + ", " +
      SLUG + ", " +
      ORGID + ", " +
      ORGSLUG + ", " +
      DATE + ", " +
      TSREGISTERED + " integer default 0, " +
      ISACTIVE + " integer default 0, " +
      ISMONITOR + " integer default 0, " +
      USERTYPE + ", " +
      "CONSTRAINT item_number_unique UNIQUE (" + UID + ")" +
      ")";

  public static String[] allColumns = {
      ID,
      NAME,
      EMAIL,
      NUMBER,
      USERNAME,
      AVATAR,
      UID,
      TAGID,
      SLUG,
      ORGID,
      ORGSLUG,
      DATE,
      TSREGISTERED,
      ISACTIVE,
      ISMONITOR,
      USERTYPE
  };

  public ContactDb(Context context, DbHelper dbHelper) {
    super(context, dbHelper);
  }

  public void removeAll() {
    removeAll(TABLE_NAME);
  }

  public Cursor getContactByAddress(String address) {
    try {
      return getRecords(TABLE_NAME, null, UID + " = ?", new String[] {address}, UID);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public ForstaUser getUserByAddress(String address) {
    ForstaUser user = null;
    try {
      Cursor cursor = getRecords(TABLE_NAME, null, UID + " = ?", new String[] {address}, UID);
      if (cursor != null && cursor.moveToNext()) {
        user = new ForstaUser(cursor);
      }
      cursor.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return user;
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

  public Set<String> getAddresses() {
    Set<String> addresses = new HashSet<>();
    try {
      Cursor c = getRecords(TABLE_NAME, allColumns, null, null, UID);
      while (c.moveToNext()) {
        addresses.add(c.getString(c.getColumnIndex(UID)));
      }
      c.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return addresses;
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

  public void updateUser(ForstaUser user) {
    ContentValues values = new ContentValues();
    values.put(NAME, user.name);
    values.put(NUMBER, user.phone);
    values.put(TSREGISTERED, user.tsRegistered);
    SQLiteDatabase db = mDbHelper.getWritableDatabase();
    db.update(TABLE_NAME, values, ID + "=?", new String[] { user.id });
    RecipientFactory.clearCache(context);
  }

  public void updateUsers(List<ForstaUser> users, boolean removeExisting) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();
    Map<String, String> uids = getUids();

    db.beginTransaction();
    try {
      for (ForstaUser user : users) {
        ContentValues values = new ContentValues();
        values.put(ContactDb.UID, user.uid);
        values.put(ContactDb.TAGID, user.tag_id);
        values.put(ContactDb.SLUG, user.slug);
        values.put(ContactDb.UID, user.uid);
        values.put(ContactDb.AVATAR, user.avatar);
        values.put(ContactDb.NAME, user.name);
        values.put(ContactDb.ORGID, user.org_id);
        values.put(ContactDb.ORGSLUG, user.org_slug);
        values.put(ContactDb.NUMBER, user.phone);
        values.put(ContactDb.USERNAME, user.username);
        values.put(ContactDb.EMAIL, user.email);
        values.put(ContactDb.ISACTIVE, user.isActive);
        values.put(ContactDb.ISMONITOR, user.isMonitor);
        values.put(ContactDb.USERTYPE, user.type.toString());
        if (uids.containsKey(user.uid)) {
          String id = uids.get(user.getUid());
          if (TextUtils.isEmpty(user.getUid())) {
            Log.w(TAG, "Existing user with empty UID!: " + user.slug);
            db.delete(TABLE_NAME, ID + " = ?", new String[] { id });
          } else {
            db.update(TABLE_NAME, values, ID + " = ?", new String[] { id });
          }
        } else {
          if (TextUtils.isEmpty(user.getUid())) {
            Log.w(TAG, "New user with empty UID!: " + user.slug);
          } else {
            db.insert(TABLE_NAME, null, values);
          }
        }
        uids.remove(user.uid);
      }
      db.setTransactionSuccessful();
      RecipientFactory.clearCache(context);

    }
    finally {
      db.endTransaction();
    }
    if (removeExisting && users.size() > 0) {
      Log.w(TAG, "Reseting directory. Removing " + uids.size() + " entries.");
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
    }
  }

  public void setInactiveAddresses(List<String> addresses) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      for (String uid : addresses) {
        ContentValues values = new ContentValues();
        values.put(ISACTIVE, false);
        db.update(TABLE_NAME, values, UID + "=?", new String[] { uid });
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void setActiveForstaAddresses(List<ContactTokenDetails> activeTokens, Set<String> eligibleAddresses) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();
    // This could be done with a update TABLE_NAME set TSREGISTERED = 1 where number in (1,2,3)
    for (ContactTokenDetails token : activeTokens) {
      String address = token.getNumber();
      ContentValues values = new ContentValues();
      values.put(TSREGISTERED, true);
      db.update(TABLE_NAME, values, UID + "=?", new String[] { address });
    }

    for (String address : eligibleAddresses) {
      ContentValues values = new ContentValues();
      values.put(TSREGISTERED, false);
      db.update(TABLE_NAME, values, UID + "=?", new String[] { address });
    }
  }

  public void setActiveForstaAddresses(List<ContactTokenDetails> activeTokens) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();
    // This could be done with a update TABLE_NAME set TSREGISTERED = 1 where number in (1,2,3), or batch the transaction.
    for (ContactTokenDetails token : activeTokens) {
      String address = token.getNumber();
      ContentValues values = new ContentValues();
      values.put(TSREGISTERED, true);
      db.update(TABLE_NAME, values, UID + "=?", new String[] { address });
    }
  }

  private Cursor getActiveRecipients(String filter) {
    String queryFilter = "(" + TSREGISTERED + " = 1 AND " + ISACTIVE + " = 1 AND " + ISMONITOR + " = 0 AND " + USERTYPE + " = 'PERSON')";

    String[] queryValues = null;
    if (filter != null && filter.length() > 0) {
      String user = filter;
      String org = filter;
      String[] parts = filter.split(":");
      if (parts.length > 0) {
        user = parts[0];
        if (parts.length > 1) {
          org = parts[1];
        }
      }
      queryFilter += " AND (" + NAME + " LIKE ? OR " + SLUG + " LIKE ? OR " + ORGSLUG + " LIKE ? OR " + NUMBER + " LIKE ? OR " + EMAIL + " LIKE ?)";
      queryValues = new String[] { "%" + user + "%", "%" + user + "%", "%" + org + "%", "%" + user + "%", "%" + user + "%" };
    }

    try {
      return getRecords(TABLE_NAME, allColumns, queryFilter, queryValues, NAME);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public List<ForstaUser> getActiveForstaUsers(String filter) {
    List<ForstaUser> users = new LinkedList<>();
    Cursor cursor = getActiveRecipients(filter);
    try {
      while (cursor != null && cursor.moveToNext()) {
        users.add(new ForstaUser(cursor));
      }
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
    return users;
  }

  public List<ForstaUser> getUsersByAddresses(List<String> addresses) {
    List<ForstaUser> users = new ArrayList<>();

    String query = "";
    String queryNumbers = TextUtils.join("','", addresses);
    query = UID + " IN ('" + queryNumbers + "')";
    try {
      Cursor c = getRecords(TABLE_NAME, allColumns, query, null, ORGID + ", " + NAME);
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

  public ForstaUser getUserByTag(String tag) {
    ForstaUser user = null;
    String[] splitTag = tag.split(":");
    try {
      Cursor cursor = null;
      if(splitTag.length == 1) {
        cursor = getRecords(TABLE_NAME, null, SLUG + " = ?", splitTag, SLUG);
      } else if(splitTag.length == 2) {
        cursor = getRecords(TABLE_NAME, null, SLUG + " = ?" + " AND " + ORGSLUG + " = ?", splitTag, SLUG);
      }
      if(cursor != null && cursor.moveToNext()) {
        user = new ForstaUser((cursor));
      }
      cursor.close();
    }catch(Exception e) {
      e.printStackTrace();
    }
    return user;
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

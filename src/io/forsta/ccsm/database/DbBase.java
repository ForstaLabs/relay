package io.forsta.ccsm.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by jlewis on 4/6/17.
 */


public abstract class DbBase {
  protected SQLiteOpenHelper mDbHelper;

  protected DbBase(Context context, DbHelper dbHelper) {
    mDbHelper = dbHelper;
  }

  public abstract Cursor get();
  public abstract Cursor getById(String id);
  public abstract long add(ContentValues values);
  public abstract int update(String id, ContentValues values);
  public abstract int remove(String id);


  protected int updateRecord(String table, String id, ContentValues values) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();
    int result = db.update(table, values, " _id= ? ", new String[] {id});
    return result;
  }

  protected long addRecord(String table, ContentValues values) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();
    long result = db.insert(table, null, values);
    return result;
  }

  protected int removeRecord(String table, String id) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();
    String[] args = new String[] {id};
    int result = db.delete(table, "_id = ?", args);
    return result;
  }

  protected Cursor getRecords(String table, String[] columns, String selection, String[] selectionArgs, String sort) throws Exception {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();
    return db.query(table, columns, selection, selectionArgs, null, null, sort);
  }

  protected void removeAll(String table) {
    SQLiteDatabase db = mDbHelper.getWritableDatabase();
    db.delete(table, null, null);
  }
}

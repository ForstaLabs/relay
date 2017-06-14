package io.forsta.ccsm.database.loaders;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;

import java.util.ArrayList;

import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.securesms.contacts.ContactsDatabase;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.util.AbstractCursorLoader;
import io.forsta.securesms.util.GroupUtil;

/**
 * Created by jlewis on 6/2/17.
 */

public class DirectoryLoader extends AbstractCursorLoader {

  private final String filter;

  public DirectoryLoader(Context context, String slugPart) {
    super(context);
    this.filter = slugPart;
  }

  @Override
  public Cursor getCursor() {
    ArrayList<Cursor> cursorList = new ArrayList<>(2);
    cursorList.add(DbFactory.getContactDb(context).filterActiveRecipients(this.filter));
    GroupDatabase groupDb = DatabaseFactory.getGroupDatabase(context);
    Cursor groupCursor = groupDb.getForstaGroups(this.filter);
    MatrixCursor forstaGroupCursor = new MatrixCursor(ContactDb.allColumns);
    while (groupCursor != null && groupCursor.moveToNext()) {
      forstaGroupCursor.addRow(new Object[] {
          groupCursor.getString(groupCursor.getColumnIndex(GroupDatabase.ID)),
          groupCursor.getString(groupCursor.getColumnIndex(GroupDatabase.TITLE)), //NAME
          "", //EMAIL
          groupCursor.getString(groupCursor.getColumnIndex(GroupDatabase.GROUP_ID)), //NUMBER
          groupCursor.getString(groupCursor.getColumnIndex(GroupDatabase.SLUG)), //USERNAME
          "", //UID
          "", //TAGID
          groupCursor.getString(groupCursor.getColumnIndex(GroupDatabase.SLUG)), //SLUG
          groupCursor.getString(groupCursor.getColumnIndex(GroupDatabase.ORG_ID)), //ORGID
          groupCursor.getString(groupCursor.getColumnIndex(GroupDatabase.TIMESTAMP)), //DATE
          1
      });
    }
    groupCursor.close();
    cursorList.add(forstaGroupCursor);
    return new MergeCursor(cursorList.toArray(new Cursor[0]));
  }
}

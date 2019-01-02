/**
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.forsta.securesms.contacts;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.net.NetworkInfo;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.content.CursorLoader;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.securesms.R;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.groups.GroupManager;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.DirectoryHelper;
import io.forsta.securesms.util.NumberUtil;
import io.forsta.securesms.util.ServiceUtil;
import io.forsta.securesms.util.TextSecurePreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * CursorLoader that initializes a ContactsDatabase instance
 *
 * @author Jake McGinty
 */
public class ContactsCursorLoader extends CursorLoader {

  private static final String TAG = ContactsCursorLoader.class.getSimpleName();

  public final static int MODE_ALL        = 0;
  public final static int MODE_PUSH_ONLY  = 1;
  public final static int MODE_OTHER_ONLY = 2;

  private final String filter;
  private final int    mode;

  private final String[] columns = {
      ContactsDatabase.ID_COLUMN,
      ContactsDatabase.NAME_COLUMN,
      ContactsDatabase.NUMBER_COLUMN,
      ContactsDatabase.NUMBER_TYPE_COLUMN,
      ContactsDatabase.LABEL_COLUMN,
      ContactsDatabase.CONTACT_TYPE_COLUMN
  };

  public ContactsCursorLoader(Context context, int mode, String filter) {
    super(context);

    this.filter = filter;
    this.mode   = mode;
  }

  @Override
  public Cursor loadInBackground() {
    ContactsDatabase  contactsDatabase = DatabaseFactory.getContactsDatabase(getContext());
    ArrayList<Cursor> cursorList = new ArrayList<>(3);

    if (mode == MODE_OTHER_ONLY) {
      cursorList.add(filterNonPushContacts(contactsDatabase.querySystemContacts(filter)));

      if (!TextUtils.isEmpty(filter) && NumberUtil.isValidSmsOrEmail(filter)) {
        MatrixCursor newNumberCursor = new MatrixCursor(columns, 1);

        newNumberCursor.addRow(new Object[] {-1L, getContext().getString(R.string.contact_selection_list__unknown_contact),
            filter, ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM,
            "\u21e2", ContactsDatabase.NEW_TYPE});

        cursorList.add(newNumberCursor);
      }
    } else {
      MatrixCursor forstaContactsCursor = new MatrixCursor(columns, 1);
      // XXX Remove self from list of recipients
      String localUid = TextSecurePreferences.getLocalNumber(getContext());
      List<ForstaUser> localUsers = DbFactory.getContactDb(getContext()).getActiveForstaUsers(filter);

      try {
        for (ForstaUser user : localUsers) {
          if (!localUid.equals(user.getUid())) {
            forstaContactsCursor.addRow(new Object[] {
                user.getDbId(),
                user.getName(),
                user.getUid(),
                user.getTag(),
                user.getOrgTag(),
                ContactsDatabase.PUSH_TYPE
            });
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }

      GroupDatabase gdb = DatabaseFactory.getGroupDatabase(getContext());
      Cursor groupCursor = gdb.getForstaGroupsByTitle(filter);
      try {
        while (groupCursor!= null && groupCursor.moveToNext()) {
          forstaContactsCursor.addRow(new Object[] {
              groupCursor.getString(groupCursor.getColumnIndex(GroupDatabase.ID)),
              groupCursor.getString(groupCursor.getColumnIndex(GroupDatabase.TITLE)),
              groupCursor.getString(groupCursor.getColumnIndex(GroupDatabase.GROUP_ID)),
              groupCursor.getString(groupCursor.getColumnIndex(GroupDatabase.SLUG)),
              groupCursor.getString(groupCursor.getColumnIndex(GroupDatabase.ORG_SLUG)),
              ContactsDatabase.PUSH_TYPE
          });
        }
      } finally {
        if (groupCursor != null) {
          groupCursor.close();
        }
      }
      cursorList.add(forstaContactsCursor);
    }
    return new MergeCursor(cursorList.toArray(new Cursor[0]));
  }

  private @NonNull Cursor filterNonPushContacts(@NonNull Cursor cursor) {
    try {
      final long startMillis = System.currentTimeMillis();
      final MatrixCursor matrix = new MatrixCursor(columns);
      while (cursor.moveToNext()) {
        final String number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.NUMBER_COLUMN));
        final Recipients recipients = RecipientFactory.getRecipientsFromString(getContext(), number, true);

        if (DirectoryHelper.getUserCapabilities(getContext(), recipients)
                           .getTextCapability() != DirectoryHelper.UserCapabilities.Capability.SUPPORTED)
        {
          matrix.addRow(new Object[]{cursor.getLong(cursor.getColumnIndexOrThrow(ContactsDatabase.ID_COLUMN)),
                                     cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.NAME_COLUMN)),
                                     number,
                                     cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.NUMBER_TYPE_COLUMN)),
                                     cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.LABEL_COLUMN)),
                                     ContactsDatabase.NORMAL_TYPE});
        }
      }
      Log.w(TAG, "filterNonPushContacts() -> " + (System.currentTimeMillis() - startMillis) + "ms");
      return matrix;
    } finally {
      cursor.close();
    }
  }
}

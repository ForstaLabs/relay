/**
 * Copyright (C) 2011 Whisper Systems
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
package io.forsta.securesms.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import java.util.Set;

import io.forsta.securesms.BuildConfig;

public abstract class Database {

  protected static final String ID_WHERE              = "_id = ?";
  public   static final String CONVERSATION_URI      = "content://" + BuildConfig.APPLICATION_ID + ".provider.database/conversation/";
  private   static final String CONVERSATION_LIST_URI = "content://" + BuildConfig.APPLICATION_ID + ".provider.database/conversation-list";
  public static final String THREAD_URI = "content://" + BuildConfig.APPLICATION_ID + ".provider.database/thread/";

  protected       SQLiteOpenHelper databaseHelper;
  protected final Context context;

  public Database(Context context, SQLiteOpenHelper databaseHelper) {
    this.context        = context;
    this.databaseHelper = databaseHelper;
  }

  protected void notifyConversationListeners(Set<Long> threadIds) {
    for (long threadId : threadIds)
      notifyConversationListeners(threadId);
  }

  protected void notifyConversationListeners(long threadId) {
    context.getContentResolver().notifyChange(Uri.parse(CONVERSATION_URI + threadId), null);
  }

  protected void notifyThreadListeners(long threadId) {
    context.getContentResolver().notifyChange(Uri.parse(THREAD_URI + threadId), null);
  }

  protected void notifyConversationListListeners() {
    context.getContentResolver().notifyChange(Uri.parse(CONVERSATION_LIST_URI), null);
  }

  protected void setNotifyConverationListeners(Cursor cursor, long threadId) {
    cursor.setNotificationUri(context.getContentResolver(), Uri.parse(CONVERSATION_URI + threadId));
  }

  protected void setNotifyConverationListListeners(Cursor cursor) {
    cursor.setNotificationUri(context.getContentResolver(), Uri.parse(CONVERSATION_LIST_URI));
  }

  public void reset(SQLiteOpenHelper databaseHelper) {
    this.databaseHelper = databaseHelper;
  }

}

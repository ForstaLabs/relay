package io.forsta.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;

import io.forsta.securesms.contacts.ContactAccessor;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.util.AbstractCursorLoader;

import java.util.LinkedList;
import java.util.List;

public class ConversationListLoader extends AbstractCursorLoader {

  private final String filter;
  private final boolean archived;
  private final boolean showAnnouncements;

  public ConversationListLoader(Context context, String filter, boolean archived, boolean showAnnouncements) {
    super(context);
    this.filter   = filter;
    this.archived = archived;
    this.showAnnouncements = showAnnouncements;
  }

  @Override
  public Cursor getCursor() {
    if      (filter != null && filter.trim().length() != 0) return getFilteredConversationList(filter);
    else if (!showAnnouncements) return getConversationListWithoutAnnouncements();
    else if (!archived)                                     return getConversationList();
    else                                                    return getArchivedConversationList();
  }

  private Cursor getConversationListWithoutAnnouncements() {
    return DatabaseFactory.getThreadDatabase(context).getConversationListWithoutAnnouncements();
  }

  private Cursor getConversationList() {
    return DatabaseFactory.getThreadDatabase(context).getConversationList();
  }

  private Cursor getArchivedConversationList() {
    return DatabaseFactory.getThreadDatabase(context).getArchivedConversationList();
  }

  private Cursor getFilteredConversationList(String filter) {
    return DatabaseFactory.getThreadDatabase(context).getFilteredConversationList(filter);
  }
}

package io.forsta.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;

import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.util.AbstractCursorLoader;

public class BlockedContactsLoader extends AbstractCursorLoader {

  public BlockedContactsLoader(Context context) {
    super(context);
  }

  @Override
  public Cursor getCursor() {
    return DatabaseFactory.getRecipientPreferenceDatabase(getContext())
                          .getBlocked();
  }

}

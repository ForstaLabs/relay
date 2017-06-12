package io.forsta.ccsm.database.loaders;

import android.content.Context;
import android.database.Cursor;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.securesms.util.AbstractCursorLoader;

/**
 * Created by jlewis on 6/2/17.
 */

public class DirectoryLoader extends AbstractCursorLoader {

  private final String filter;

  public DirectoryLoader(Context context, String filter) {
    super(context);
    this.filter = filter;
  }

  @Override
  public Cursor getCursor() {
    return DbFactory.getContactDb(context).getActiveRecipients();
  }
}

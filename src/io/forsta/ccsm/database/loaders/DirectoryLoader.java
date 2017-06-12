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

  public DirectoryLoader(Context context, String slugPart) {
    super(context);
    this.filter = slugPart;
  }

  @Override
  public Cursor getCursor() {
    return DbFactory.getContactDb(context).filterActiveRecipients(this.filter);
  }
}

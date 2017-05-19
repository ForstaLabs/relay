package io.forsta.ccsm.database;

import android.content.Context;

/**
 * Created by jlewis on 4/6/17.
 */

public class DbFactory {
  private static DbFactory instance;
  private DbHelper mDbHelper;
  private static final Object lock = new Object();
  private ContactDb mContactDb;

  public static DbFactory getInstance(Context context) {
    synchronized (lock) {
      if (instance == null)
        instance = new DbFactory(context.getApplicationContext());

      return instance;
    }
  }

  private DbFactory(Context context) {
    mDbHelper = new DbHelper(context);
    mContactDb = new ContactDb(context, mDbHelper);
  }

  public static ContactDb getContactDb(Context context) {
    return getInstance(context).mContactDb;
  }

}

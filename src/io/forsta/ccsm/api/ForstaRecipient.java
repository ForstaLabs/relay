package io.forsta.ccsm.api;

import android.database.Cursor;

import java.util.List;

import io.forsta.ccsm.database.ContactDb;

/**
 * Created by jlewis on 5/19/17.
 */

public class ForstaRecipient {
  public String uuid;
  public String name;
  public String slug;
  public String number;
  public List<String> groupNumbers;

  public ForstaRecipient(String name, String number, String slug) {
    this.name = name;
    this.number = number;
    this.slug = slug;
  }

  public ForstaRecipient(Cursor cursor) {
    this.slug = cursor.getString(cursor.getColumnIndex(ContactDb.USERNAME));
    this.name = cursor.getString(cursor.getColumnIndex(ContactDb.NAME));
    this.number = cursor.getString(cursor.getColumnIndex(ContactDb.NUMBER));
  }
}

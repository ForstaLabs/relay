package io.forsta.ccsm.api;

import android.database.Cursor;
import android.provider.ContactsContract;

import org.json.JSONException;
import org.json.JSONObject;

import io.forsta.ccsm.database.ContactDb;
import io.forsta.securesms.contacts.ContactsDatabase;

/**
 * Created by jlewis on 3/2/17.
 */

public class ForstaUser {
  public String id;
  public String username;
  public String firstName;
  public String middleName;
  public String lastName;
  public String email;
  public String phone;
  public String orgId;
  public boolean tsRegistered;

  public ForstaUser(JSONObject userObj) {
    try {
      this.id = userObj.getString("id");
      this.orgId = userObj.getString("org_id");
      this.username = userObj.getString("username");
      this.firstName = userObj.getString("first_name");
      this.middleName = userObj.getString("middle_name");
      this.lastName = userObj.getString("last_name");
      this.email = userObj.getString("email");
      this.phone = userObj.getString("phone");
      this.tsRegistered = false;
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }

  // Mapper for Db to UI object
  public ForstaUser(Cursor cursor) {
    this.id = cursor.getString(cursor.getColumnIndex(ContactDb.UID));
    this.orgId = cursor.getString(cursor.getColumnIndex(ContactDb.ORGID));
    this.username = cursor.getString(cursor.getColumnIndex(ContactDb.USERNAME));
    this.firstName = cursor.getString(cursor.getColumnIndex(ContactDb.FIRSTNAME));
    this.middleName = cursor.getString(cursor.getColumnIndex(ContactDb.MIDDLENAME));
    this.lastName = cursor.getString(cursor.getColumnIndex(ContactDb.LASTNAME));
    this.email = cursor.getString(cursor.getColumnIndex(ContactDb.EMAIL));
    this.phone = cursor.getString(cursor.getColumnIndex(ContactDb.NUMBER));
    this.tsRegistered = true;
  }

  public String getName() {
    StringBuilder name = new StringBuilder();
    name.append(firstName).append(" ");
    if (!middleName.equals("")) {
      name.append(middleName).append(" ");
    }
    name.append(lastName);
    return name.toString();
  }
}

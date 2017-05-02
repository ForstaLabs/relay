package io.forsta.ccsm.api;

import android.database.Cursor;
import android.provider.ContactsContract;

import org.json.JSONException;
import org.json.JSONObject;

import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbUtils;
import io.forsta.securesms.contacts.ContactsDatabase;

/**
 * Created by jlewis on 3/2/17.
 */

public class ForstaUser {
  public String id; //Db id
  public String uid;
  public String name;
  public String username;
  public String email;
  public String phone;
  public String orgId;
  public boolean tsRegistered;

  public ForstaUser(JSONObject userObj) {
    try {
      String name = DbUtils.getContactName(userObj);
      this.uid = userObj.getString("id");
      this.orgId = userObj.getString("org_id");
      this.username = userObj.getString("username");
      this.name = name;
      this.email = userObj.getString("email");
      this.phone = userObj.getString("phone");
      this.tsRegistered = false;
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }

  // Mapper for Db to UI object
  public ForstaUser(Cursor cursor) {
    this.id = cursor.getString(cursor.getColumnIndex(ContactDb.ID));
    this.uid = cursor.getString(cursor.getColumnIndex(ContactDb.UID));
    this.orgId = cursor.getString(cursor.getColumnIndex(ContactDb.ORGID));
    this.username = cursor.getString(cursor.getColumnIndex(ContactDb.USERNAME));
    this.name = cursor.getString(cursor.getColumnIndex(ContactDb.NAME));
    this.email = cursor.getString(cursor.getColumnIndex(ContactDb.EMAIL));
    this.phone = cursor.getString(cursor.getColumnIndex(ContactDb.NUMBER));
    this.tsRegistered = cursor.getString(cursor.getColumnIndex(ContactDb.TSREGISTERED)) == "1" ? true : false;
  }

  public String getName() {
    return name;
  }
}

package io.forsta.ccsm.database.model;

import android.database.Cursor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.forsta.ccsm.database.ContactDb;

/**
 * Created by jlewis on 3/2/17.
 */

public class ForstaUser {
  public String id; //Db id
  public String uid;
  public String tag_id;
  public String name;
  public String username;
  public String slug;
  public String email;
  public String phone;
  public String org_id;
  public boolean tsRegistered;

  public ForstaUser() {

  }

  public ForstaUser(JSONObject userObj) {
    try {
      String name = getContactName(userObj);
      this.name = name;
      if (userObj.has("tag")) {
        JSONObject tag = userObj.getJSONObject("tag");
        if (tag.has("id")) {
          this.tag_id = tag.getString("id");
          this.slug = tag.getString("slug");
        }
      }
      this.uid = userObj.getString("id");
      this.org_id = userObj.getString("org_id");
      this.username = userObj.getString("username");
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
    this.org_id = cursor.getString(cursor.getColumnIndex(ContactDb.ORGID));
    this.tag_id = cursor.getString(cursor.getColumnIndex(ContactDb.TAGID));
    this.slug = cursor.getString(cursor.getColumnIndex(ContactDb.SLUG));
    this.username = cursor.getString(cursor.getColumnIndex(ContactDb.USERNAME));
    this.name = cursor.getString(cursor.getColumnIndex(ContactDb.NAME));
    this.email = cursor.getString(cursor.getColumnIndex(ContactDb.EMAIL));
    this.phone = cursor.getString(cursor.getColumnIndex(ContactDb.NUMBER));
    this.tsRegistered = cursor.getInt(cursor.getColumnIndex(ContactDb.TSREGISTERED)) == 1 ? true : false;
  }

  public String getName() {
    return name;
  }

  private String getContactName(JSONObject userObject) throws JSONException {
    StringBuilder name = new StringBuilder();
    String firstName = userObject.getString("first_name");
    String middleName = userObject.getString("middle_name");
    String lastName = userObject.getString("last_name");
    name.append(firstName).append(" ");
    if (!middleName.equals("")) {
      name.append(middleName).append(" ");
    }
    name.append(lastName);
    return name.toString();
  }
}

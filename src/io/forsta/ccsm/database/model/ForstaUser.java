package io.forsta.ccsm.database.model;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.util.InvalidUserException;

/**
 * Created by jlewis on 3/2/17.
 */

public class ForstaUser {
  private static final String TAG = ForstaUser.class.getSimpleName();
  public String id; //Db id
  public String uid;
  public String tag_id;
  public String name;
  public String username;
  public String slug;
  public String email;
  public String avatar;
  public String phone;
  public String org_id;
  public String org_slug;
  public boolean tsRegistered;

  public ForstaUser() {

  }

  public ForstaUser(JSONObject userObj) throws InvalidUserException {
    try {
      this.uid = userObj.getString("id");
      if (TextUtils.isEmpty(this.uid)) {
        throw new InvalidUserException("UID is empty of null");
      }
      this.username = userObj.getString("username");
      String name = getContactName(userObj);
      this.name = name;
      if (userObj.has("tag")) {
        JSONObject tag = userObj.getJSONObject("tag");
        if (tag.has("id")) {
          this.tag_id = tag.getString("id");
        }
        if (tag.has("slug")) {
          this.slug = tag.getString("slug");
        }
      }
      JSONObject org = userObj.getJSONObject("org");
      if (org.has("id")) {
        this.org_id = org.getString("id");
      }

      if (org.has("slug")) {
        this.org_slug = org.getString("slug");
      }

      if (userObj.has("gravatar_hash")) {
        this.avatar = userObj.getString("gravatar_hash");
      }

      if (userObj.has("email")) {
        this.email = userObj.getString("email");
      }

      if (userObj.has("phone")) {
        this.phone = userObj.getString("phone");
      }
      this.tsRegistered = false;

    } catch (JSONException e) {
      e.printStackTrace();
      Log.w(TAG, userObj.toString());
      throw new InvalidUserException("An error occured parsing user JSON");
    }
  }

  public static ForstaUser getLocalForstaUser(Context context) {
    try {
      return new ForstaUser(new JSONObject(ForstaPreferences.getForstaUser(context)));
    } catch (JSONException e) {
      Log.e(TAG, "Exception parsing user object from preferences");
    } catch (InvalidUserException e) {
      Log.e(TAG, e.getMessage());
      e.printStackTrace();
    }
    return null;
  }

  // Mapper for Db to UI object
  public ForstaUser(Cursor cursor) {
    this.id = cursor.getString(cursor.getColumnIndex(ContactDb.ID));
    this.uid = cursor.getString(cursor.getColumnIndex(ContactDb.UID));
    this.org_id = cursor.getString(cursor.getColumnIndex(ContactDb.ORGID));
    this.avatar = cursor.getString(cursor.getColumnIndex(ContactDb.AVATAR));
    this.org_slug = cursor.getString(cursor.getColumnIndex(ContactDb.ORGSLUG));
    this.tag_id = cursor.getString(cursor.getColumnIndex(ContactDb.TAGID));
    this.slug = cursor.getString(cursor.getColumnIndex(ContactDb.SLUG));
    this.username = cursor.getString(cursor.getColumnIndex(ContactDb.USERNAME));
    this.name = cursor.getString(cursor.getColumnIndex(ContactDb.NAME));
    this.email = cursor.getString(cursor.getColumnIndex(ContactDb.EMAIL));
    this.phone = cursor.getString(cursor.getColumnIndex(ContactDb.NUMBER));
    this.tsRegistered = cursor.getInt(cursor.getColumnIndex(ContactDb.TSREGISTERED)) == 1 ? true : false;
  }

  public String getDbId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getUid() {
    return uid;
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

  public String getOrgTag() {
    return org_slug;
  }

  public String getTag() {
    return slug;
  }

  public String getFullTag() {
    return slug + ":" + org_slug;
  }

  public String getFormattedTag(String currentOrg) {
    return "@" + (currentOrg.equals(getOrgTag()) ? getTag() : getFullTag());
  }

  public String getPhone() {
    return phone;
  }

  @Override
  public boolean equals(Object o) {
    ForstaUser other = (ForstaUser)o;
    return this.uid.equals(other.uid);
  }
}

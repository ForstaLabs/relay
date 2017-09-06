package io.forsta.ccsm.database.model;

import android.database.Cursor;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.util.GroupUtil;

/**
 * Created by jlewis on 2/24/17.
 */

public class ForstaGroup {
  private static final String TAG = ForstaGroup.class.getSimpleName();
  public String id;
  public String slug;
  public String org_id;
  public String org_slug;
  public String description;
  public String parent;
  public Set<String> members = new HashSet<>();

  public ForstaGroup(JSONObject jsonObject) {
    try {
      this.id = jsonObject.getString("id");
      this.slug = jsonObject.getString("slug");
      this.description = jsonObject.getString("description");
      this.parent = jsonObject.getString("parent");
      JSONObject orgObj = jsonObject.getJSONObject("org");
      this.org_id = orgObj.getString("id");
      this.org_slug = orgObj.getString("slug");
    } catch (JSONException e) {
      Log.w(TAG, "Error parsing tag");
      e.printStackTrace();
    }
  }

  public ForstaGroup(Cursor cursor) {
    this.id = cursor.getString(cursor.getColumnIndex(GroupDatabase.GROUP_ID));
    this.slug = cursor.getString(cursor.getColumnIndex(GroupDatabase.SLUG));
    this.org_id = cursor.getString(cursor.getColumnIndex(GroupDatabase.ORG_ID));
    this.description = cursor.getString(cursor.getColumnIndex(GroupDatabase.TITLE));
    String members = cursor.getString(cursor.getColumnIndex(GroupDatabase.MEMBERS));
    String[] memberArray = members.split(",");
    this.members = new HashSet<>(Arrays.asList(memberArray));
  }

  public void addMembers(Set<String> numbers) {
    for (String number : numbers) {
      members.add(number);
    }
  }

  public String getEncodedId() {
    return GroupUtil.getEncodedId(id.getBytes());
  }
}

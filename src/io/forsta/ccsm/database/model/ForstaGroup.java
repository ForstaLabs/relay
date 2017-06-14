package io.forsta.ccsm.database.model;

import android.database.Cursor;

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
  public String id;
  public String org;
  public String slug;
  public String description;
  public String parent;
  public Set<String> members = new HashSet<>();

  public ForstaGroup(JSONObject jsonObject) {
    try {
      this.id = jsonObject.getString("id");
      this.org = jsonObject.getString("org_id");
      this.slug = jsonObject.getString("slug");
      this.description = jsonObject.getString("description");
      this.parent = jsonObject.getString("parent");
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }

  public ForstaGroup(Cursor cursor) {
    this.id = cursor.getString(cursor.getColumnIndex("group_id"));
    this.slug = cursor.getString(cursor.getColumnIndex("slug"));
    this.org = cursor.getString(cursor.getColumnIndex("org"));
    this.description = cursor.getString(cursor.getColumnIndex("title"));
    String members = cursor.getString(cursor.getColumnIndex("members"));
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

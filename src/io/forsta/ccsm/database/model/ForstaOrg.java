package io.forsta.ccsm.database.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by jlewis on 9/21/17.
 */

public class ForstaOrg {
  private String uid;
  private String name;
  private String slug;

  public ForstaOrg() {

  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public String getUid() {
    return uid;
  }

  public static ForstaOrg fromJsonString(String jsonString) {
    try {
      JSONObject json = new JSONObject(jsonString);
      ForstaOrg org = new ForstaOrg();
      org.uid = json.getString("id");
      org.name = json.getString("name");
      org.slug = json.getString("slug");
      return org;
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return new ForstaOrg();
  }
}

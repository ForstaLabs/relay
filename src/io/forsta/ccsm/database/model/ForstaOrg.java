package io.forsta.ccsm.database.model;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.ccsm.util.InvalidUserException;

/**
 * Created by jlewis on 9/21/17.
 */

public class ForstaOrg {
  private static final String TAG = ForstaOrg.class.getSimpleName();
  private String uid;
  private String name;
  private String slug;
  private boolean offTheRecord = false;

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

  public boolean getOffTheRecord() {
    return offTheRecord;
  }

  public static ForstaOrg fromJsonString(String jsonString) {
    try {
      JSONObject json = new JSONObject(jsonString);
      ForstaOrg org = new ForstaOrg();
      org.uid = json.getString("id");
      org.name = json.getString("name");
      org.slug = json.getString("slug");
      if (json.has("preferences")) {
        JSONObject preferences = json.getJSONObject("preferences");
        if (preferences.has("messaging.off_the_record")) {
          org.offTheRecord = preferences.getBoolean("messaging.off_the_record");
        }
      }
      return org;
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return null;
  }

  public static ForstaOrg getLocalForstaOrg(Context context) {
    return fromJsonString(ForstaPreferences.getForstaOrg(context));
  }
}

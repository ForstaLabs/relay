package io.forsta.ccsm.api.model;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.forsta.securesms.util.TextSecurePreferences;

/**
 * Created by jlewis on 9/6/17.
 */

public class ForstaDistribution {
  private static final String TAG = ForstaDistribution.class.getSimpleName();
  public String pretty;
  public String universal;
  public Set<String> userIds = new HashSet<>();
  public String warning;

  public ForstaDistribution(JSONObject jsonResponse) {
    try {
      JSONArray ids = jsonResponse.getJSONArray("userids");
      for (int i=0; i<ids.length(); i++) {
        userIds.add(ids.getString(i));
      }
      universal = jsonResponse.getString("universal");
      pretty = jsonResponse.getString("pretty");

      JSONArray warnings = jsonResponse.getJSONArray("warnings");
      StringBuilder sb = new StringBuilder();
      for (int i=0; i<warnings.length(); i++) {
        JSONObject object = warnings.getJSONObject(i);
        if (object.has("kind")) {
          sb.append(object.getString("kind")).append(": ");
        }
        if (object.has("cue")) {
          sb.append(object.getString("cue"));
        }
      }
      this.warning = sb.toString();
    } catch (JSONException e) {
      Log.w(TAG, "ForstaDistribution json parsing error:");
      e.printStackTrace();
      this.warning = "Bad response from server";
    }
  }

  public boolean hasRecipients() {
    return userIds.size() > 0;
  }

  public List<String> getRecipients(Context context) {
    List<String> users = new ArrayList<>();
    boolean excludeSelf = true;
    if (userIds.size() > 2) {
      excludeSelf = false;
    }
    for (String id : userIds) {
      if (!(excludeSelf && id.equals(TextSecurePreferences.getLocalNumber(context)))) {
        users.add(id);
      }
    }
    return users;
  }
}

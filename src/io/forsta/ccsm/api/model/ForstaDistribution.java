package io.forsta.ccsm.api.model;

import android.content.Context;

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
  public String pretty;
  public String universal;
  public Set<String> userIds;
  public String warning;

  public ForstaDistribution(JSONObject jsonResponse) {
    userIds = new HashSet<>();
    try {
      JSONArray ids = jsonResponse.getJSONArray("userids");
      for (int i=0; i<ids.length(); i++) {
        userIds.add(ids.getString(i));
      }
      universal = jsonResponse.getString("universal");
      pretty = jsonResponse.getString("pretty");

      JSONObject warnings = jsonResponse.getJSONObject("warnings");
      if (warnings.has("cue")) {
        warning = warnings.getString("cue");
      }
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }

  public boolean hasRecipients() {
    return userIds.size() > 0;
  }

  public List<String> getRecipients(Context context) {
    List<String> users = new ArrayList<>();
    for (String id : userIds) {
      if (!id.equals(TextSecurePreferences.getLocalNumber(context))) {
        users.add(id);
      }
    }
    return users;
  }
}

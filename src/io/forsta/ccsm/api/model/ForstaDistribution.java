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

import io.forsta.securesms.BuildConfig;
import io.forsta.securesms.util.TextSecurePreferences;

/**
 * Created by jlewis on 9/6/17.
 */

public class ForstaDistribution {
  private static final String TAG = ForstaDistribution.class.getSimpleName();
  public String pretty;
  public String universal;
  public Set<String> userIds = new HashSet<>();
  public Set<String> monitorIds = new HashSet<>();
  public String warning = "";

  public ForstaDistribution() {

  }

  public static ForstaDistribution fromJson(JSONObject jsonResponse) {
    ForstaDistribution forstaDistribution = new ForstaDistribution();
    try {
      JSONArray ids = jsonResponse.getJSONArray("userids");
      for (int i=0; i<ids.length(); i++) {
        forstaDistribution.userIds.add(ids.getString(i));
      }
      if (jsonResponse.has("monitorids")) {
        JSONArray mIds = jsonResponse.getJSONArray("monitorids");
        for (int i=0; i< mIds.length(); i++) {
          forstaDistribution.monitorIds.add(mIds.getString(i));
        }
      }
      forstaDistribution.universal = jsonResponse.getString("universal");
      forstaDistribution.pretty = jsonResponse.getString("pretty");

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
      forstaDistribution.appendWarning(sb.toString());
    } catch (JSONException e) {
      Log.w(TAG, "ForstaDistribution json parsing error!");
      Log.w(TAG, "Distribution object:");
      Log.w(TAG, jsonResponse.toString());
      e.printStackTrace();
      forstaDistribution.appendWarning("Bad response from server");
    }
    return forstaDistribution;
  }

  public boolean isValid() {
    return universal != null && universal.contains("<") && hasRecipients();
  }

  public boolean hasRecipients() {
    return userIds.size() > 0;
  }

  public boolean hasMonitors() {
    return monitorIds.size() > 0;
  }

  public boolean hasSufficientRecipients() {
    return userIds.size() > 1;
  }

  public List<String> getRecipients(Context context) {
    List<String> users = new ArrayList<>();
    boolean excludeSelf = true;
    if (userIds.size() > 2 || userIds.size() == 1) {
      excludeSelf = false;
    }
    for (String id : userIds) {
      if (!(excludeSelf && id.equals(TextSecurePreferences.getLocalNumber(context)))) {
        users.add(id);
      }
    }
    return users;
  }

  public JSONArray getRecipientsArray(Context context) {
    JSONArray recipientArray = new JSONArray();
    for (String recipient : userIds) {
      recipientArray.put(recipient);
    }
    return recipientArray;
  }

  public List<String> getMonitors(Context context) {
    return new ArrayList<>(monitorIds);
  }

  public boolean hasWarnings() {
    return !TextUtils.isEmpty(warning);
  }

  public String getWarnings() {
    return warning;
  }

  private void appendWarning(String warningMessage) {
    if (!hasWarnings()) {
      warning = warningMessage;
    } else {
      warning += " " + warningMessage;
    }
  }
}

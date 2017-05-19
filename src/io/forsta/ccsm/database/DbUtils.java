package io.forsta.ccsm.database;

import android.content.Context;
import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.database.TextSecureDirectory;

/**
 * Created by jlewis on 4/7/17.
 */

public class DbUtils {

  public static Set<String> getTextSecureGroupIds(Context context) {
    Set<String> groupIds = new HashSet<>();
    GroupDatabase groupDb = DatabaseFactory.getGroupDatabase(context);
    GroupDatabase.Reader reader = groupDb.getGroups();
    GroupDatabase.GroupRecord record;
    GroupDatabase.GroupRecord existing = null;
    while ((record = reader.getNext()) != null) {
      groupIds.add(record.getEncodedId());
    }
    reader.close();
    return groupIds;
  }

  public static String getContactName(JSONObject userObject) throws JSONException {
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

package io.forsta.ccsm.api;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by jlewis on 3/2/17.
 */

public class ForstaUser {
  public String id;
  public String username;
  private String firstName;
  private String middleName;
  private String lastName;
  public String phone;

  public ForstaUser(JSONObject userObj) {
    try {
      this.id = userObj.getString("id");
      this.username = userObj.getString("username");
      this.firstName = userObj.getString("first_name");
      this.middleName = userObj.getString("middle_name");
      this.lastName = userObj.getString("last_name");
      this.phone = userObj.getString("phone");
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }

  public String getName() {
    StringBuilder name = new StringBuilder();
    name.append(firstName).append(" ");
    if (!middleName.equals("")) {
      name.append(middleName).append(" ");
    }
    name.append(lastName);
    return name.toString();
  }
}

package io.forsta.ccsm.api;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by jlewis on 3/2/17.
 */

public class ForstaUser {
    public String id;
    public String username;
    public String firstName;
    public String middleName;
    public String lastName;
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

}

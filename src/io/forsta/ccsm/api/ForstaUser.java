package io.forsta.ccsm.api;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by jlewis on 2/24/17.
 */

public class ForstaUser {
    public String id;
    public String username;
    public String firstName;
    public String middleName;
    public String lastName;
    public String primaryPhone;

    public ForstaUser(JSONObject jsonObject) {
        try {
            JSONObject user = jsonObject.getJSONObject("user");
            this.id = user.getString("id");
            this.username = user.getString("username");
            this.firstName = user.getString("first_name");
            this.lastName = user.getString("last_name");
            this.primaryPhone = user.getString("primary_phone");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}

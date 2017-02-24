package io.forsta.ccsm.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by jlewis on 2/24/17.
 */

public class ForstaOrg {
    public String id;
    public String logo;
    public String name;
    public Address address;
    public String slug;
    public String slogan;

    public ForstaOrg(JSONObject jsonObject) {
        try {
            this.id = jsonObject.getString("id");
            this.name = jsonObject.getString("name");
            this.slug = jsonObject.getString("slug");

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private class Address {
        private String address1;
        private String address2;
        private String city;
        private String state;
        private String postal;
    }
}

package io.forsta.ccsm.api;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by jlewis on 2/24/17.
 */

public class ForstaGroup {
    public String id;
    public String org;
    public String slug;
    public String description;
    public String parent;
    public ForstaOrg parentOrg;
    private Set<ForstaUser> users;

    public ForstaGroup(ForstaOrg parent, JSONObject jsonObject) {
        try {
            this.id = jsonObject.getString("id");
            this.org = jsonObject.getString("org");
            this.slug = jsonObject.getString("slug");
            this.description = jsonObject.getString("description");
            this.parent = jsonObject.getString("parent");

            this.parentOrg = parent;
            this.users = new HashSet<>();
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public void addUser(ForstaUser user) {
        users.add(user);
    }

    public void removeUser(ForstaUser user) {
        users.remove(user);
    }

    public Set<String> getGroupNumbers() {
        Set<String> set = new HashSet<>();
        for (ForstaUser user : users) {
            set.add(user.primaryPhone);
        }
        return set;
    }
}

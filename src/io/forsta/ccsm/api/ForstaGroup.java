package io.forsta.ccsm.api;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.forsta.securesms.util.GroupUtil;

/**
 * Created by jlewis on 2/24/17.
 */

public class ForstaGroup {
    public String id;
    public String org;
    public String slug;
    public String description;
    public String parent;
    private Map<String, String> users;

    public ForstaGroup(JSONObject jsonObject) {
        try {
            this.id = jsonObject.getString("id");
            this.org = jsonObject.getString("org");
            this.slug = jsonObject.getString("slug");
            this.description = jsonObject.getString("description");
            this.parent = jsonObject.getString("parent");

            this.users = new HashMap<>();
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public void addUser(String id, String number) {
        users.put(id, number);
    }

    public void removeUser(String id) {
        for (Map.Entry<String, String> user : users.entrySet()) {
            if (user.getKey().equals(id)) {
                users.remove(user);
                break;
            }
        }
    }

    public void addMembers(Map<String, String> numbers) {
        for (Map.Entry<String, String> number : numbers.entrySet()) {
            users.put(number.getKey(), number.getValue());
        }
    }

    public Set<String> getGroupNumbers() {
        Set<String> set = new HashSet<>();
        for (Map.Entry<String, String> user : users.entrySet()) {
            set.add(user.getValue());
        }
        return set;
    }

    public String getEncodedId() {
        return GroupUtil.getEncodedId(id.getBytes());
    }
}

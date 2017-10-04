package io.forsta.ccsm.database.model;

import android.database.Cursor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.forsta.ccsm.database.ContactDb;

/**
 * Created by jlewis on 5/19/17.
 */


// TODO Complete this object. This is used to store recipient information during new and existing conversations.
public class ForstaRecipient {
  public String uuid;
  public String org;
  public String parent;
  public String name;
  public String slug;
  public String number;
  public boolean registered;
  public Set<String> groupNumbers = new HashSet<>();

  public ForstaRecipient(String name, String number, String slug, String id, String orgId) {
    this.name = name;
    this.number = number;
    this.slug = slug;
    this.uuid = id;
    this.org = orgId; //TODO Change to org slug when endpoint is ready.
  }
}

package io.forsta.ccsm.database.model;

/**
 * Created by jlewis on 9/7/17.
 */

public class ForstaThread {
  public long threadid;
  public String uid;
  public String title;
  public String distribution;

  public ForstaThread(long threadid, String uid, String title, String distribution) {
    this.threadid = threadid;
    this.uid = uid;
    this.title = title;
    this.distribution = distribution;
  }
}

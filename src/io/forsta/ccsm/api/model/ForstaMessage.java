package io.forsta.ccsm.api.model;

import android.text.Html;
import android.text.Spanned;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

import io.forsta.ccsm.util.ForstaUtils;

/**
 * Created by jlewis on 9/6/17.
 */

public class ForstaMessage {
  private static final String TAG = ForstaMessage.class.getSimpleName();
  public String textBody;
  public Spanned htmlBody;
  public String messageId;
  public String senderId;
  public String universalExpression;
  public String threadId;
  public String threadTitle;
  public ForstaDistribution distribution;

  public ForstaMessage(String messageBody) {
    try {
      JSONObject jsonBody = ForstaUtils.getVersion(1, messageBody);
      JSONObject distribution = jsonBody.getJSONObject("distribution");
      this.universalExpression = distribution.getString("expression");
      this.threadId = jsonBody.getString("threadId");
      this.messageId = jsonBody.getString("messageId");
      this.threadTitle = jsonBody.getString("threadTitle");

      JSONObject data = jsonBody.getJSONObject("data");
      JSONArray body =  data.getJSONArray("body");
      for (int j=0; j<body.length(); j++) {
        JSONObject object = body.getJSONObject(j);
        if (object.getString("type").equals("text/html")) {
          this.htmlBody = Html.fromHtml(object.getString("value"));
        }
        if (object.getString("type").equals("text/plain")) {
          this.textBody = object.getString("value");
        }
      }

      JSONObject sender = jsonBody.getJSONObject("sender");
      this.senderId = sender.getString("userId");

    } catch (JSONException e) {
      Log.w(TAG, "Invalid JSON message body");
      e.printStackTrace();
    }
  }
}

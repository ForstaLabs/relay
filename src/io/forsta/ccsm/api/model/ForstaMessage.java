package io.forsta.ccsm.api.model;

import android.text.Html;
import android.text.Spanned;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

import io.forsta.ccsm.database.model.ForstaThread;
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
  private ForstaDistribution distribution;

  public ForstaMessage(String messageBody) {
    try {
      JSONObject jsonBody = ForstaUtils.getVersion(1, messageBody);
      if (jsonBody == null) {
        textBody = messageBody;
      } else {
        JSONObject distribution = jsonBody.getJSONObject("distribution");
        universalExpression = distribution.getString("expression");
        threadId = jsonBody.getString("threadId");
        messageId = jsonBody.getString("messageId");
        if (jsonBody.has("threadTitle")) {
          threadTitle = jsonBody.getString("threadTitle");
        }
        if (jsonBody.has("data")) {
          JSONObject data = jsonBody.getJSONObject("data");
          if (data.has("body")) {
            JSONArray body =  data.getJSONArray("body");
            for (int j=0; j<body.length(); j++) {
              JSONObject object = body.getJSONObject(j);
              if (object.getString("type").equals("text/html")) {
                htmlBody = Html.fromHtml(object.getString("value"));
              }
              if (object.getString("type").equals("text/plain")) {
                textBody = object.getString("value");
              }
            }
          }
        }
        JSONObject sender = jsonBody.getJSONObject("sender");
        senderId = sender.getString("userId");
      }
    } catch (JSONException e) {
      Log.e(TAG, "Invalid JSON message body");
      Log.e(TAG, messageBody);
    } catch (Exception e) {
      Log.w(TAG, "Exception occurred");
      e.printStackTrace();
    }
  }

  public void setForstaDistribution(ForstaDistribution forstaDistribution) {
    this.distribution = forstaDistribution;
  }

  public ForstaDistribution getForstaDistribution() {
    if (distribution == null) {
      return new ForstaDistribution(new JSONObject());
    }
    return distribution;
  }
}

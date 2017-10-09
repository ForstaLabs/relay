package io.forsta.ccsm.api.model;

import android.content.Context;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.ccsm.database.model.ForstaRecipient;
import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.ccsm.util.ForstaUtils;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.Util;

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

  private ForstaMessage() {

  }

  public static ForstaMessage fromJsonString(String messageBody) {
    ForstaMessage forstaMessage = new ForstaMessage();
    try {
      JSONObject jsonBody = getVersion(1, messageBody);
      if (jsonBody == null) {
        forstaMessage.textBody = messageBody;
      } else {
        forstaMessage.threadId = jsonBody.getString("threadId");
        forstaMessage.messageId = jsonBody.getString("messageId");
        if (jsonBody.has("threadTitle")) {
          forstaMessage.threadTitle = jsonBody.getString("threadTitle");
        }
        if (jsonBody.has("data")) {
          JSONObject data = jsonBody.getJSONObject("data");
          if (data.has("body")) {
            JSONArray body =  data.getJSONArray("body");
            for (int j=0; j<body.length(); j++) {
              JSONObject object = body.getJSONObject(j);
              if (object.getString("type").equals("text/html")) {
                forstaMessage.htmlBody = Html.fromHtml(object.getString("value"));
              }
              if (object.getString("type").equals("text/plain")) {
                forstaMessage.textBody = object.getString("value");
              }
            }
          }
        } else {
          forstaMessage.textBody = "";
        }
        JSONObject sender = jsonBody.getJSONObject("sender");
        forstaMessage.senderId = sender.getString("userId");
        JSONObject distribution = jsonBody.getJSONObject("distribution");
        forstaMessage.universalExpression = distribution.getString("expression");
      }
    } catch (JSONException e) {
      Log.e(TAG, "Invalid JSON message body");
      Log.e(TAG, messageBody);
    } catch (Exception e) {
      Log.w(TAG, "Exception occurred");
      e.printStackTrace();
    }
    return forstaMessage;
  }

  public void setForstaDistribution(ForstaDistribution forstaDistribution) {
    this.distribution = forstaDistribution;
  }

  public ForstaDistribution getForstaDistribution() {
    if (distribution == null) {
      return new ForstaDistribution();
    }
    return distribution;
  }

  public static boolean isJsonBody(String body) {
    return getVersion(1, body) != null;
  }

  public boolean hasThreadUid() {
    return !TextUtils.isEmpty(threadId);
  }

  public boolean hasDistributionExpression() {
    return !TextUtils.isEmpty(universalExpression);
  }

  public String getUniversalExpression() {
    return universalExpression;
  }

  public static JSONObject getVersion(int version, String body) {
    try {
      JSONArray jsonArray = new JSONArray(body);
      for (int i=0; i<jsonArray.length(); i++) {
        JSONObject versionObject = jsonArray.getJSONObject(i);
        if (versionObject.getInt("version") == version) {
          return versionObject;
        }
      }
    } catch (JSONException e) {
      Log.w(TAG, "JSON exception. No Forsta message body");
    }
    return null;
  }

  public static String createForstaMessageBody(Context context, String richTextMessage, Recipients messageRecipients) {
    return createForstaMessageBody(context, richTextMessage, messageRecipients, "", "", "");
  }

  public static String createForstaMessageBody(Context context, String message, Recipients recipients, ForstaThread forstaThread) {
    return createForstaMessageBody(context, message, recipients, forstaThread.distribution, forstaThread.title, forstaThread.uid);
  }

  public static String createForstaMessageBody(Context context, String richTextMessage, Recipients messageRecipients, String universalExpression, String threadTitle, String threadUid) {
    JSONArray versions = new JSONArray();
    JSONObject version1 = new JSONObject();
    ContactDb contactDb = DbFactory.getContactDb(context);

    // Looking for a empty expression being sent.
    if (TextUtils.isEmpty(universalExpression)) {
      Log.e(TAG, "No universal expression for thread: " + threadUid);
    }

    try {
      JSONObject data = new JSONObject();
      JSONArray body = new JSONArray();
      String threadType = "conversation";
      String messageType = "content";
      JSONObject sender = new JSONObject();
      JSONObject recipients = new JSONObject();
      JSONArray userIds = new JSONArray();
      String threadId = !TextUtils.isEmpty(threadUid) ? threadUid : "";

      ForstaUser user = ForstaUser.getLocalForstaUser(context);
      sender.put("tagId", user.tag_id);
      sender.put("tagPresentation", user.slug);
      sender.put("userId", user.uid);

      List<String> recipientList = new ArrayList<>();
      if (messageRecipients.isGroupRecipient()) {
        try {
          GroupDatabase groupDb = DatabaseFactory.getGroupDatabase(context);
          String endcodedGroupId = messageRecipients.getPrimaryRecipient().getNumber();
          GroupDatabase.GroupRecord group = groupDb.getGroup(GroupUtil.getDecodedId(endcodedGroupId));
          threadTitle = group.getTitle();
          recipientList = group.getMembers();
        } catch (IOException e) {
          Log.e(TAG, "createForstaMessageBody exception decoding group ID.");
          e.printStackTrace();
        }
      } else {
        for (String recipient : messageRecipients.toNumberStringList(false)) {
          try {
            recipientList.add(Util.canonicalizeNumber(context, recipient));
          } catch (InvalidNumberException e) {
            e.printStackTrace();
          }
        }
      }

      List<ForstaRecipient> forstaRecipients = contactDb.getRecipientsFromNumbers(recipientList);

      for (ForstaRecipient r : forstaRecipients) {
        userIds.put(r.uuid);
      }
      recipients.put("userIds", userIds);
      recipients.put("expression", universalExpression);

      JSONObject bodyHtml = new JSONObject();
      bodyHtml.put("type", "text/html");
      bodyHtml.put("value", richTextMessage);
      body.put(bodyHtml);

      JSONObject bodyPlain = new JSONObject();
      bodyPlain.put("type", "text/plain");
      Spanned stripMarkup = Html.fromHtml(richTextMessage);
      bodyPlain.put("value", stripMarkup);
      body.put(bodyPlain);

      data.put("body", body);
      version1.put("version", 1);
      version1.put("userAgent", System.getProperty("http.agent", ""));
      version1.put("messageId", UUID.randomUUID().toString());
      version1.put("messageType", messageType);
      version1.put("threadId", threadId);
      version1.put("threadTitle", threadTitle);
      version1.put("threadType", threadType);
      version1.put("sendTime", ForstaUtils.formatDateISOUTC(new Date()));
      version1.put("data", data);
      version1.put("sender", sender);
      version1.put("distribution", recipients);
      versions.put(version1);
    } catch (JSONException e) {
      Log.e(TAG, "createForstaMessageBody JSON exception");
      Log.e(TAG, "Recipient: "+ messageRecipients.getPrimaryRecipient().getNumber());
      Log.e(TAG, "Thread: "+ threadUid);
      e.printStackTrace();
      // Something failed. Return original message body
      return richTextMessage;
    }
    return versions.toString();
  }
}

package io.forsta.ccsm.util;

import android.content.Context;
import android.database.Cursor;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.ccsm.database.model.ForstaRecipient;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.Util;

/**
 * Created by jlewis on 6/5/17.
 */

public class ForstaUtils {
  private static final String TAG = ForstaUtils.class.getSimpleName();

  private static String hex(byte[] array) {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < array.length; ++i) {
      sb.append(Integer.toHexString((array[i]
          & 0xFF) | 0x100).substring(1,3));
    }
    return sb.toString();
  }
  public static String md5Hex (String message) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      return hex (md.digest(message.getBytes("CP1252")));
    } catch (NoSuchAlgorithmException e) {
    } catch (UnsupportedEncodingException e) {
    }
    return null;
  }

  public static boolean isJsonBody(String body) {
    try {
      JSONArray forstaArray = new JSONArray(body);
      for (int i=0; i<forstaArray.length(); i++) {
        JSONObject version = forstaArray.getJSONObject(i);
        if (version.getInt("version") == 1) {
          return true;
        }
      }
    } catch (JSONException e) {
      Log.w(TAG, "JSON exception. getForstaHtmlBody, Not a Forsta JSON message body");
    }
    return false;
  }

  public static Spanned getForstaHtmlBody(String messageBody) {
    try {
      JSONObject version = getVersion(1, messageBody);
      JSONObject data = version.getJSONObject("data");
      JSONArray body =  data.getJSONArray("body");
      for (int j=0; j<body.length(); j++) {
        JSONObject object = body.getJSONObject(j);
        if (object.getString("type").equals("text/html")) {
          return Html.fromHtml(object.getString("value"));
        }
      }
    } catch (JSONException e) {
      Log.w(TAG, "JSON exception. getForstaHtmlBody, Not a Forsta JSON message body");
    }
    return null;
  }

  public static String getForstaPlainTextBody(String messageBody) {
    try {
      JSONObject version = getVersion(1, messageBody);
      JSONObject data = version.getJSONObject("data");
      JSONArray body =  data.getJSONArray("body");
      for (int j=0; j<body.length(); j++) {
        JSONObject object = body.getJSONObject(j);
        if (object.getString("type").equals("text/plain")) {
          return object.getString("value");
        }
      }
    } catch (JSONException e) {
      Log.w(TAG, "JSON exception. getForstaPlainTextBody, Not a Forsta JSON message body");
    }
    return messageBody;
  }

  public static JSONArray getResolvedDistributionIds(JSONObject jsonObject) {
    JSONArray result = new JSONArray();



    return result;
  }

  public static String getMessageDistribution(String body) {
    String result = "";
    try {
      JSONObject jsonObject = getVersion(1, body);
      if (jsonObject.has("distribution")) {
        result = jsonObject.getString("distribution");
      }
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return result;
  }

  private static JSONObject getVersion(int version, String body) {
    try {
      JSONArray jsonArray = new JSONArray(body);
      for (int i=0; i<jsonArray.length(); i++) {
        JSONObject versionObject = jsonArray.getJSONObject(i);
        if (versionObject.getInt("version") == 1) {
          return versionObject;
        }
      }
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return new JSONObject();
  }

  public static String createForstaMessageBody(Context context, String richTextMessage, Recipients messageRecipients) {
    JSONArray versions = new JSONArray();
    JSONObject version1 = new JSONObject();
    ContactDb contactDb = DbFactory.getContactDb(context);
    try {
      version1.put("version", 1);
      JSONObject data = new JSONObject();
      JSONArray body = new JSONArray();
      String type = "ordinary";
      JSONObject sender = new JSONObject();
      JSONObject recipients = new JSONObject();
      JSONArray userIds = new JSONArray();
      JSONObject tagExpression = new JSONObject();
      String presentation = "";

      String threadId = "";
      String threadTitle = "";

      ForstaUser user = new ForstaUser(new JSONObject(ForstaPreferences.getForstaUser(context)));
      sender.put("tagId", user.tag_id);
      sender.put("tagPresentation", user.slug);
      sender.put("userId", user.uid);

      List<String> recipientList = new ArrayList<>();
      if (messageRecipients.isGroupRecipient()) {
        try {
          GroupDatabase groupDb = DatabaseFactory.getGroupDatabase(context);
          String endcodedGroupId = messageRecipients.getPrimaryRecipient().getNumber();
          GroupDatabase.GroupRecord group = groupDb.getGroup(GroupUtil.getDecodedId(endcodedGroupId));
          threadId = new String(GroupUtil.getDecodedId(endcodedGroupId));
          threadTitle = group.getTitle();
          recipientList = group.getMembers();
          presentation = group.getSlug();
        } catch (IOException e) {
          Log.e(TAG, "createForstaMessageBody exception decoding group ID.");
          e.printStackTrace();
        }
      } else {
        List<String> singleRecipient = messageRecipients.toNumberStringList(false);
        List<ForstaRecipient> forstaSingleRecipients = contactDb.getRecipientsFromNumbers(singleRecipient);
        List<String> forstaSlugs = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (ForstaRecipient recipient : forstaSingleRecipients) {
          forstaSlugs.add(recipient.slug);
          // Should only ever be one recipient. Groups represent multiple recipients, unless it is a mix of secure and non-secure recipients.
          names.add(TextUtils.isEmpty(recipient.name) ? recipient.number : recipient.name);
        }

        // If the recipients are unknown to CCSM
        for (Recipient unknownRecipient : messageRecipients.getRecipientsList()) {
          names.add(TextUtils.isEmpty(unknownRecipient.getName()) ? unknownRecipient.getNumber(): unknownRecipient.getName());
        }

        threadId = forstaSingleRecipients.size() > 0 ? forstaSingleRecipients.get(0).uuid : "";
        threadTitle = TextUtils.join(",", names);

        presentation = TextUtils.join("+", forstaSlugs);
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
      tagExpression.put("type", "+");
      tagExpression.put("presentation", presentation);
      recipients.put("userIds", userIds);
      recipients.put("expression", tagExpression);

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
      version1.put("messageId", UUID.randomUUID().toString());
      version1.put("threadId", threadId);
      version1.put("threadTitle", threadTitle);
      version1.put("type", type);
      version1.put("sendTime", formatDateISOUTC(new Date()));
      version1.put("data", data);
      version1.put("sender", sender);
      version1.put("recipients", recipients);
      versions.put(version1);
    } catch (JSONException e) {
      Log.e(TAG, "createForstaMessageBody JSON exception");
      e.printStackTrace();
      // Something failed. Return original message body
      return richTextMessage;
    }
    return versions.toString();
  }

  public static String formatDateISOUTC(Date date) {
    TimeZone tz = TimeZone.getTimeZone("UTC");
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
    df.setTimeZone(tz);
    return df.format(date);
  }

  public static String getForstaGroupTitle(String name) {
    try {
      JSONObject nameObj = new JSONObject(name);
      String title = nameObj.getString("title");
      return title;
    } catch (JSONException e) {
      Log.w(TAG, "JSON exception. Not a Forsta group title blob.");
    }
    return null;
  }
}

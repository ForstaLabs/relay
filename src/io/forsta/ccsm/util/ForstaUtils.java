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
import io.forsta.ccsm.database.model.ForstaThread;
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
    try {
      JSONObject data = new JSONObject();
      JSONArray body = new JSONArray();
      String threadType = "conversation";
      String messageType = "content";
      JSONObject sender = new JSONObject();
      JSONObject recipients = new JSONObject();
      JSONArray userIds = new JSONArray();
      String threadId = !TextUtils.isEmpty(threadUid) ? threadUid : "";

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
      version1.put("sendTime", formatDateISOUTC(new Date()));
      version1.put("data", data);
      version1.put("sender", sender);
      version1.put("distribution", recipients);
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

}

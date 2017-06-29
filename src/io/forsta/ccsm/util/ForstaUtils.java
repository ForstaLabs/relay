package io.forsta.ccsm.util;

import android.content.Context;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.util.Log;

import com.fasterxml.jackson.core.JsonParseException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

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

  public static Spanned getForstaJsonBody(String messageBody) {
    try {
      JSONArray forstaObject= new JSONArray(messageBody);
      JSONObject version = forstaObject.getJSONObject(0);
      JSONObject data = version.getJSONObject("data");
      JSONArray body =  data.getJSONArray("body");
      for (int i=0; i<body.length(); i++) {
        JSONObject object = body.getJSONObject(i);
        String type = object.getString("type");
        if (object.getString("type").equals("text/html")) {
          String htmlText = object.getString("value");
          return Html.fromHtml(htmlText);
        }
      }
    } catch (JSONException e) {
      Log.e(TAG, "JSON exception. Not a Forsta JSON message body");
    }
    return null;
  }

  public static String getForstaPlainTextBody(String messageBody) {
    try {
      JSONArray forstaObject= new JSONArray(messageBody);
      JSONObject version = forstaObject.getJSONObject(0);
      JSONObject data = version.getJSONObject("data");
      JSONArray body =  data.getJSONArray("body");
      for (int i=0; i<body.length(); i++) {
        JSONObject object = body.getJSONObject(i);
        String type = object.getString("type");
        if (object.getString("type").equals("text/plain")) {
          return object.getString("value");
        }
      }
    } catch (JSONException e) {
      Log.w(TAG, "JSON exception. Not a Forsta JSON message body");
    }
    return null;
  }

  public static String createForstaMessageBody(Context context, String richTextMessage, Recipients messageRecipients) {
    JSONArray versions = new JSONArray();
    JSONObject version1 = new JSONObject();
    try {
      version1.put("version", "1");
      JSONObject data = new JSONObject();
      JSONArray body = new JSONArray();
      String type = "ordinary";
      JSONObject sender = new JSONObject();
      JSONObject recipients = new JSONObject();
      JSONArray resolvedUsers = new JSONArray();
      JSONArray resolvedNumbers = new JSONArray();
      JSONObject resolvedUser = new JSONObject();

      ForstaUser user = new ForstaUser(new JSONObject(ForstaPreferences.getForstaUser(context)));
      sender.put("tagId", user.tag_id);
      sender.put("tagPresentation", user.slug);

      resolvedUser.put("orgId", user.org_id);
      resolvedUser.put("userId", user.uid);
      sender.put("resolvedUser", resolvedUser);
      sender.put("resolvedNumber", user.phone);

      List<String> recipientList = new ArrayList<>();
      if (messageRecipients.isGroupRecipient()) {
        try {
          GroupDatabase groupDb = DatabaseFactory.getGroupDatabase(context);
          String endcodedGroupId = messageRecipients.getPrimaryRecipient().getNumber();
          GroupDatabase.GroupRecord group = groupDb.getGroup(GroupUtil.getDecodedId(endcodedGroupId));
          recipientList = group.getMembers();
        } catch (IOException e) {
          Log.e(TAG, "createForstaMessageBody exception decoding group ID.");
          e.printStackTrace();
        }
      } else {
        recipientList = messageRecipients.toNumberStringList(false);
      }

      List<ForstaRecipient> forstaRecipients = DbFactory.getContactDb(context).getRecipientsFromNumbers(recipientList);

      for (ForstaRecipient r : forstaRecipients) {
        resolvedNumbers.put(r.number);
        JSONObject forstaUser = new JSONObject();
        forstaUser.put("orgId", r.org);
        forstaUser.put("userId", r.uuid);
        resolvedUsers.put(forstaUser);
      }
      recipients.put("resolvedUsers", resolvedUsers);
      recipients.put("resolvedNumbers", resolvedNumbers);

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
      version1.put("type", type);
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

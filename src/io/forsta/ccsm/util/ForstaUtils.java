package io.forsta.ccsm.util;

import android.content.Context;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.Recipients;

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
      Log.e(TAG, "JSON exception. Not a Forsta JSON message body");
    }
    return null;
  }

  public static String createForstaMessageBody(String richTextMessage, Recipients messageRecipients) {
    JSONArray versions = new JSONArray();
    JSONObject version1 = new JSONObject();
    try {
      version1.put("version", "version 1");
      JSONObject data = new JSONObject();
      JSONArray body = new JSONArray();
      String type = "ordinary";
      JSONObject sender = new JSONObject();
      JSONObject recipients = new JSONObject();
      JSONArray resolvedUsers = new JSONArray();
      JSONArray resolvedNumbers = new JSONArray();

      for (Recipient r : messageRecipients.getRecipientsList()) {
        resolvedNumbers.put(r.getNumber());
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
      Log.e(TAG, "JSON exception creating message body");
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
      Log.e(TAG, "JSON exception. Not a Forsta group title blob.");
    }
    return null;
  }
}

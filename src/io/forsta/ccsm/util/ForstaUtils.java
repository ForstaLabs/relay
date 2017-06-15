package io.forsta.ccsm.util;

import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by jlewis on 6/5/17.
 */

public class ForstaUtils {

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
      e.printStackTrace();
    }
    return null;
  }
}

package io.forsta.ccsm.util;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.TimeZone;

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

  public static String formatDateISOUTC(Date date) {
    TimeZone tz = TimeZone.getTimeZone("UTC");
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
    df.setTimeZone(tz);
    return df.format(date);
  }

  public static String slugify(String fullName) {
    String slug = fullName.replaceAll("[^\\w\\s-]+", "");
    slug = slug.replaceAll("\\s+$", "");
    slug = slug.replaceAll("[\\s_-]+", ".");
    slug = slug.replaceAll("^[+!|-]+$", ".");
    return slug.toLowerCase();
  }

  public static String parseErrors(JSONObject jsonObject) {
    StringBuilder sb = new StringBuilder();
    if (jsonObject != null) {
      Iterator<String> keys = jsonObject.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        JSONObject object = jsonObject.optJSONObject(key);
        if (object != null) {
          Iterator<String> objKeys = object.keys();
          while (objKeys.hasNext()) {
            String objKey = objKeys.next();
            JSONArray messages = object.optJSONArray(objKey);
            if (messages != null) {
              // Just get the first error.
              String message = messages.optString(0, "No error");
              sb.append(message).append(" ");
            } else {
              sb.append("No Errors");
            }
          }
        } else {
          String message = jsonObject.optString(key);
          sb.append(message);
        }
      }
    }
    return sb.toString();
  }
}

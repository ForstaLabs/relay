package io.forsta.ccsm.util;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
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

}

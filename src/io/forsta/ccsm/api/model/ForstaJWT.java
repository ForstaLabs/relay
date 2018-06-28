package io.forsta.ccsm.api.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Date;

import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.securesms.util.Base64;

/**
 * Created by jlewis on 2/6/17.
 */

public class ForstaJWT {
  private String jwt = "";
  private String[] tokenParts;

  public ForstaJWT(String token) {
    this.jwt = token;
    tokenParts = jwt.split("\\.");
  }

  private String getHeader() {
    if (tokenParts.length > 0) {
      return tokenParts[0];
    }
    return "";
  }

  private String getPayload() {
    if (tokenParts.length > 1) {
      return tokenParts[1];
    }
    return "";
  }

  public Date getExpireDate() {
    Date expireDate = null;
    String payload = getPayload();
    try {
      byte[] payloadBytes = Base64.decodeWithoutPadding(payload);
      String payloadString = new String(payloadBytes, "UTF-8");
      JSONObject obj = new JSONObject(payloadString);
      if (obj.has("exp")) {
        int expire = obj.getInt("exp");
        long expireTime = (long) expire * 1000;
        expireDate = new Date(expireTime);
      }
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return expireDate;
  }

  public String getUid() {
    String payload = getPayload();
    try {
      byte[] payloadBytes = Base64.decodeWithoutPadding(payload);
      String payloadString = new String(payloadBytes, "UTF-8");
      JSONObject obj = new JSONObject(payloadString);
      return obj.getString("user_id");
    } catch (IOException e) {
      e.printStackTrace();
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return null;
  }
}

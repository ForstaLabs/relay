package io.forsta.ccsm.messaging;

import android.content.Context;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;

import io.forsta.ccsm.ThreadPreferenceActivity;
import io.forsta.ccsm.api.model.ForstaControlMessage;
import io.forsta.ccsm.api.model.ForstaMessage;
import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.ccsm.util.InvalidMessagePayloadException;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.MasterSecretUnion;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.mms.OutgoingMediaMessage;
import io.forsta.securesms.mms.OutgoingSecureMediaMessage;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.sms.MessageSender;
import ws.com.google.android.mms.MmsException;

/**
 * Created by jlewis on 10/25/17.
 */

public class ForstaMessageManager {
  private static final String TAG = ForstaMessageManager.class.getSimpleName();

  public static JSONObject getMessageVersion(int version, String body)
      throws InvalidMessagePayloadException {
    try {
      JSONArray jsonArray = new JSONArray(body);
      for (int i=0; i<jsonArray.length(); i++) {
        JSONObject versionObject = jsonArray.getJSONObject(i);
        if (versionObject.getInt("version") == version) {
          return versionObject;
        }
      }
    } catch (JSONException e) {
      Log.w(TAG, e);
    }
    throw new InvalidMessagePayloadException(body);
  }

  private static boolean isContentType(JSONObject jsonBody) throws JSONException {
    if (jsonBody.getString("messageType").equals("content")) {
      return true;
    }
    return false;
  }

  private static boolean isControlType(JSONObject jsonBody) throws JSONException {
    if (jsonBody.has("messageType")) {
      if (jsonBody.getString("messageType").equals("control")) {
        return true;
      }
    }
    return false;
  }

  public static boolean isJsonBody(String body) {
    try {
      getMessageVersion(1, body);
    } catch (InvalidMessagePayloadException e) {
      return false;
    }
    return true;
  }

  public static ForstaMessage fromJsonString(String messageBody) {
    ForstaMessage forstaMessage = new ForstaMessage();
    try {
      forstaMessage = fromMessagBodyString(messageBody);
    } catch (InvalidMessagePayloadException e) {
      Log.e(TAG, "Invalid message payload: " + e.getMessage());
      Log.e(TAG, messageBody);
      forstaMessage.setTextBody("Invalid message format");
    }
    return forstaMessage;
  }


  public static ForstaMessage fromMessagBodyString(String messageBody) throws InvalidMessagePayloadException {
    JSONObject jsonBody = getMessageVersion(1, messageBody);
    try {
      if (isContentType(jsonBody)) {
        return handleContentType(jsonBody);
      } else if (isControlType(jsonBody)) {
        return handleControlType(jsonBody);
      } else {
        throw new InvalidMessagePayloadException("Unsupported messageType");
      }
    } catch (JSONException e) {
      throw new InvalidMessagePayloadException(e.getMessage());
    }
  }

  private static ForstaMessage handleControlType(JSONObject jsonBody) throws InvalidMessagePayloadException {
    ForstaMessage forstaMessage = new ForstaMessage();
    try {
      forstaMessage.setMessageType(ForstaMessage.MessageType.CONTROL);
      JSONObject data = jsonBody.getJSONObject("data");
      if (data.getString("control").equals("threadUpdate")) {
        forstaMessage.setControlType(ForstaMessage.ControlType.THREAD_UPDATE);
        JSONObject threadUpdates = data.getJSONObject("threadUpdates");
        forstaMessage.setThreadUid(threadUpdates.getString("threadId"));
        if (threadUpdates.has("threadTitle")) {
          forstaMessage.setThreadTitle(threadUpdates.getString("threadTitle"));
        }
      }
    } catch (JSONException e) {
      throw new InvalidMessagePayloadException(e.getMessage());
    }

    return forstaMessage;
  }

  private static ForstaMessage handleContentType(JSONObject jsonBody) throws InvalidMessagePayloadException {
    ForstaMessage forstaMessage = new ForstaMessage();
    try {
      forstaMessage.setThreadUid(jsonBody.getString("threadId"));
      if (jsonBody.has("threadTitle")) {
        forstaMessage.setThreadTitle(jsonBody.getString("threadTitle"));
      }
      JSONObject distribution = jsonBody.getJSONObject("distribution");
      forstaMessage.setUniversalExpression(distribution.getString("expression"));
      if (TextUtils.isEmpty(forstaMessage.getUniversalExpression())) {
        throw new InvalidMessagePayloadException("No universal expression");
      }
      JSONObject sender = jsonBody.getJSONObject("sender");
      forstaMessage.setSenderId(sender.getString("userId"));
      forstaMessage.setMessageId(jsonBody.getString("messageId"));
      if (jsonBody.has("data")) {
        JSONObject data = jsonBody.getJSONObject("data");
        if (data.has("body")) {
          JSONArray body =  data.getJSONArray("body");
          for (int j=0; j<body.length(); j++) {
            JSONObject object = body.getJSONObject(j);
            if (object.getString("type").equals("text/html")) {
              forstaMessage.setHtmlBody(Html.fromHtml(object.getString("value")));
            }
            if (object.getString("type").equals("text/plain")) {
              forstaMessage.setTextBody(object.getString("value"));
            }
          }
        }
      }
    } catch (JSONException e) {
      throw new InvalidMessagePayloadException(e.getMessage());
    }

    return forstaMessage;
  }

  public static void sendThreadUpdate(Context context, MasterSecret masterSecret, Recipients recipients, long threadId) {
    try {
      OutgoingMediaMessage message = new OutgoingMediaMessage(recipients, "Control Message", new LinkedList<Attachment>(),  System.currentTimeMillis(), -1, 0, ThreadDatabase.DistributionTypes.DEFAULT);
      message = new OutgoingSecureMediaMessage(message);
      ForstaThread threadData = DatabaseFactory.getThreadDatabase(context).getForstaThread(threadId);
      message.setForstaControlJsonBody(context, threadData);
      MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
      long messageId  = database.insertMessageOutbox(new MasterSecretUnion(masterSecret), message, -1, false);
      MessageSender.sendMediaMessage(context, masterSecret, recipients, false, messageId, 0);
    } catch (MmsException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

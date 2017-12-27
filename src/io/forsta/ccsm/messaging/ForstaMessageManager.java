package io.forsta.ccsm.messaging;

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
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import io.forsta.ccsm.api.model.ForstaMessage;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.ccsm.util.ForstaUtils;
import io.forsta.ccsm.util.InvalidMessagePayloadException;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.crypto.MasterSecretUnion;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.ThreadDatabase;
import io.forsta.securesms.mms.OutgoingMediaMessage;
import io.forsta.securesms.mms.OutgoingSecureMediaMessage;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.sms.MessageSender;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.MediaUtil;
import io.forsta.securesms.util.Util;
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
    if (jsonBody.getString("messageType").equals(ForstaMessage.MessageTypes.CONTENT)) {
      return true;
    }
    return false;
  }

  private static boolean isControlType(JSONObject jsonBody) throws JSONException {
    if (jsonBody.has("messageType")) {
      if (jsonBody.getString("messageType").equals(ForstaMessage.MessageTypes.CONTROL)) {
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
      Log.e(TAG, "Invalid message payload!");
      Log.e(TAG, messageBody);
      forstaMessage.setTextBody(messageBody);
    }
    return forstaMessage;
  }

  public static ForstaMessage fromMessagBodyString(String messageBody) throws InvalidMessagePayloadException {
    JSONObject jsonBody = getMessageVersion(1, messageBody);
    return parseMessageBody(jsonBody);
  }

  private static ForstaMessage parseMessageBody(JSONObject jsonBody) throws InvalidMessagePayloadException {
    ForstaMessage forstaMessage = new ForstaMessage();
    try {
      forstaMessage.setThreadUid(jsonBody.getString("threadId"));
      if (jsonBody.has("threadTitle")) {
        forstaMessage.setThreadTitle(jsonBody.getString("threadTitle"));
      }

      if (jsonBody.has("threadType")) {
        forstaMessage.setThreadType(jsonBody.getString("threadType"));
      }

      if (jsonBody.has("messageType")) {
        forstaMessage.setMessageType(jsonBody.getString("messageType"));
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
        if (data.has("attachments")) {
          JSONArray attachments = data.getJSONArray("attachments");
          for (int i=0; i<attachments.length(); i++) {
            JSONObject object = attachments.getJSONObject(i);
            String name = object.getString("name");
            String type = object.getString("type");
            long size = object.getLong("size");
            forstaMessage.addAttachment(name, type, size);
          }
        }

        if (data.has("control")) {
          forstaMessage.setControlType(data.getString("control"));
          Log.w(TAG, "Control message: " + forstaMessage.getControlType());

          switch (forstaMessage.getControlType()) {
            case ForstaMessage.ControlTypes.THREAD_UPDATE:
              Log.w(TAG, "Thread update: ");
              JSONObject threadUpdates = data.getJSONObject("threadUpdates");
              if (threadUpdates.has("threadTitle")) {
                forstaMessage.setThreadTitle(threadUpdates.getString("threadTitle"));
                Log.w(TAG, "New thread title: " + forstaMessage.getThreadTitle());
              }
              break;
            default:
              Log.w(TAG, "Not a control message");
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

  public static String createControlMessageBody(Context context, String message, Recipients recipients, List<Attachment> messageAttachments, ForstaThread forstaThread) {
    return createForstaMessageBody(context, message, recipients, messageAttachments, forstaThread, ForstaMessage.MessageTypes.CONTROL);
  }

  public static String createForstaMessageBody(Context context, String message, Recipients recipients, List<Attachment> messageAttachments, ForstaThread forstaThread) {
    return createForstaMessageBody(context, message, recipients, messageAttachments, forstaThread, ForstaMessage.MessageTypes.CONTENT);
  }

  public static String createForstaMessageBody(Context context, String richTextMessage, Recipients messageRecipients, List<Attachment> messageAttachments, ForstaThread forstaThread, String type) {
    JSONArray versions = new JSONArray();
    JSONObject version1 = new JSONObject();
    ContactDb contactDb = DbFactory.getContactDb(context);
    String title = forstaThread.getTitle();
    try {
      JSONObject data = new JSONObject();
      JSONArray body = new JSONArray();
      String messageType = "content";
      if (type.equals(ForstaMessage.MessageTypes.CONTROL)) {
        messageType = "control";
        data.put("control", "threadUpdate");
        JSONObject threadUpdates = new JSONObject();
        threadUpdates.put("threadTitle", title);
        threadUpdates.put("threadId", forstaThread.getUid());
        data.put("threadUpdates", threadUpdates);
      }

      String threadType = forstaThread.getThreadType() == 1 ? "announcement" : "conversation";
      JSONObject sender = new JSONObject();
      JSONObject recipients = new JSONObject();
      JSONArray userIds = new JSONArray();
      JSONArray attachments = new JSONArray();

      String threadId = !TextUtils.isEmpty(forstaThread.getUid()) ? forstaThread.getUid() : "";

      ForstaUser user = ForstaUser.getLocalForstaUser(context);
      sender.put("tagId", user.tag_id);
      sender.put("tagPresentation", user.slug);
      sender.put("userId", user.uid);

      List<String> recipientList = new ArrayList<>();
      if (messageRecipients.isGroupRecipient()) {
        // XXX Obsolete. REMOVE.
        Log.e(TAG, "ERROR: Group message received!!!!");
        try {
          GroupDatabase groupDb = DatabaseFactory.getGroupDatabase(context);
          String endcodedGroupId = messageRecipients.getPrimaryRecipient().getNumber();
          GroupDatabase.GroupRecord group = groupDb.getGroup(GroupUtil.getDecodedId(endcodedGroupId));
          title = group.getTitle();
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

      if (attachments != null) {
        for (Attachment attachment : messageAttachments) {
          JSONObject attachmentJson = new JSONObject();
          attachmentJson.put("name", MediaUtil.getFileName(context, attachment.getDataUri()));
          attachmentJson.put("size", attachment.getSize());
          attachmentJson.put("type", attachment.getContentType());
          attachments.put(attachmentJson);
        }
      }

      List<ForstaUser> forstaUsers = contactDb.getUsersByAddresses(recipientList);
      for (ForstaUser x : forstaUsers) {
        userIds.put(x.getUid());
      }

      recipients.put("userIds", userIds);
      recipients.put("expression", forstaThread.getDistribution());

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
      data.put("attachments", attachments);
      version1.put("version", 1);
      version1.put("userAgent", System.getProperty("http.agent", ""));
      version1.put("messageId", UUID.randomUUID().toString());
      version1.put("messageType", messageType);
      version1.put("threadId", threadId);
      version1.put("threadTitle", title);
      version1.put("threadType", threadType);
      version1.put("sendTime", ForstaUtils.formatDateISOUTC(new Date()));
      version1.put("data", data);
      version1.put("sender", sender);
      version1.put("distribution", recipients);
      versions.put(version1);
    } catch (JSONException e) {
      Log.e(TAG, "createForstaMessageBody JSON exception");
      Log.e(TAG, "Recipient: "+ messageRecipients.getPrimaryRecipient().getNumber());
      Log.e(TAG, "Thread: "+ forstaThread.getUid());
      e.printStackTrace();
      // Something failed. Return original message body
      return richTextMessage;
    }
    return versions.toString();
  }
}

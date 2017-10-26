package io.forsta.ccsm.api.model;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.forsta.ccsm.ForstaPreferences;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.ccsm.database.model.ForstaRecipient;
import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.ccsm.util.ForstaUtils;
import io.forsta.ccsm.util.InvalidMessagePayloadException;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.attachments.DatabaseAttachment;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.mms.AttachmentManager;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.Util;

/**
 * Created by jlewis on 9/6/17.
 */

public class ForstaMessage {
  private static final String TAG = ForstaMessage.class.getSimpleName();
  private String textBody = "";
  private Spanned htmlBody;
  private String messageId;
  private String senderId;
  private String universalExpression;
  private String threadUid;
  private String threadTitle;
  private MessageType messageType = MessageType.CONTENT;
  private ControlType controlType = ControlType.NONE;

  public enum ControlType {
    NONE,
    THREAD_UPDATE,
    THREAD_CLEAR,
    THREAD_CLOSE,
    THREAD_DELETE,
    SNOOZE,
    PROVISION_REQUEST,
    SYNC_REQUEST,
    SYNC_RESPONSE,
    DISCOVER,
    DISCOVER_RESPONSE
  }

  public enum MessageType {
    CONTENT,
    CONTROL
  }

  protected ForstaMessage() {

  }

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

  private boolean isContentType(JSONObject jsonBody) throws JSONException {
    if (jsonBody.getString("messageType").equals("content")) {
      return true;
    }
    return false;
  }

  private boolean isControlType(JSONObject jsonBody) throws JSONException {
    if (jsonBody.has("messageType")) {
      if (jsonBody.getString("messageType").equals("control")) {
        return true;
      }
    }
    return false;
  }

  private boolean isConversationType(JSONObject jsonBody) throws JSONException {
    if (jsonBody.has("threadType")) {
      if (jsonBody.getString("threadType").equals("conversation")) {
        return true;
      }
    }
    return false;
  }

  public static boolean isJsonBody(String body) {
    return getVersion(1, body) != null;
  }

  public static ForstaMessage fromMessagBodyString(String messageBody) throws InvalidMessagePayloadException {
    ForstaMessage forstaMessage = new ForstaMessage();

    JSONObject jsonBody = getMessageVersion(1, messageBody);
    try {
      if (forstaMessage.isContentType(jsonBody)) {
        forstaMessage.threadUid = jsonBody.getString("threadId");
        if (jsonBody.has("threadTitle")) {
          forstaMessage.threadTitle = jsonBody.getString("threadTitle");
        }
        JSONObject distribution = jsonBody.getJSONObject("distribution");
        forstaMessage.universalExpression = distribution.getString("expression");
        if (TextUtils.isEmpty(forstaMessage.universalExpression)) {
          throw new InvalidMessagePayloadException("No universal expression");
        }
        JSONObject sender = jsonBody.getJSONObject("sender");
        forstaMessage.senderId = sender.getString("userId");
        forstaMessage.messageId = jsonBody.getString("messageId");
        JSONObject data = jsonBody.getJSONObject("data");
        if (data.has("body")) {
          JSONArray body =  data.getJSONArray("body");
          for (int j=0; j<body.length(); j++) {
            JSONObject object = body.getJSONObject(j);
            if (object.getString("type").equals("text/html")) {
              forstaMessage.htmlBody = Html.fromHtml(object.getString("value"));
            }
            if (object.getString("type").equals("text/plain")) {
              forstaMessage.textBody = object.getString("value");
            }
          }
        }
      } else if (forstaMessage.isControlType(jsonBody)) {
        forstaMessage.messageType = MessageType.CONTROL;
        JSONObject data = jsonBody.getJSONObject("data");
        if (data.getString("control").equals("threadUpdate")) {
          forstaMessage.controlType = ControlType.THREAD_UPDATE;
          JSONObject threadUpdates = data.getJSONObject("threadUpdates");
          forstaMessage.threadUid = threadUpdates.getString("threadId");
          if (threadUpdates.has("threadTitle")) {
            forstaMessage.threadTitle = threadUpdates.getString("threadTitle");
          }
        }
      } else {
        throw new InvalidMessagePayloadException("Invalid messageType");
      }
      return forstaMessage;
    } catch (JSONException e) {
      throw new InvalidMessagePayloadException(e.getMessage());
    }
  }

  public static ForstaMessage fromJsonString(String messageBody) {
    ForstaMessage forstaMessage = new ForstaMessage();
    try {
      forstaMessage = fromMessagBodyString(messageBody);
    } catch (InvalidMessagePayloadException e) {
      Log.e(TAG, "Invalid message payload: " + e.getMessage());
      Log.e(TAG, messageBody);
      forstaMessage.textBody = "Invalid message format";
    }
    return forstaMessage;
  }

  public boolean hasThreadUid() {
    return !TextUtils.isEmpty(threadUid);
  }

  public boolean hasDistributionExpression() {
    return !TextUtils.isEmpty(universalExpression);
  }

  public String getUniversalExpression() {
    return universalExpression;
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

  public static String createControlMessageBody(Context context, String message, Recipients recipients, List<Attachment> messageAttachments, ForstaThread forstaThread) {
    return createForstaMessageBody(context, message, recipients, messageAttachments, forstaThread.getDistribution(), forstaThread.getTitle(), forstaThread.getUid(), MessageType.CONTROL);
  }

  public static String createForstaMessageBody(Context context, String richTextMessage, Recipients messageRecipients) {
    return createForstaMessageBody(context, richTextMessage, messageRecipients, new ArrayList<Attachment>(), "", "", "", MessageType.CONTENT);
  }

  public static String createForstaMessageBody(Context context, String message, Recipients recipients, List<Attachment> messageAttachments, ForstaThread forstaThread) {
    return createForstaMessageBody(context, message, recipients, messageAttachments, forstaThread.getDistribution(), forstaThread.getTitle(), forstaThread.getUid(), MessageType.CONTENT);
  }

  public static String createForstaMessageBody(Context context, String richTextMessage, Recipients messageRecipients, List<Attachment> messageAttachments, String universalExpression, String threadTitle, String threadUid, MessageType type) {
    JSONArray versions = new JSONArray();
    JSONObject version1 = new JSONObject();
    ContactDb contactDb = DbFactory.getContactDb(context);

    try {
      JSONObject data = new JSONObject();
      JSONArray body = new JSONArray();
      String messageType = "content";
      if (type == MessageType.CONTROL) {
        messageType = "control";
        data.put("control", "threadUpdate");
        JSONObject threadUpdates = new JSONObject();
        threadUpdates.put("threadTitle", threadTitle);
        threadUpdates.put("threadId", threadUid);
        data.put("threadUpdates", threadUpdates);
      }

      String threadType = "conversation";
      JSONObject sender = new JSONObject();
      JSONObject recipients = new JSONObject();
      JSONArray userIds = new JSONArray();
      JSONArray attachments = new JSONArray();

      String threadId = !TextUtils.isEmpty(threadUid) ? threadUid : "";

      ForstaUser user = ForstaUser.getLocalForstaUser(context);
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

      if (attachments != null) {
        for (Attachment attachment : messageAttachments) {
          JSONObject attachmentJson = new JSONObject();
          attachmentJson.put("name", "");
          attachmentJson.put("size", attachment.getSize());
          attachmentJson.put("type", attachment.getContentType());
          attachments.put(attachmentJson);
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
      data.put("attachments", attachments);
      version1.put("version", 1);
      version1.put("userAgent", System.getProperty("http.agent", ""));
      version1.put("messageId", UUID.randomUUID().toString());
      version1.put("messageType", messageType);
      version1.put("threadId", threadId);
      version1.put("threadTitle", threadTitle);
      version1.put("threadType", threadType);
      version1.put("sendTime", ForstaUtils.formatDateISOUTC(new Date()));
      version1.put("data", data);
      version1.put("sender", sender);
      version1.put("distribution", recipients);
      versions.put(version1);
    } catch (JSONException e) {
      Log.e(TAG, "createForstaMessageBody JSON exception");
      Log.e(TAG, "Recipient: "+ messageRecipients.getPrimaryRecipient().getNumber());
      Log.e(TAG, "Thread: "+ threadUid);
      e.printStackTrace();
      // Something failed. Return original message body
      return richTextMessage;
    }
    return versions.toString();
  }

  public String getThreadUId() {
    return threadUid;
  }

  public String getThreadTitle() {
    return threadTitle;
  }

  public String getTextBody() {
    return textBody;
  }

  public Spanned getHtmlBody() {
    return htmlBody;
  }

  public MessageType getMessageType() {
    return messageType;
  }

  public ControlType getControlType() {
    return controlType;
  }
}

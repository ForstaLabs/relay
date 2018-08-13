package io.forsta.ccsm.messaging;

import android.content.Context;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import io.forsta.securesms.recipients.Recipient;
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
      Log.w(TAG, body);
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

      JSONObject sender = jsonBody.getJSONObject("sender");
      forstaMessage.setSenderId(sender.getString("userId"));
      forstaMessage.setMessageId(jsonBody.getString("messageId"));

      JSONObject distribution = jsonBody.getJSONObject("distribution");
      if (distribution.has("expression")) {
        forstaMessage.setUniversalExpression(distribution.getString("expression"));
      }

      if (jsonBody.has("data")) {
        JSONObject data = jsonBody.getJSONObject("data");
        if (data.has("body")) {
          JSONArray body =  data.getJSONArray("body");
          for (int j=0; j<body.length(); j++) {
            JSONObject object = body.getJSONObject(j);
            if (object.getString("type").equals("text/html")) {
              forstaMessage.setHtmlBody(object.getString("value"));
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
        if (data.has("mentions")) {
          JSONArray mentions = data.getJSONArray(("mentions"));
          for (int i=0; i<mentions.length(); i++) {
            String id = mentions.getString(i);
            forstaMessage.addMention(id);
          }
        }

        if (data.has("messageRef")) {
          String messageId = data.getString("messageRef");
          forstaMessage.setMessageRef(messageId);
          if (data.has("vote")) {
            int vote = data.getInt("vote");
            forstaMessage.setVote(vote);
          }
        }

        if (data.has("control")) {
          forstaMessage.setControlType(data.getString("control"));

          switch (forstaMessage.getControlType()) {
            case ForstaMessage.ControlTypes.THREAD_UPDATE:
              if (TextUtils.isEmpty(forstaMessage.getUniversalExpression())) {
                throw new InvalidMessagePayloadException("Thread update. No universal expression.");
              }
              JSONObject threadUpdates = data.getJSONObject("threadUpdates");
              if (threadUpdates.has("threadTitle")) {
                forstaMessage.setThreadTitle(threadUpdates.getString("threadTitle"));
              }
              break;
            case ForstaMessage.ControlTypes.PROVISION_REQUEST:
              String uuid = data.getString("uuid");
              String key = data.getString("key");
              forstaMessage.setProvisionRequest(uuid, key);
              break;
            case ForstaMessage.ControlTypes.CALL_OFFER:
              if (data.has("offer")) {
                String originator = data.getString("originator");
                String callId = data.getString("callId");
                JSONObject offer = data.getJSONObject("offer");
                String spd = offer.optString("sdp");
                String peerId = data.getString("peerId");
                forstaMessage.setCallOffer(callId, originator, peerId, spd);
                Log.w(TAG, "Incomming call offer from: " + originator + " :" + offer.toString());

              } else {
                Log.w(TAG, "Not a valid callOffer control message");
              }
              break;
            case ForstaMessage.ControlTypes.CALL_ICE_CANDIDATES:
              if (data.has("icecandidates")) {
                String originator = data.getString("originator");
                String callId = data.getString("callId");
                String peerId = data.getString("peerId");
                JSONArray callIceCandidates = data.getJSONArray("icecandidates");
                List<IceCandidate> candidates = new ArrayList<>();
                for (int i=0; i<callIceCandidates.length(); i++) {
                  JSONObject iceCandidate = callIceCandidates.getJSONObject(i);
                  String spdMid = iceCandidate.getString("sdpMid");
                  int spdLineIndex = iceCandidate.getInt("sdpMLineIndex");
                  String spd = iceCandidate.getString("candidate");
                  candidates.add(new IceCandidate(spdMid, spdLineIndex, spd));
                }
                forstaMessage.setIceCandidates(callId, originator, peerId, candidates);
                Log.w(TAG, "Incoming call ICE candidates from: " + originator + " :" + callIceCandidates.toString());
              } else {
                Log.w(TAG, "Not a valid callIceCandidate control message");
              }
              break;
            case ForstaMessage.ControlTypes.CALL_LEAVE:
              String originator = data.getString("originator");
              String callId = data.getString("callId");
              forstaMessage.setCallLeave(callId, originator);
              Log.w(TAG, "Call leave received: " + callId + " From : " + originator);
              break;
            default:
              Log.w(TAG, "Not a control message");
          }
        }
      }

      if (!forstaMessage.isControlMessage()) {
        if (TextUtils.isEmpty(forstaMessage.getUniversalExpression())) {
          throw new InvalidMessagePayloadException("Content message. No universal expression.");
        }
      }

    } catch (JSONException e) {
      Log.e(TAG, jsonBody.toString());
      throw new InvalidMessagePayloadException(e.getMessage());
    }

    return forstaMessage;
  }

  public static void sendThreadUpdate(Context context, MasterSecret masterSecret, Recipients recipients, long threadId) {
    try {
      ForstaThread threadData = DatabaseFactory.getThreadDatabase(context).getForstaThread(threadId);
      String payload = createThreadUpdateMessage(context, recipients, threadData);
      OutgoingMessage message = new OutgoingMessage(recipients, payload, new LinkedList<Attachment>(), System.currentTimeMillis(), 0);
      MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
      long messageId  = database.insertMessageOutbox(new MasterSecretUnion(masterSecret), message, -1, false);
      MessageSender.sendMediaMessage(context, masterSecret, recipients, false, messageId, 0);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void sendCallAcceptOffer(Context context, MasterSecret masterSecret, Recipients recipients, String threadId, String callId, SessionDescription sdp, String peerId) {
    try {
      ForstaThread threadData = DatabaseFactory.getThreadDatabase(context).getForstaThread(threadId);
      String payload = createAcceptCallOfferMessage(context, recipients, threadData, callId, sdp.description, sdp.type.name(), peerId);
      OutgoingMessage message = new OutgoingMessage(recipients, payload, new LinkedList<Attachment>(), System.currentTimeMillis(), 0);
      MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
      long messageId  = database.insertMessageOutbox(new MasterSecretUnion(masterSecret), message, -1, false);
      MessageSender.sendMediaMessage(context, masterSecret, recipients, false, messageId, 0);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static String createAcceptCallOfferMessage(Context context, Recipients recipients, ForstaThread forstaThread, String callId, String description, String type, String peerId) {
    JSONObject data = new JSONObject();
    ForstaUser user = ForstaUser.getLocalForstaUser(context);
    try {
      data.put("control", "callAcceptOffer");
      data.put("peerId", peerId);
      JSONArray members = new JSONArray();
      for (Recipient x : recipients) {
        members.put(x.getAddress());
      }
      data.put("members", members);
      data.put("callId", callId);
      data.put("originator", user.getUid());
      JSONObject answer = new JSONObject();
      answer.put("sdp", description);
      answer.put("type", type);
      data.put("answer", answer);
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return createBaseMessageBody(user, recipients, forstaThread, ForstaMessage.MessageTypes.CONTROL, data);
  }

  private static String createThreadUpdateMessage(Context context, Recipients recipients, ForstaThread forstaThread) {
    JSONObject data = new JSONObject();
    try {
      data.put("control", "threadUpdate");
      JSONObject threadUpdates = new JSONObject();
      threadUpdates.put("threadTitle", forstaThread.getTitle());
      threadUpdates.put("threadId", forstaThread.getUid());
      data.put("threadUpdates", threadUpdates);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    ForstaUser user = ForstaUser.getLocalForstaUser(context);
    return createBaseMessageBody(user, recipients, forstaThread, ForstaMessage.MessageTypes.CONTROL, data);
  }

  // Standard version 1 body with no data section.
  private static String createBaseMessageBody(ForstaUser user, Recipients messageRecipients, ForstaThread forstaThread, String type, JSONObject data) {
    JSONArray versions = new JSONArray();
    JSONObject version1 = new JSONObject();
    String title = forstaThread.getTitle();
    try {
      String messageType = "content";
      if (type.equals(ForstaMessage.MessageTypes.CONTROL)) {
        messageType = "control";
      }

      String threadType = forstaThread.getThreadType() == 1 ? "announcement" : "conversation";
      JSONObject sender = new JSONObject();
      JSONObject recipients = new JSONObject();
      JSONArray userIds = new JSONArray();

      String threadId = !TextUtils.isEmpty(forstaThread.getUid()) ? forstaThread.getUid() : "";
      sender.put("tagId", user.tag_id);
      sender.put("tagPresentation", user.slug);
      sender.put("userId", user.uid);

      for (Recipient x : messageRecipients) {
        userIds.put(x.getAddress());
      }

      recipients.put("userIds", userIds);
      recipients.put("expression", forstaThread.getDistribution());
      version1.put("version", 1);
      version1.put("userAgent", System.getProperty("http.agent", ""));
      version1.put("messageId", UUID.randomUUID().toString());
      version1.put("messageType", messageType);
      version1.put("threadId", threadId);
      version1.put("threadTitle", title);
      version1.put("threadType", threadType);
      version1.put("sendTime", ForstaUtils.formatDateISOUTC(new Date()));
      version1.put("sender", sender);
      version1.put("distribution", recipients);
      version1.put("data", data);
      versions.put(version1);
    } catch (JSONException e) {
      Log.e(TAG, "createForstaMessageBody JSON exception");
      Log.e(TAG, "Thread: "+ forstaThread.getUid());
      Log.e(TAG, data.toString());
      e.printStackTrace();
    }
    return versions.toString();
  }

  public static String createControlMessageBody(Context context, Recipients recipients, List<Attachment> messageAttachments, ForstaThread forstaThread) {
    return createForstaMessageBody(context, "", recipients, messageAttachments, forstaThread, ForstaMessage.MessageTypes.CONTROL);
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
      JSONArray mentions = new JSONArray();
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
          String endcodedGroupId = messageRecipients.getPrimaryRecipient().getAddress();
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

      ForstaUser parsedUser;
      String tagRegex = "@[a-zA-Z0-9(-|.)]+";
      Pattern tagPattern = Pattern.compile(tagRegex);
      Matcher tagMatcher = tagPattern.matcher(richTextMessage);
      while (tagMatcher.find()) {
        String parsedTag = richTextMessage.substring(tagMatcher.start(), tagMatcher.end());
        parsedUser = contactDb.getUserByTag(parsedTag);
        if(parsedUser != null) {
          mentions.put(parsedUser.getUid());
        }
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
      if (mentions.length() > 0) {
        data.put("mentions", mentions );
      }
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
      Log.e(TAG, "Recipient: "+ messageRecipients.getPrimaryRecipient().getAddress());
      Log.e(TAG, "Thread: "+ forstaThread.getUid());
      e.printStackTrace();
      // Something failed. Return original message body
      return richTextMessage;
    }
    return versions.toString();
  }
}

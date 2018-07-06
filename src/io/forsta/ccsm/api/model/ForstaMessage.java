package io.forsta.ccsm.api.model;

import android.net.Uri;
import android.text.Spanned;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jlewis on 9/6/17.
 */

public class ForstaMessage {
  private static final String TAG = ForstaMessage.class.getSimpleName();
  private String textBody = "";
  private String htmlBody;
  private String messageId;
  private String senderId;
  private String universalExpression;
  private String threadUid;
  private String threadTitle;
  private String messageType = MessageTypes.CONTENT;
  private String controlType = ControlTypes.NONE;
  private String threadType = ThreadTypes.CONVERSATION;
  private List<ForstaAttachment> attachments = new ArrayList<>();
  private ForstaProvisionRequest provisionRequest;
  private Vote messageVote;
  private List<String> mentions = new ArrayList<>();
  private String messageRef;

  public static class ControlTypes {
    public static final String NONE = "none";
    public static final String THREAD_UPDATE = "threadUpdate";
    public static final String THREAD_CLEAR = "threadClear";
    public static final String THREAD_CLOSE = "threadClose";
    public static final String THREAD_DELETE = "threadDelete";
    public static final String SNOOZE = "snooze";
    public static final String PROVISION_REQUEST = "provisionRequest";
    public static final String SYNC_REQUEST = "syncRequest";
    public static final String SYNC_RESPONSE = "syncResponse";
    public static final String DISCOVER = "discover";
    public static final String DISCOVER_RESPONSE = "discoverResponse";
    public static final String UP_VOTE = "upVote";
  }

  public static class MessageTypes {
    public static final String CONTENT = "content";
    public static final String CONTROL = "control";
  }

  public static class ThreadTypes {
    public static final String CONVERSATION = "conversation";
    public static final String ANNOUNCEMENT = "announcement";
  }

  public class Vote {
    private String messageRef;
    private int vote;
    public Vote(String messageRef, int vote) {
      this.messageRef = messageRef;
      this.vote = vote;
    }

    public int getVote() {
      return vote;
    }

    public String getMessageRef() {
      return messageRef;
    }
  }

  public ForstaMessage() {

  }

  public boolean isControlMessage() {
    return messageType.equals(MessageTypes.CONTROL);
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

  public String getThreadUId() {
    return threadUid;
  }

  public String getSenderId() {
    return senderId;
  }

  public String getThreadTitle() {
    return threadTitle;
  }

  public String getTextBody() {
    return textBody;
  }

  public String getHtmlBody() {
    return htmlBody;
  }

  public boolean hasHtmlBody() {
    return htmlBody != null;
  }

  public String getMessageType() {
    return messageType;
  }

  public String getControlType() {
    return controlType;
  }

  public String getThreadType() {
    return threadType;
  }

  public List<String> getMentions() { return mentions; }

  public String getMessageRef() { return messageRef; }

  public String getGiphyUrl() {
    String html = getHtmlBody();
    if (!TextUtils.isEmpty(html)) {
      try {
        int startIndex = html.indexOf("src=");
        if (startIndex > 0) {
          startIndex += 5;
          String src = html.substring(startIndex);
          int endIndex = src.indexOf("\"");
          src = src.substring(0, endIndex);
          return src;
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return "";
  }

  public void setControlType(String controlType) {
    this.controlType = controlType;
  }

  public void setMessageType(String messageType) {
    this.messageType = messageType;
  }

  public void setThreadType(String threadType) {
    this.threadType = threadType;
  }

  public void setHtmlBody(String htmlBody) {
    this.htmlBody = htmlBody;
  }

  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }

  public void setTextBody(String textBody) {
    this.textBody = textBody;
  }

  public void setThreadTitle(String threadTitle) {
    this.threadTitle = threadTitle;
  }

  public void setThreadUid(String threadUid) {
    this.threadUid = threadUid;
  }

  public void setUniversalExpression(String universalExpression) {
    this.universalExpression = universalExpression;
  }

  public void setSenderId(String senderId) {
    this.senderId = senderId;
  }

  public List<ForstaAttachment> getAttachments() {
    return attachments;
  }

  public void addAttachment(String name, String type, long size) {
    attachments.add(new ForstaAttachment(name, type, size));
  }

  public void setProvisionRequest(String uuid, String key) {
    this.provisionRequest = new ForstaProvisionRequest(uuid, key);
  }

  public void setMessageVote(String messageRef, int vote) {
    messageVote = new Vote(messageRef, vote);
  }

  public void setMessageRef(String messageRef) {
    this.messageRef = messageRef;
  }

  public ForstaProvisionRequest getProvisionRequest() {
    return provisionRequest;
  }

  public Vote getMessageVote() {
    return messageVote;
  }

  public void addMention(String mention) { this.mentions.add(mention); }

  public class ForstaAttachment {
    private String name;
    private String type;
    private long size;

    public ForstaAttachment(String name, String type, long size) {
      this.name = name;
      this.type = type;
      this.size = size;
    }

    public String getName() {
      return name;
    }

    public String getType() {
      return type;
    }

    public long getSize() {
      return size;
    }
  }

  public class ForstaProvisionRequest {
    private String uuid;
    private String key;

    public ForstaProvisionRequest(String uuid, String key) {
      this.uuid = uuid;
      this.key = key;
    }

    public String getUuid() {
      return uuid;
    }

    public String getKey() {
      return key;
    }
  }
}

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
  private String messageType = MessageTypes.CONTENT;
  private String controlType = ControlTypes.NONE;
  private int threadType = 0;
  private List<ForstaAttachment> attachments = new ArrayList<>();

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
  }

  public static class MessageTypes {
    public static final String CONTENT = "content";
    public static final String CONTROL = "control";
  }

  public ForstaMessage() {

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

  public Spanned getHtmlBody() {
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

  public int getThreadType() {
    return threadType;
  }

  public void setControlType(String controlType) {
    this.controlType = controlType;
  }

  public void setMessageType(String messageType) {
    this.messageType = messageType;
  }

  public void setThreadType(int threadType) {
    this.threadType = threadType;
  }

  public void setHtmlBody(Spanned htmlBody) {
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
    ForstaAttachment attachment = new ForstaAttachment(name, type, size);
    attachments.add(attachment);
  }

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
}

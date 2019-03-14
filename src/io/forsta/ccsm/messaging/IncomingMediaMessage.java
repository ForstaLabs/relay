package io.forsta.ccsm.messaging;

import io.forsta.ccsm.api.model.ForstaMessage;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.attachments.PointerAttachment;
import io.forsta.securesms.crypto.MasterSecretUnion;
import io.forsta.securesms.database.MmsAddresses;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.GroupUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;

import java.util.LinkedList;
import java.util.List;

public class IncomingMediaMessage {

  private final String  from;
  private final String  body;
  private final String  groupId;
  private final boolean push;
  private final long    sentTimeMillis;
  private final int     subscriptionId;
  private final long    expiresIn;
  private final boolean expirationUpdate;
  private final boolean endSession = false;
  private final String messageRef;
  private final int voteCount;
  private final String messageId;

  private final List<String>     to          = new LinkedList<>();
  private final List<String>     cc          = new LinkedList<>();
  private final List<Attachment> attachments = new LinkedList<>();

  protected IncomingMediaMessage(String from, String to, String body, long sentTimeMillis, long expiresIn)
  {
    this.from             = from;
    this.sentTimeMillis   = sentTimeMillis;
    this.body             = body;
    this.groupId          = null;
    this.push             = true;
    this.subscriptionId   = -1;
    this.expiresIn        = expiresIn;
    this.expirationUpdate = false;
    this.messageRef = null;
    this.voteCount = 0;
    this.messageId = null;

    this.to.add(to);
  }

  public IncomingMediaMessage(MasterSecretUnion masterSecret,
                              String from,
                              String to,
                              long sentTimeMillis,
                              int subscriptionId,
                              long expiresIn,
                              boolean expirationUpdate,
                              Optional<String> relay,
                              Optional<String> body,
                              Optional<SignalServiceGroup> group,
                              Optional<List<SignalServiceAttachment>> attachments)
  {
    this(masterSecret, from, to, sentTimeMillis, subscriptionId, expiresIn, expirationUpdate, relay, body, group, attachments, null, 0, null);
  }

  public IncomingMediaMessage(MasterSecretUnion masterSecret,
                              String from,
                              String to,
                              long sentTimeMillis,
                              int subscriptionId,
                              long expiresIn,
                              boolean expirationUpdate,
                              Optional<String> relay,
                              Optional<String> body,
                              Optional<SignalServiceGroup> group,
                              Optional<List<SignalServiceAttachment>> attachments, String messageRef, int voteCount, String messageId)
  {
    this.push             = true;
    this.from             = from;
    this.sentTimeMillis   = sentTimeMillis;
    this.body             = body.orNull();
    this.subscriptionId   = subscriptionId;
    this.expiresIn        = expiresIn;
    this.expirationUpdate = expirationUpdate;
    this.messageRef = messageRef;
    this.voteCount = voteCount;
    this.messageId = messageId;

    if (group.isPresent()) this.groupId = GroupUtil.getEncodedId(group.get().getGroupId());
    else                   this.groupId = null;

    this.to.add(to);
    this.attachments.addAll(PointerAttachment.forPointers(masterSecret, attachments));
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public String getBody() {
    return body;
  }

  public MmsAddresses getAddresses() {
    return new MmsAddresses(from, to, cc, new LinkedList<String>());
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }

  public String getGroupId() {
    return groupId;
  }

  public boolean isPushMessage() {
    return push;
  }

  public boolean isExpirationUpdate() {
    return expirationUpdate;
  }

  public long getSentTimeMillis() {
    return sentTimeMillis;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public String getMessageRef() {
    return messageRef;
  }

  public String getMessageId() {
    return messageId;
  }

  public int getVoteCount() {
    return voteCount;
  }

  public boolean isGroupMessage() {
    return groupId != null || to.size() > 1 || cc.size() > 0;
  }
}

package io.forsta.securesms.mms;

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
  private final String messageRef;
  private final int voteCount;

  private final List<String>     to          = new LinkedList<>();
  private final List<String>     cc          = new LinkedList<>();
  private final List<Attachment> attachments = new LinkedList<>();

  public IncomingMediaMessage(String from, List<String> to, List<String> cc,
                              String body, long sentTimeMillis,
                              List<Attachment> attachments, int subscriptionId,
                              long expiresIn, boolean expirationUpdate)
  {
    this.from             = from;
    this.sentTimeMillis   = sentTimeMillis;
    this.body             = body;
    this.groupId          = null;
    this.push             = false;
    this.subscriptionId   = subscriptionId;
    this.expiresIn        = expiresIn;
    this.expirationUpdate = expirationUpdate;
    this.messageRef = null;
    this.voteCount = 0;

    this.to.addAll(to);
    this.cc.addAll(cc);
    this.attachments.addAll(attachments);
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
    this(masterSecret, from, to, sentTimeMillis, subscriptionId, expiresIn, expirationUpdate, relay, body, group, attachments, null, 0);
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
                              Optional<List<SignalServiceAttachment>> attachments, String messageRef, int voteCount)
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

  public boolean isGroupMessage() {
    return groupId != null || to.size() > 1 || cc.size() > 0;
  }
}

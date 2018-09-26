/**
 * Copyright (C) 2012 Moxie Marlinpsike
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.forsta.securesms.database.model;

import android.content.Context;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;

import org.w3c.dom.Text;

import io.forsta.ccsm.api.model.ForstaMessage;
import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.ccsm.messaging.ForstaMessageManager;
import io.forsta.ccsm.util.InvalidMessagePayloadException;
import io.forsta.securesms.R;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.MmsSmsColumns;
import io.forsta.securesms.database.SmsDatabase;
import io.forsta.securesms.database.documents.NetworkFailure;
import io.forsta.securesms.database.documents.IdentityKeyMismatch;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.ExpirationUtil;
import io.forsta.securesms.util.GroupUtil;
import io.forsta.securesms.util.TextSecurePreferences;

import java.util.List;
import java.util.Set;

import static android.R.attr.filter;

/**
 * The base class for message record models that are displayed in
 * conversations, as opposed to models that are displayed in a thread list.
 * Encapsulates the shared data between both SMS and MMS messages.
 *
 * @author Moxie Marlinspike
 *
 */
public abstract class MessageRecord extends DisplayRecord {

  private final String TAG = MessageRecord.class.getSimpleName();

  private static final int MAX_DISPLAY_LENGTH = 2000;

  private final Recipient individualRecipient;
  private final int                       recipientDeviceId;
  private final long                      id;
  private final List<IdentityKeyMismatch> mismatches;
  private final List<NetworkFailure>      networkFailures;
  private final int                       subscriptionId;
  private final long                      expiresIn;
  private final long                      expireStarted;
  private final String messageRef;
  private final int voteCount;
  private final String messageId;

  MessageRecord(Context context, long id, Body body, Recipients recipients,
                Recipient individualRecipient, int recipientDeviceId,
                long dateSent, long dateReceived, long threadId,
                int deliveryStatus, int receiptCount, long type,
                List<IdentityKeyMismatch> mismatches,
                List<NetworkFailure> networkFailures,
                int subscriptionId, long expiresIn, long expireStarted, String messageRef, int voteCount, String messageId)
  {
    super(context, body, recipients, dateSent, dateReceived, threadId, deliveryStatus, receiptCount,
          type);
    this.id                  = id;
    this.individualRecipient = individualRecipient;
    this.recipientDeviceId   = recipientDeviceId;
    this.mismatches          = mismatches;
    this.networkFailures     = networkFailures;
    this.subscriptionId      = subscriptionId;
    this.expiresIn           = expiresIn;
    this.expireStarted       = expireStarted;
    this.messageRef = messageRef;
    this.voteCount = voteCount;
    this.messageId = messageId;
  }

  public abstract boolean isMms();
  public abstract boolean isMmsNotification();

  public boolean isSecure() {
    return MmsSmsColumns.Types.isSecureType(type);
  }

  public boolean isLegacyMessage() {
    return MmsSmsColumns.Types.isLegacyType(type);
  }

  public boolean isAsymmetricEncryption() {
    return MmsSmsColumns.Types.isAsymmetricEncryption(type);
  }

  public boolean isMentioned(Context context) {
    try {
      ForstaUser user = ForstaUser.getLocalForstaUser(context);
      ForstaMessage forstaBody = getForstaMessageBody();
      if (forstaBody.getMentions().contains(user.getUid())) {
        return true;
      }
    } catch (InvalidMessagePayloadException e) {
      e.printStackTrace();
    }
    return false;
  }

  public boolean isNamed(Context context) {
    try {
      ForstaUser user = ForstaUser.getLocalForstaUser(context);
      ForstaMessage forstaBody = getForstaMessageBody();
      String plainTextBody = forstaBody.getTextBody().toLowerCase();
      String name = user.getName();
      if (name != null) {
        String[] parts = name.split(" ");
        for (String part : parts) {
          if (plainTextBody.contains(part.toLowerCase())) {
            return true;
          }
        }
      }
    } catch (InvalidMessagePayloadException e) {
      e.printStackTrace();
    }
    return false;
  }

  public String getPlainTextBody() {
    try {
      ForstaMessage forstaBody = getForstaMessageBody();

      if(forstaBody.getVote() > 0) {
        return "Up Vote";
      }
      return forstaBody.getTextBody();
    } catch (InvalidMessagePayloadException e) {
      e.printStackTrace();
    }
    return "Invalid message body";
  }

  public Spanned getHtmlBody() {
    try {
      ForstaMessage forstaBody = getForstaMessageBody();
      if (!TextUtils.isEmpty(forstaBody.getHtmlBody())) {
        return Html.fromHtml(forstaBody.getHtmlBody());
      }
    } catch (InvalidMessagePayloadException e) {
      e.printStackTrace();
    }
    return null;
  }

  public String getGiphy() {
    try {
      ForstaMessage forstaBody = getForstaMessageBody();
      return forstaBody.getGiphyUrl();
    } catch (InvalidMessagePayloadException e) {
      e.printStackTrace();
    }
    return "";
  }

  public String getMessagePayloadBody() {
    return getBody().getBody();
  }

  public String getMessageId() {
    if (TextUtils.isEmpty(messageId)) {
      return forstaMessageBody.getMessageId();
    }
    return messageId;
  }

  public String getMessageRef() {
    return messageRef;
  }

  public int getVoteCount() {
    return voteCount;
  }

  @Override
  public SpannableString getDisplayBody() {
    String body = getBody().getBody();
    try {
      ForstaMessage forstaBody = getForstaMessageBody();
      body = !TextUtils.isEmpty(forstaBody.getHtmlBody()) ? forstaBody.getHtmlBody() : forstaBody.getTextBody();
    } catch (InvalidMessagePayloadException e) {
      e.printStackTrace();
    }
    if (isExpirationTimerUpdate()) {
      String sender = isOutgoing() ? context.getString(R.string.MessageRecord_you) : getIndividualRecipient().toShortString();
      String time   = ExpirationUtil.getExpirationDisplayValue(context, (int) (getExpiresIn() / 1000));
      return emphasisAdded(context.getString(R.string.MessageRecord_s_set_disappearing_message_time_to_s, sender, time));
    }
    return new SpannableString(body);
  }

  public long getId() {
    return id;
  }

  public boolean isPush() {
    return SmsDatabase.Types.isPushType(type) && !SmsDatabase.Types.isForcedSms(type);
  }

  public long getTimestamp() {
    if (isPush() && getDateSent() < getDateReceived()) {
      return getDateSent();
    }
    return getDateReceived();
  }

  public boolean isForcedSms() {
    return SmsDatabase.Types.isForcedSms(type);
  }

  public boolean isStaleKeyExchange() {
    return SmsDatabase.Types.isStaleKeyExchange(type);
  }

  public boolean isProcessedKeyExchange() {
    return SmsDatabase.Types.isProcessedKeyExchange(type);
  }

  public boolean isIdentityMismatchFailure() {
    return mismatches != null && !mismatches.isEmpty();
  }

  public boolean isBundleKeyExchange() {
    return SmsDatabase.Types.isBundleKeyExchange(type);
  }

  public boolean isIdentityUpdate() {
    return SmsDatabase.Types.isIdentityUpdate(type);
  }

  public boolean isCorruptedKeyExchange() {
    return SmsDatabase.Types.isCorruptedKeyExchange(type);
  }

  public boolean isInvalidVersionKeyExchange() {
    return SmsDatabase.Types.isInvalidVersionKeyExchange(type);
  }

  public boolean isMediaPending() {
    return false;
  }

  public Recipient getIndividualRecipient() {
    return individualRecipient;
  }

  public int getRecipientDeviceId() {
    return recipientDeviceId;
  }

  public long getType() {
    return type;
  }

  public List<IdentityKeyMismatch> getIdentityKeyMismatches() {
    return mismatches;
  }

  public List<NetworkFailure> getNetworkFailures() {
    return networkFailures;
  }

  public boolean hasNetworkFailures() {
    return networkFailures != null && !networkFailures.isEmpty();
  }

  protected SpannableString emphasisAdded(String sequence) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new RelativeSizeSpan(0.9f), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

    return spannable;
  }

  public boolean equals(Object other) {
    return other != null                              &&
           other instanceof MessageRecord             &&
           ((MessageRecord) other).getId() == getId() &&
           ((MessageRecord) other).isMms() == isMms();
  }

  public int hashCode() {
    return (int)getId();
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public long getExpireStarted() {
    return expireStarted;
  }
}

/**
 * Copyright (C) 2011 Whisper Systems
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
package io.forsta.securesms;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import io.forsta.ccsm.util.ForstaUtils;
import io.forsta.securesms.components.AlertView;
import io.forsta.securesms.components.AudioView;
import io.forsta.securesms.components.AvatarImageView;
import io.forsta.securesms.components.DeliveryStatusView;
import io.forsta.securesms.components.ExpirationTimerView;
import io.forsta.securesms.components.ThumbnailView;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.AttachmentDatabase;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.IdentityDatabase;
import io.forsta.securesms.database.MmsAddressDatabase;
import io.forsta.securesms.database.MmsDatabase;
import io.forsta.securesms.database.MmsSmsDatabase;
import io.forsta.securesms.database.PushDatabase;
import io.forsta.securesms.database.SmsDatabase;
import io.forsta.securesms.database.documents.IdentityKeyMismatch;
import io.forsta.securesms.database.model.MediaMmsMessageRecord;
import io.forsta.securesms.database.model.MessageRecord;
import io.forsta.securesms.database.model.NotificationMmsMessageRecord;
import io.forsta.securesms.jobs.IdentityUpdateJob;
import io.forsta.securesms.jobs.MmsDownloadJob;
import io.forsta.securesms.jobs.MmsSendJob;
import io.forsta.securesms.jobs.PushDecryptJob;
import io.forsta.securesms.jobs.SmsSendJob;
import io.forsta.securesms.mms.PartAuthority;
import io.forsta.securesms.mms.Slide;
import io.forsta.securesms.mms.SlideClickListener;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.service.ExpiringMessageManager;
import io.forsta.securesms.sms.MessageSender;
import io.forsta.securesms.util.Base64;
import io.forsta.securesms.util.DateUtils;
import io.forsta.securesms.util.DynamicTheme;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.Util;
import io.forsta.securesms.util.dualsim.SubscriptionInfoCompat;
import io.forsta.securesms.util.dualsim.SubscriptionManagerCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A view that displays an individual conversation item within a conversation
 * thread.  Used by ComposeMessageActivity's ListActivity via a ConversationAdapter.
 *
 * @author Moxie Marlinspike
 *
 */

public class ConversationItem extends LinearLayout
    implements Recipient.RecipientModifiedListener, Recipients.RecipientsModifiedListener, BindableConversationItem
{
  private final static String TAG = ConversationItem.class.getSimpleName();

  private MessageRecord messageRecord;
  private MasterSecret masterSecret;
  private Locale        locale;
  private boolean       groupThread;
  private Recipient     recipient;

  private View               bodyBubble;
  private TextView           bodyText;
  private TextView           dateText;
  private TextView           simInfoText;
  private TextView           indicatorText;
  private TextView           groupStatusText;
  private ImageView          secureImage;
  private AvatarImageView contactPhoto;
  private DeliveryStatusView deliveryStatusIndicator;
  private AlertView alertView;

  private @NonNull  Set<MessageRecord>  batchSelected = new HashSet<>();
  private @Nullable Recipients          conversationRecipients;
  private @NonNull  ThumbnailView       mediaThumbnail;
  private @NonNull  AudioView           audioView;
  private @NonNull  Button              mmsDownloadButton;
  private @NonNull  TextView            mmsDownloadingLabel;
  private @NonNull
  ExpirationTimerView expirationTimer;

  private int defaultBubbleColor;

  private final MmsDownloadClickListener    mmsDownloadClickListener    = new MmsDownloadClickListener();
  private final MmsPreferencesClickListener mmsPreferencesClickListener = new MmsPreferencesClickListener();
  private final Context                     context;

  public ConversationItem(Context context) {
    this(context, null);
  }

  public ConversationItem(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
  }

  @Override
  public void setOnClickListener(OnClickListener l) {
    super.setOnClickListener(new ClickListener(l));
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    initializeAttributes();

    this.bodyText                = (TextView)           findViewById(R.id.conversation_item_body);
    this.dateText                = (TextView)           findViewById(R.id.conversation_item_date);
    this.simInfoText             = (TextView)           findViewById(R.id.sim_info);
    this.indicatorText           = (TextView)           findViewById(R.id.indicator_text);
    this.groupStatusText         = (TextView)           findViewById(R.id.group_message_status);
    this.secureImage             = (ImageView)          findViewById(R.id.secure_indicator);
    this.deliveryStatusIndicator = (DeliveryStatusView) findViewById(R.id.delivery_status);
    this.alertView               = (AlertView)          findViewById(R.id.indicators_parent);
    this.mmsDownloadButton       = (Button)             findViewById(R.id.mms_download_button);
    this.mmsDownloadingLabel     = (TextView)           findViewById(R.id.mms_label_downloading);
    this.contactPhoto            = (AvatarImageView)    findViewById(R.id.contact_photo);
    this.bodyBubble              =                      findViewById(R.id.body_bubble);
    this.mediaThumbnail          = (ThumbnailView)      findViewById(R.id.image_view);
    this.audioView               = (AudioView)          findViewById(R.id.audio_view);
    this.expirationTimer         = (ExpirationTimerView) findViewById(R.id.expiration_indicator);

    setOnClickListener(new ClickListener(null));
    PassthroughClickListener        passthroughClickListener = new PassthroughClickListener();
    AttachmentDownloadClickListener downloadClickListener    = new AttachmentDownloadClickListener();

    mmsDownloadButton.setOnClickListener(mmsDownloadClickListener);
    mediaThumbnail.setThumbnailClickListener(new ThumbnailClickListener());
    mediaThumbnail.setDownloadClickListener(downloadClickListener);
    mediaThumbnail.setOnLongClickListener(passthroughClickListener);
    mediaThumbnail.setOnClickListener(passthroughClickListener);
    audioView.setDownloadClickListener(downloadClickListener);
    audioView.setOnLongClickListener(passthroughClickListener);
    bodyText.setOnLongClickListener(passthroughClickListener);
    bodyText.setOnClickListener(passthroughClickListener);
  }

  @Override
  public void bind(@NonNull MasterSecret       masterSecret,
                   @NonNull MessageRecord      messageRecord,
                   @NonNull Locale             locale,
                   @NonNull Set<MessageRecord> batchSelected,
                   @NonNull Recipients         conversationRecipients)
  {
    this.masterSecret           = masterSecret;
    this.messageRecord          = messageRecord;
    this.locale                 = locale;
    this.batchSelected          = batchSelected;
    this.conversationRecipients = conversationRecipients;
    this.groupThread            = !conversationRecipients.isSingleRecipient() || conversationRecipients.isGroupRecipient();
    this.recipient              = messageRecord.getIndividualRecipient();

    this.recipient.addListener(this);
    this.conversationRecipients.addListener(this);

    setInteractionState(messageRecord);
    setBodyText(messageRecord);
    setBubbleState(messageRecord, recipient);
    setStatusIcons(messageRecord);
    setContactPhoto(recipient);
    setGroupMessageStatus(messageRecord, recipient);
    setMinimumWidth();
    setMediaAttributes(messageRecord);
    setSimInfo(messageRecord);
    setExpiration(messageRecord);
  }

  private void initializeAttributes() {
    final int[]      attributes = new int[] {R.attr.conversation_item_bubble_background,
                                             R.attr.conversation_list_item_background_selected,
                                             R.attr.conversation_item_background};
    final TypedArray attrs      = context.obtainStyledAttributes(attributes);

    defaultBubbleColor = attrs.getColor(0, Color.WHITE);
    attrs.recycle();
  }

  @Override
  public void unbind() {
    if (recipient != null) {
      recipient.removeListener(this);
    }

    this.expirationTimer.stopAnimation();
  }

  public MessageRecord getMessageRecord() {
    return messageRecord;
  }

  /// MessageRecord Attribute Parsers

  private void setBubbleState(MessageRecord messageRecord, Recipient recipient) {
    if (messageRecord.isOutgoing()) {
      bodyBubble.getBackground().setColorFilter(defaultBubbleColor, PorterDuff.Mode.MULTIPLY);
      mediaThumbnail.setBackgroundColorHint(defaultBubbleColor);
      setAudioViewTint(messageRecord, conversationRecipients);
    } else {
      int color = recipient.getColor().toConversationColor(context);
      bodyBubble.getBackground().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
      mediaThumbnail.setBackgroundColorHint(color);
    }
  }

  private void setAudioViewTint(MessageRecord messageRecord, Recipients recipients) {
    if (messageRecord.isOutgoing()) {
      if (DynamicTheme.LIGHT.equals(TextSecurePreferences.getTheme(context))) {
        audioView.setTint(recipients.getColor().toConversationColor(context));
      } else {
        audioView.setTint(Color.WHITE);
      }
    }
  }

  private void setInteractionState(MessageRecord messageRecord) {
    setSelected(batchSelected.contains(messageRecord));
    mediaThumbnail.setFocusable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
    mediaThumbnail.setClickable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
    mediaThumbnail.setLongClickable(batchSelected.isEmpty());
    bodyText.setAutoLinkMask(batchSelected.isEmpty() ? Linkify.ALL : 0);
  }

  private boolean isCaptionlessMms(MessageRecord messageRecord) {
    return TextUtils.isEmpty(messageRecord.getDisplayBody()) && messageRecord.isMms();
  }

  private boolean hasAudio(MessageRecord messageRecord) {
    return messageRecord.isMms() &&
           !messageRecord.isMmsNotification() &&
           ((MediaMmsMessageRecord)messageRecord).getSlideDeck().getAudioSlide() != null;
  }

  private boolean hasThumbnail(MessageRecord messageRecord) {
    return messageRecord.isMms()              &&
           !messageRecord.isMmsNotification() &&
           ((MediaMmsMessageRecord)messageRecord).getSlideDeck().getThumbnailSlide() != null;
  }

  private void setBodyText(MessageRecord messageRecord) {
    bodyText.setClickable(false);
    bodyText.setFocusable(false);

    if (isCaptionlessMms(messageRecord)) {
      bodyText.setVisibility(View.GONE);
    } else {
      Spanned forstaBody = ForstaUtils.getForstaJsonBody(messageRecord.getDisplayBody().toString());
      if (forstaBody != null) {
        bodyText.setText(forstaBody);
      } else {
        String forstaPlainBody = ForstaUtils.getForstaPlainTextBody(messageRecord.getDisplayBody().toString());
        if (forstaPlainBody != null) {
          bodyText.setText(forstaPlainBody);
        } else {
          bodyText.setText(messageRecord.getDisplayBody());
        }
      }
      bodyText.setVisibility(View.VISIBLE);
    }
  }

  private void setMediaAttributes(MessageRecord messageRecord) {
    boolean showControls = !messageRecord.isFailed() && (!messageRecord.isOutgoing() || messageRecord.isPending());

    if (messageRecord.isMmsNotification()) {
      mediaThumbnail.setVisibility(View.GONE);
      audioView.setVisibility(View.GONE);

      bodyText.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
      setNotificationMmsAttributes((NotificationMmsMessageRecord) messageRecord);
    } else if (hasAudio(messageRecord)) {
      audioView.setVisibility(View.VISIBLE);
      mediaThumbnail.setVisibility(View.GONE);

      //noinspection ConstantConditions
      audioView.setAudio(masterSecret, ((MediaMmsMessageRecord) messageRecord).getSlideDeck().getAudioSlide(), showControls);
      bodyText.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    } else if (hasThumbnail(messageRecord)) {
      mediaThumbnail.setVisibility(View.VISIBLE);
      audioView.setVisibility(View.GONE);

      //noinspection ConstantConditions
      mediaThumbnail.setImageResource(masterSecret,
                                      ((MediaMmsMessageRecord)messageRecord).getSlideDeck().getThumbnailSlide(),
                                      showControls);
      bodyText.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    } else {
      mediaThumbnail.setVisibility(View.GONE);
      audioView.setVisibility(View.GONE);
      bodyText.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
  }

  private void setContactPhoto(Recipient recipient) {
    if (! messageRecord.isOutgoing()) {
      setContactPhotoForRecipient(recipient);
    }
  }

  private void setStatusIcons(MessageRecord messageRecord) {
    mmsDownloadButton.setVisibility(View.GONE);
    mmsDownloadingLabel.setVisibility(View.GONE);
    indicatorText.setVisibility(View.GONE);

    secureImage.setVisibility(messageRecord.isSecure() ? View.VISIBLE : View.GONE);
    bodyText.setCompoundDrawablesWithIntrinsicBounds(0, 0, messageRecord.isKeyExchange() ? R.drawable.ic_menu_login : 0, 0);
    dateText.setText(DateUtils.getExtendedRelativeTimeSpanString(getContext(), locale, messageRecord.getTimestamp()));

    if (messageRecord.isFailed()) {
      setFailedStatusIcons();
    } else if (messageRecord.isPendingInsecureSmsFallback()) {
      setFallbackStatusIcons();
    } else {
      alertView.setNone();

      if      (!messageRecord.isOutgoing()) deliveryStatusIndicator.setNone();
      else if (messageRecord.isPending())   deliveryStatusIndicator.setPending();
      else if (messageRecord.isDelivered()) deliveryStatusIndicator.setDelivered();
      else                                  deliveryStatusIndicator.setSent();
    }
  }

  private void setSimInfo(MessageRecord messageRecord) {
    SubscriptionManagerCompat subscriptionManager = new SubscriptionManagerCompat(context);

    if (messageRecord.getSubscriptionId() == -1 || subscriptionManager.getActiveSubscriptionInfoList().size() < 2) {
      simInfoText.setVisibility(View.GONE);
    } else {
      Optional<SubscriptionInfoCompat> subscriptionInfo = subscriptionManager.getActiveSubscriptionInfo(messageRecord.getSubscriptionId());

      if (subscriptionInfo.isPresent() && messageRecord.isOutgoing()) {
        simInfoText.setText(getContext().getString(R.string.ConversationItem_from_s, subscriptionInfo.get().getDisplayName()));
        simInfoText.setVisibility(View.VISIBLE);
      } else if (subscriptionInfo.isPresent()) {
        simInfoText.setText(getContext().getString(R.string.ConversationItem_to_s,  subscriptionInfo.get().getDisplayName()));
        simInfoText.setVisibility(View.VISIBLE);
      } else {
        simInfoText.setVisibility(View.GONE);
      }
    }
  }

  private void setExpiration(final MessageRecord messageRecord) {
    if (messageRecord.getExpiresIn() > 0) {
      this.expirationTimer.setVisibility(View.VISIBLE);
      this.expirationTimer.setPercentage(0);

      if (messageRecord.getExpireStarted() > 0) {
        this.expirationTimer.setExpirationTime(messageRecord.getExpireStarted(),
                                               messageRecord.getExpiresIn());
        this.expirationTimer.startAnimation();
      } else if (!messageRecord.isOutgoing() && !messageRecord.isMediaPending()) {
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            ExpiringMessageManager expirationManager = ApplicationContext.getInstance(context).getExpiringMessageManager();
            long                   id                = messageRecord.getId();
            boolean                mms               = messageRecord.isMms();

            if (mms) DatabaseFactory.getMmsDatabase(context).markExpireStarted(id);
            else     DatabaseFactory.getSmsDatabase(context).markExpireStarted(id);

            expirationManager.scheduleDeletion(id, mms, messageRecord.getExpiresIn());
            return null;
          }
        }.execute();
      }
    } else {
      this.expirationTimer.setVisibility(View.GONE);
    }
  }

  private void setFailedStatusIcons() {
    alertView.setFailed();
    deliveryStatusIndicator.setNone();
    dateText.setText(R.string.ConversationItem_error_not_delivered);

    if (messageRecord.isOutgoing()) {
      indicatorText.setText(R.string.ConversationItem_click_for_details);
      indicatorText.setVisibility(View.VISIBLE);
    }
  }

  private void setFallbackStatusIcons() {
    alertView.setPendingApproval();
    deliveryStatusIndicator.setNone();
    indicatorText.setVisibility(View.VISIBLE);
    indicatorText.setText(R.string.ConversationItem_click_to_approve_unencrypted);
  }

  private void setMinimumWidth() {
    if (indicatorText.getVisibility() == View.VISIBLE && indicatorText.getText() != null) {
      final float density = getResources().getDisplayMetrics().density;
      bodyBubble.setMinimumWidth(indicatorText.getText().length() * (int) (6.5 * density) + (int) (22.0 * density));
    } else {
      bodyBubble.setMinimumWidth(0);
    }
  }

  private boolean shouldInterceptClicks(MessageRecord messageRecord) {
    return batchSelected.isEmpty() &&
            ((messageRecord.isFailed() && !messageRecord.isMmsNotification()) ||
            messageRecord.isPendingInsecureSmsFallback() ||
            messageRecord.isBundleKeyExchange());
  }

  private void setGroupMessageStatus(MessageRecord messageRecord, Recipient recipient) {
    if (groupThread && !messageRecord.isOutgoing()) {
      this.groupStatusText.setText(recipient.toShortString());
      this.groupStatusText.setVisibility(View.VISIBLE);
    } else {
      this.groupStatusText.setVisibility(View.GONE);
    }
  }

  private void setNotificationMmsAttributes(NotificationMmsMessageRecord messageRecord) {
    String messageSize = String.format(context.getString(R.string.ConversationItem_message_size_d_kb),
                                       messageRecord.getMessageSize());
    String expires     = String.format(context.getString(R.string.ConversationItem_expires_s),
                                       DateUtils.getRelativeTimeSpanString(getContext(),
                                                                           messageRecord.getExpiration(),
                                                                           false));

    dateText.setText(messageSize + "\n" + expires);

    if (MmsDatabase.Status.isDisplayDownloadButton(messageRecord.getStatus())) {
      mmsDownloadButton.setVisibility(View.VISIBLE);
      mmsDownloadingLabel.setVisibility(View.GONE);
    } else {
      mmsDownloadingLabel.setText(MmsDatabase.Status.getLabelForStatus(context, messageRecord.getStatus()));
      mmsDownloadButton.setVisibility(View.GONE);
      mmsDownloadingLabel.setVisibility(View.VISIBLE);

      if (MmsDatabase.Status.isHardError(messageRecord.getStatus()) && !messageRecord.isOutgoing())
        setOnClickListener(mmsDownloadClickListener);
      else if (MmsDatabase.Status.DOWNLOAD_APN_UNAVAILABLE == messageRecord.getStatus() && !messageRecord.isOutgoing())
        setOnClickListener(mmsPreferencesClickListener);
    }
  }

  /// Helper Methods

  private void setContactPhotoForRecipient(final Recipient recipient) {
    if (contactPhoto == null) return;

    contactPhoto.setAvatar(recipient, true);
    contactPhoto.setVisibility(View.VISIBLE);
  }

  /// Event handlers

  private void handleApproveIdentity() {
    List<IdentityKeyMismatch> mismatches = messageRecord.getIdentityKeyMismatches();

    if (mismatches.size() != 1) {
      throw new AssertionError("Identity mismatch count: " + mismatches.size());
    }

    new AcceptIdentityMismatch(getContext(), masterSecret, messageRecord, mismatches.get(0)).execute();
//    new ConfirmIdentityDialog(context, masterSecret, messageRecord, mismatches.get(0)).show();
  }

  @Override
  public void onModified(final Recipient recipient) {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        setBubbleState(messageRecord, recipient);
        setContactPhoto(recipient);
        setGroupMessageStatus(messageRecord, recipient);
      }
    });
  }

  @Override
  public void onModified(final Recipients recipients) {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        setAudioViewTint(messageRecord, recipients);
      }
    });
  }

  private class AttachmentDownloadClickListener implements SlideClickListener {
    @Override public void onClick(View v, final Slide slide) {
      DatabaseFactory.getAttachmentDatabase(context).setTransferState(messageRecord.getId(),
                                                                      slide.asAttachment(),
                                                                      AttachmentDatabase.TRANSFER_PROGRESS_STARTED);
    }
  }

  private class ThumbnailClickListener implements SlideClickListener {
    private void fireIntent(Slide slide) {
      Log.w(TAG, "Clicked: " + slide.getUri() + " , " + slide.getContentType());
      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      intent.setDataAndType(PartAuthority.getAttachmentPublicUri(slide.getUri()), slide.getContentType());
      try {
        context.startActivity(intent);
      } catch (ActivityNotFoundException anfe) {
        Log.w(TAG, "No activity existed to view the media.");
        Toast.makeText(context, R.string.ConversationItem_unable_to_open_media, Toast.LENGTH_LONG).show();
      }
    }

    public void onClick(final View v, final Slide slide) {
      if (shouldInterceptClicks(messageRecord) || !batchSelected.isEmpty()) {
        performClick();
      } else if (MediaPreviewActivity.isContentTypeSupported(slide.getContentType()) &&
                 slide.getThumbnailUri() != null)
      {
        Intent intent = new Intent(context, MediaPreviewActivity.class);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(slide.getUri(), slide.getContentType());
        if (!messageRecord.isOutgoing()) intent.putExtra(MediaPreviewActivity.RECIPIENT_EXTRA, recipient.getRecipientId());
        intent.putExtra(MediaPreviewActivity.DATE_EXTRA, messageRecord.getTimestamp());

        context.startActivity(intent);
      } else {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.ConversationItem_view_secure_media_question);
        builder.setIconAttribute(R.attr.dialog_alert_icon);
        builder.setCancelable(true);
        builder.setMessage(R.string.ConversationItem_this_media_has_been_stored_in_an_encrypted_database_external_viewer_warning);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int which) {
            fireIntent(slide);
          }
        });
        builder.setNegativeButton(R.string.no, null);
        builder.show();
      }
    }
  }

  private class MmsDownloadClickListener implements View.OnClickListener {
    public void onClick(View v) {
      NotificationMmsMessageRecord notificationRecord = (NotificationMmsMessageRecord)messageRecord;
      Log.w(TAG, "Content location: " + new String(notificationRecord.getContentLocation()));
      mmsDownloadButton.setVisibility(View.GONE);
      mmsDownloadingLabel.setVisibility(View.VISIBLE);

      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MmsDownloadJob(context, messageRecord.getId(),
                                                messageRecord.getThreadId(), false));
    }
  }

  private class MmsPreferencesClickListener implements View.OnClickListener {
    public void onClick(View v) {
      Intent intent = new Intent(context, PromptMmsActivity.class);
      intent.putExtra("message_id", messageRecord.getId());
      intent.putExtra("thread_id", messageRecord.getThreadId());
      intent.putExtra("automatic", true);
      context.startActivity(intent);
    }
  }

  private class PassthroughClickListener implements View.OnLongClickListener, View.OnClickListener {

    @Override
    public boolean onLongClick(View v) {
      performLongClick();
      return true;
    }

    @Override
    public void onClick(View v) {
      performClick();
    }
  }
  private class ClickListener implements View.OnClickListener {
    private OnClickListener parent;

    public ClickListener(@Nullable OnClickListener parent) {
      this.parent = parent;
    }

    public void onClick(View v) {
      if (!shouldInterceptClicks(messageRecord) && parent != null) {
        parent.onClick(v);
      } else if (messageRecord.isFailed()) {
        Intent intent = new Intent(context, MessageDetailsActivity.class);
        intent.putExtra(MessageDetailsActivity.MASTER_SECRET_EXTRA, masterSecret);
        intent.putExtra(MessageDetailsActivity.MESSAGE_ID_EXTRA, messageRecord.getId());
        intent.putExtra(MessageDetailsActivity.THREAD_ID_EXTRA, messageRecord.getThreadId());
        intent.putExtra(MessageDetailsActivity.TYPE_EXTRA, messageRecord.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
        intent.putExtra(MessageDetailsActivity.IS_PUSH_GROUP_EXTRA, groupThread && messageRecord.isPush());
        intent.putExtra(MessageDetailsActivity.RECIPIENTS_IDS_EXTRA, conversationRecipients.getIds());
        context.startActivity(intent);
      } else if (!messageRecord.isOutgoing() && messageRecord.isIdentityMismatchFailure()) {
        handleApproveIdentity();
      } else if (messageRecord.isPendingInsecureSmsFallback()) {
        handleMessageApproval();
      }
    }
  }

  private void handleMessageApproval() {
    final int title;
    final int message;

    if (messageRecord.isMms()) title = R.string.ConversationItem_click_to_approve_unencrypted_mms_dialog_title;
    else                       title = R.string.ConversationItem_click_to_approve_unencrypted_sms_dialog_title;

    message = R.string.ConversationItem_click_to_approve_unencrypted_dialog_message;

    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(title);

    if (message > -1) builder.setMessage(message);

    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialogInterface, int i) {
        if (messageRecord.isMms()) {
          MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
          database.markAsInsecure(messageRecord.getId());
          database.markAsOutbox(messageRecord.getId());
          database.markAsForcedSms(messageRecord.getId());

          ApplicationContext.getInstance(context)
                            .getJobManager()
                            .add(new MmsSendJob(context, messageRecord.getId()));
        } else {
          SmsDatabase database = DatabaseFactory.getSmsDatabase(context);
          database.markAsInsecure(messageRecord.getId());
          database.markAsOutbox(messageRecord.getId());
          database.markAsForcedSms(messageRecord.getId());

          ApplicationContext.getInstance(context)
                            .getJobManager()
                            .add(new SmsSendJob(context, messageRecord.getId(),
                                                messageRecord.getIndividualRecipient().getNumber()));
        }
      }
    });

    builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialogInterface, int i) {
        if (messageRecord.isMms()) {
          DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageRecord.getId());
        } else {
          DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageRecord.getId());
        }
      }
    });
    builder.show();
  }

}

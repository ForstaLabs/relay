package io.forsta.securesms;

import android.content.Context;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.forsta.ccsm.database.model.ForstaThread;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.ViewUtil;

public class ConversationTitleView extends LinearLayout {

  private static final String TAG = ConversationTitleView.class.getSimpleName();

  private TextView  title;
  private TextView  subtitle;
  private ImageView announcement;

  public ConversationTitleView(Context context) {
    this(context, null);
  }

  public ConversationTitleView(Context context, AttributeSet attrs) {
    super(context, attrs);

  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    this.title    = (TextView) findViewById(R.id.title);
    this.subtitle = (TextView) findViewById(R.id.subtitle);
    this.announcement = (ImageView) findViewById(R.id.conversation_title_announcement_indicator);

    ViewUtil.setTextViewGravityStart(this.title, getContext());
    ViewUtil.setTextViewGravityStart(this.subtitle, getContext());
  }

  public void setTitle(@Nullable Recipients recipients, ForstaThread thread) {
    if      (recipients == null)             setComposeTitle();
    else                                     setRecipientsTitle(recipients, thread);

    if (recipients != null && recipients.isBlocked()) {
      title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_block_white_18dp, 0, 0, 0);
    } else if (recipients != null && recipients.isMuted()) {
      title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_volume_off_white_18dp, 0, 0, 0);
    } else {
      title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
    }
  }

  private void setComposeTitle() {
    this.title.setText(R.string.ConversationActivity_compose_message);
    this.subtitle.setText(null);
    this.subtitle.setVisibility(View.GONE);
  }

  private void setRecipientsTitle(Recipients recipients, ForstaThread thread) {
    int size = recipients.getRecipientsList().size();
    Recipient recipient = recipients.getPrimaryRecipient();

    if (thread.isAnnouncement()) {
      title.setText(getContext().getString(R.string.ConversationActivity_announcement));
      this.subtitle.setText(recipient.getName());
      subtitle.setVisibility(View.VISIBLE);
      announcement.setVisibility(VISIBLE);
    } else {
      announcement.setVisibility(GONE);
      if (recipients.isSingleRecipient()) {
        this.title.setText(recipient.getName());
        this.subtitle.setText(recipient.getFullTag());
        this.subtitle.setVisibility(View.GONE);
      } else {
        title.setText(getContext().getString(R.string.ConversationActivity_group_conversation));
        subtitle.setText(getContext().getResources().getQuantityString(R.plurals.ConversationActivity_d_recipients_in_group, size, size));
        subtitle.setVisibility(View.VISIBLE);
      }
    }

    if (!recipients.includesSelf(getContext())) {
      subtitle.setText("You left this conversation.");
    }
    // Always show thread title, if available
    if (!TextUtils.isEmpty(thread.getTitle())) {
      title.setText(thread.getTitle());
    }
  }
}

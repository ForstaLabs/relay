package io.forsta.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.AttributeSet;

import io.forsta.ccsm.util.ForstaUtils;
import io.forsta.securesms.R;
import io.forsta.securesms.components.emoji.EmojiTextView;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.ThreadPreferenceDatabase;
import io.forsta.securesms.database.model.ThreadRecord;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;

public class FromTextView extends EmojiTextView {

  private static final String TAG = FromTextView.class.getSimpleName();

  public FromTextView(Context context) {
    super(context);
  }

  public FromTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void setText(Recipient recipient) {
    setText(RecipientFactory.getRecipientsFor(getContext(), recipient, true));
  }

  public void setText(Recipients recipients) {
    setText(recipients, true);
  }

  public void setText(Recipients recipients, boolean read) {
    int        attributes[]   = new int[]{R.attr.conversation_list_item_count_color};
    TypedArray colors         = getContext().obtainStyledAttributes(attributes);
    boolean    isUnnamedGroup = recipients.isGroupRecipient() && TextUtils.isEmpty(recipients.getPrimaryRecipient().getName());

    String fromString = "";

    if (isUnnamedGroup) {
      fromString = getContext().getString(R.string.ConversationActivity_unnamed_group);
    } else {
      fromString = recipients.toCondensedString(getContext());
    }

    int typeface;

    if (isUnnamedGroup) {
      if (!read) typeface = Typeface.BOLD_ITALIC;
      else       typeface = Typeface.ITALIC;
    } else if (!read) {
      typeface = Typeface.BOLD;
    } else {
      typeface = Typeface.NORMAL;
    }

    SpannableStringBuilder builder = new SpannableStringBuilder(fromString);
    builder.setSpan(new StyleSpan(typeface), 0, builder.length(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);

    colors.recycle();

    setText(builder);

    if      (recipients.isBlocked()) setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_block_grey600_18dp, 0, 0, 0);
    else if (recipients.isMuted())   setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_volume_off_grey600_18dp, 0, 0, 0);
    else                             setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
  }

  public void setText(String title, boolean read) {
    int typeface;
    if (!read) {
      typeface = Typeface.BOLD;
    } else {
      typeface = Typeface.NORMAL;
    }

    SpannableStringBuilder builder = new SpannableStringBuilder(title);
    builder.setSpan(new StyleSpan(typeface), 0, builder.length(),
        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    setText(builder);
  }

}

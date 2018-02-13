package io.forsta.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import io.forsta.ccsm.RecipientDetailsDialog;
import io.forsta.ccsm.components.AvatarImageViewTarget;
import io.forsta.securesms.R;
import io.forsta.securesms.color.MaterialColor;
import io.forsta.securesms.contacts.avatars.ContactPhotoFactory;
import io.forsta.securesms.recipients.Recipient;
import io.forsta.securesms.recipients.RecipientFactory;
import io.forsta.securesms.recipients.Recipients;
import io.forsta.securesms.util.ResUtil;

public class AvatarImageView extends ImageView {

  private boolean inverted;

  public AvatarImageView(Context context) {
    super(context);
    setScaleType(ScaleType.CENTER_CROP);
  }

  public AvatarImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setScaleType(ScaleType.CENTER_CROP);

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AvatarImageView, 0, 0);
      inverted = typedArray.getBoolean(0, false);
      typedArray.recycle();
    }
  }

  public void setAvatar(final @Nullable Recipients recipients, final MaterialColor backgroundColor) {
    setAvatar(recipients, backgroundColor, false);
  }

  public void setAvatar(final @Nullable Recipients recipients, boolean quickContactEnabled) {
    setAvatar(recipients, recipients.getColor(), quickContactEnabled);
  }

  public void setAvatar(@Nullable Recipient recipient, boolean quickContactEnabled) {
    setAvatar(RecipientFactory.getRecipientsFor(getContext(), recipient, true), quickContactEnabled);
  }

  private void setAvatar(Recipients recipients, final MaterialColor backgroundColor, boolean enableDetails) {
    if (recipients.isSingleRecipient()) {
      setAvatarClickHandler(recipients, enableDetails);
      final Recipient recipient = recipients.getPrimaryRecipient();
      if (!TextUtils.isEmpty(recipient.getGravitarUrl())) {
        Glide.with(getContext().getApplicationContext()).load(recipient.getGravitarUrl()).asBitmap().into(new AvatarImageViewTarget(this));
      } else {
        setImageDrawable(ContactPhotoFactory.getDefaultContactPhoto(recipient.getName()).asDrawable(getContext(), backgroundColor.toConversationColor(getContext()), inverted));
      }
    } else {
      setOnClickListener(null);
      setImageDrawable(ContactPhotoFactory.getDefaultGroupPhoto().asDrawable(getContext(), backgroundColor.toConversationColor(getContext())));
    }
  }

  public void setAnnouncement() {
    setImageDrawable(ResUtil.getDrawable(getContext(), R.attr.announcement_icon));
  }

  private void setAvatarClickHandler(final Recipients recipients, boolean quickContactEnabled) {
    if (!recipients.isGroupRecipient() && quickContactEnabled) {
      setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          Recipient recipient = recipients.getPrimaryRecipient();
          RecipientDetailsDialog.show(getContext(), recipient);
        }
      });
    } else {
      setOnClickListener(null);
    }
  }
}

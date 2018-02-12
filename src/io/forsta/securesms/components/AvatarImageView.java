package io.forsta.securesms.components;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.URL;

import io.forsta.ccsm.RecipientDetailsDialog;
import io.forsta.securesms.R;
import io.forsta.securesms.color.MaterialColor;
import io.forsta.securesms.contacts.avatars.BitmapContactPhoto;
import io.forsta.securesms.contacts.avatars.ContactColors;
import io.forsta.securesms.contacts.avatars.ContactPhoto;
import io.forsta.securesms.contacts.avatars.ContactPhotoFactory;
import io.forsta.securesms.recipients.ContactPhotoFetcher;
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
    if (recipients.isSingleRecipient()) {
      setAvatarClickHandler(recipients, false);
      final ImageView imageView = this;
      Recipient recipient = recipients.getPrimaryRecipient();
      if (!TextUtils.isEmpty(recipient.getGravitarUrl())) {
        new ContactPhotoFetcher(getContext(), new ContactPhotoFetcher.Callbacks() {
          @Override
          public void onComplete(BitmapContactPhoto contactPhoto) {
          // May need handler here, or post to UI thread object
          imageView.setImageDrawable(contactPhoto.asDrawable(getContext(), backgroundColor.toConversationColor(getContext()), inverted));
          }
        }).execute(recipient.getGravitarUrl());
      } else {
        setImageDrawable(ContactPhotoFactory.getDefaultContactPhoto(recipient.getName()).asDrawable(getContext(), backgroundColor.toConversationColor(getContext()), inverted));
      }
    } else {
      setOnClickListener(null);
      setImageDrawable(ContactPhotoFactory.getDefaultGroupPhoto().asDrawable(getContext(), backgroundColor.toConversationColor(getContext())));
    }
  }

  public void setAvatar(final @Nullable Recipients recipients, boolean quickContactEnabled) {
    setAvatarClickHandler(recipients, quickContactEnabled);

}

  public void setAvatar(@Nullable Recipient recipient, boolean quickContactEnabled) {
    setAvatar(RecipientFactory.getRecipientsFor(getContext(), recipient, true), quickContactEnabled);
  }

  private void setAvatar(ContactPhoto contactPhoto, MaterialColor color) {
    if (contactPhoto != null) {
      MaterialColor backgroundColor = color;
      setImageDrawable(contactPhoto.asDrawable(getContext(), backgroundColor.toConversationColor(getContext()), inverted));
    } else {
      setImageDrawable(ContactPhotoFactory.getDefaultContactPhoto(null).asDrawable(getContext(), ContactColors.UNKNOWN_COLOR.toConversationColor(getContext()), inverted));
      setOnClickListener(null);
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

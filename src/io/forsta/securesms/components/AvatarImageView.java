package io.forsta.securesms.components;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
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

  public void setAvatar(final @Nullable Recipients recipients, MaterialColor backgroundColor) {
    new AvatarLoader(backgroundColor).execute(recipients);

//    if (recipients != null) {
//      setImageDrawable(recipients.getContactPhoto().asDrawable(getContext(), backgroundColor.toConversationColor(getContext()), inverted));
//      setAvatarClickHandler(recipients, false);
//    } else {
//      setImageDrawable(ContactPhotoFactory.getDefaultContactPhoto(null).asDrawable(getContext(), ContactColors.UNKNOWN_COLOR.toConversationColor(getContext()), inverted));
//      setOnClickListener(null);
//    }
  }

  public void setAvatar(final @Nullable Recipients recipients, boolean quickContactEnabled) {
    new AvatarLoader(recipients.getColor()).execute(recipients);

//    if (recipients != null) {
//      MaterialColor backgroundColor = recipients.getColor();
//      setImageDrawable(recipients.getContactPhoto().asDrawable(getContext(), backgroundColor.toConversationColor(getContext()), inverted));
//      setAvatarClickHandler(recipients, quickContactEnabled);
//    } else {
//      setImageDrawable(ContactPhotoFactory.getDefaultContactPhoto(null).asDrawable(getContext(), ContactColors.UNKNOWN_COLOR.toConversationColor(getContext()), inverted));
//      setOnClickListener(null);
//    }
  }

  public void setAvatar(@Nullable Recipient recipient, boolean quickContactEnabled) {
    setAvatar(RecipientFactory.getRecipientsFor(getContext(), recipient, true), quickContactEnabled);
  }

  private void setAvatar(ContactPhoto contactPhoto, MaterialColor color) {
    if (contactPhoto != null) {
      MaterialColor backgroundColor = color;
      setImageDrawable(contactPhoto.asDrawable(getContext(), backgroundColor.toConversationColor(getContext()), inverted));
//      setAvatarClickHandler(recipients, quickContactEnabled);
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

  private class AvatarLoader extends AsyncTask<Recipients, Void, ContactPhoto> {
    private MaterialColor color;

    public AvatarLoader(MaterialColor color) {
      this.color = color;
    }

    @Override
    protected ContactPhoto doInBackground(Recipients... params) {
      Recipients recipients = params[0];

      if (!recipients.isSingleRecipient() && !recipients.isEmpty()) {
        return ContactPhotoFactory.getDefaultGroupPhoto();
      } else {
        try {
          URL gravatarUrl = recipients.getPrimaryRecipient().getGravitarUrl();
          if (gravatarUrl != null) {
            return new BitmapContactPhoto(getContactGravatar(gravatarUrl));
          }
        } catch (Exception e) {
          Log.w("AvatarImageView", "Error loading gravatar");
        }
      }
      return ContactPhotoFactory.getDefaultContactPhoto(recipients.getPrimaryRecipient().getName());
    }

    @Override
    protected void onPostExecute(ContactPhoto contactPhoto) {
      if (contactPhoto != null) {
        setAvatar(contactPhoto, color);
      } else {

      }
    }

    private Bitmap getContactGravatar(URL url) {
      try {
        InputStream is = (InputStream) url.getContent();
        Bitmap d = BitmapFactory.decodeStream(is);
        is.close();
        return d;
      } catch (Exception e) {
        e.printStackTrace();
      }
      return null;
    }
  }


}

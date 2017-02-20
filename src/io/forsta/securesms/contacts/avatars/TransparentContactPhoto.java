package io.forsta.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;

import com.makeramen.roundedimageview.RoundedDrawable;

import io.forsta.securesms.R;

public class TransparentContactPhoto implements ContactPhoto {

  TransparentContactPhoto() {}

  @Override
  public Drawable asDrawable(Context context, int color) {
    return asDrawable(context, color, false);
  }

  @Override
  public Drawable asDrawable(Context context, int color, boolean inverted) {
    return RoundedDrawable.fromDrawable(context.getResources().getDrawable(android.R.color.transparent));
  }

  @Override
  public Drawable asCallCard(Context context) {
    return ContextCompat.getDrawable(context, R.drawable.ic_contact_picture_large);
  }
}

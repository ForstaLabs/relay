package io.forsta.ccsm.components;

import android.graphics.Bitmap;
import android.widget.ImageView;

import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.makeramen.roundedimageview.RoundedDrawable;

/**
 * Created by jlewis on 2/13/18.
 */

public class AvatarImageViewTarget extends BitmapImageViewTarget {

  public AvatarImageViewTarget(ImageView view) {
    super(view);
  }

  @Override
  protected void setResource(Bitmap resource) {
    RoundedDrawable drawable = RoundedDrawable.fromBitmap(resource)
        .setScaleType(ImageView.ScaleType.CENTER_CROP)
        .setOval(true);
    view.setImageDrawable(drawable);
  }
}

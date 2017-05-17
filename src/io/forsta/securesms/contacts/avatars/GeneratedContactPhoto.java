package io.forsta.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;

import com.amulyakhare.textdrawable.TextDrawable;

import io.forsta.securesms.R;

public class GeneratedContactPhoto implements ContactPhoto {

  private final String name;

  GeneratedContactPhoto(@NonNull String name) {
    this.name  = name;
  }

  @Override
  public Drawable asDrawable(Context context, int color) {
    return asDrawable(context, color, false);
  }

  @Override
  public Drawable asDrawable(Context context, int color, boolean inverted) {
    int targetSize = context.getResources().getDimensionPixelSize(R.dimen.contact_photo_target_size);

    return TextDrawable.builder()
                       .beginConfig()
                       .width(targetSize)
                       .height(targetSize)
                       .textColor(inverted ? color : Color.WHITE)
                       .endConfig()
                       .buildRound(getCharacter(name), inverted ? Color.WHITE : color);
  }

  private String getCharacter(String name) {
    String first = "";
    String last = "";
    String initials = "";
    String[] splitName = name.split(" ");
    if (splitName.length > 0) {
      first = splitName[0];
      initials = String.valueOf(first.charAt(0));
    }
    if (splitName.length > 1) {
      last = splitName[splitName.length - 1];
      initials += String.valueOf(last.charAt(0));
    }

    String cleanedName = name.replaceFirst("[^\\p{L}\\p{Nd}\\p{P}\\p{S}]+", "");

    if (cleanedName.isEmpty()) {
      return "#";
    } else {
     return initials;
//      return String.valueOf(cleanedName.charAt(0));
    }
  }

  @Override
  public Drawable asCallCard(Context context) {
    return ContextCompat.getDrawable(context, R.drawable.ic_contact_picture_large);
  }
}

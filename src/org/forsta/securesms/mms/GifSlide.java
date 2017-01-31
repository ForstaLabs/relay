package org.forsta.securesms.mms;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.Nullable;

import org.forsta.securesms.attachments.Attachment;

import ws.com.google.android.mms.ContentType;

public class GifSlide extends ImageSlide {

  public GifSlide(Context context, Attachment attachment) {
    super(context, attachment);
  }

  public GifSlide(Context context, Uri uri, long size) {
    super(context, constructAttachmentFromUri(context, uri, ContentType.IMAGE_GIF, size));
  }

  @Override
  @Nullable
  public Uri getThumbnailUri() {
    return getUri();
  }
}

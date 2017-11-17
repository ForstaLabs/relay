package io.forsta.securesms.mms;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import io.forsta.securesms.attachments.Attachment;

public class DocumentSlide extends Slide {

  private final String fileName;

  public DocumentSlide(@NonNull Context context, @NonNull Attachment attachment) {
    super(context, attachment);
    this.fileName = "";
  }

  public DocumentSlide(@NonNull Context context, @NonNull Uri uri,
                       @NonNull String contentType,  long size,
                       @Nullable String fileName)
  {
    super(context, constructAttachmentFromUri(context, uri, contentType, size));
    this.fileName = fileName;
  }

  @Override
  public boolean hasDocument() {
    return true;
  }

}

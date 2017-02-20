package io.forsta.securesms.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import io.forsta.securesms.R;
import io.forsta.securesms.attachments.Attachment;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.mms.AudioSlide;
import io.forsta.securesms.mms.GifSlide;
import io.forsta.securesms.mms.ImageSlide;
import io.forsta.securesms.mms.PartAuthority;
import io.forsta.securesms.mms.Slide;
import io.forsta.securesms.mms.VideoSlide;
import io.forsta.securesms.providers.PersistentBlobProvider;

import java.io.IOException;
import java.io.InputStream;

import io.forsta.securesms.mms.DecryptableStreamUriLoader;
import ws.com.google.android.mms.ContentType;

public class MediaUtil {
  private static final String TAG = MediaUtil.class.getSimpleName();

  public static @Nullable ThumbnailData generateThumbnail(Context context, MasterSecret masterSecret, String contentType, Uri uri)
      throws BitmapDecodingException
  {
    long   startMillis = System.currentTimeMillis();
    ThumbnailData data = null;

    if (ContentType.isImageType(contentType)) {
      data = new ThumbnailData(generateImageThumbnail(context, masterSecret, uri));
    }

    if (data != null) {
      Log.w(TAG, String.format("generated thumbnail for part, %dx%d (%.3f:1) in %dms",
                               data.getBitmap().getWidth(), data.getBitmap().getHeight(),
                               data.getAspectRatio(), System.currentTimeMillis() - startMillis));
    }

    return data;
  }

  private static Bitmap generateImageThumbnail(Context context, MasterSecret masterSecret, Uri uri)
      throws BitmapDecodingException
  {
    int maxSize = context.getResources().getDimensionPixelSize(R.dimen.media_bubble_height);
    return BitmapUtil.createScaledBitmap(context, new DecryptableStreamUriLoader.DecryptableUri(masterSecret, uri), maxSize, maxSize);
  }

  public static Slide getSlideForAttachment(Context context, Attachment attachment) {
    Slide slide = null;
    if (isGif(attachment.getContentType())) {
      slide = new GifSlide(context, attachment);
    } else if (ContentType.isImageType(attachment.getContentType())) {
      slide = new ImageSlide(context, attachment);
    } else if (ContentType.isVideoType(attachment.getContentType())) {
      slide = new VideoSlide(context, attachment);
    } else if (ContentType.isAudioType(attachment.getContentType())) {
      slide = new AudioSlide(context, attachment);
    }

    return slide;
  }

  public static @Nullable String getMimeType(Context context, Uri uri) {
    if (uri == null) return null;

    if (PersistentBlobProvider.isAuthority(context, uri)) {
      return PersistentBlobProvider.getMimeType(context, uri);
    }

    String type = context.getContentResolver().getType(uri);
    if (type == null) {
      final String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
      type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
    }
    return getCorrectedMimeType(type);
  }

  public static @Nullable String getCorrectedMimeType(@Nullable String mimeType) {
    if (mimeType == null) return null;

    switch(mimeType) {
    case "image/jpg":
      return MimeTypeMap.getSingleton().hasMimeType(ContentType.IMAGE_JPEG)
             ? ContentType.IMAGE_JPEG
             : mimeType;
    default:
      return mimeType;
    }
  }

  public static long getMediaSize(Context context, MasterSecret masterSecret, Uri uri) throws IOException {
    InputStream in = PartAuthority.getAttachmentStream(context, masterSecret, uri);
    if (in == null) throw new IOException("Couldn't obtain input stream.");

    long   size   = 0;
    byte[] buffer = new byte[4096];
    int    read;

    while ((read = in.read(buffer)) != -1) {
      size += read;
    }
    in.close();

    return size;
  }

  public static boolean isGif(String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().equals("image/gif");
  }

  public static boolean isGif(Attachment attachment) {
    return isGif(attachment.getContentType());
  }

  public static boolean isImage(Attachment attachment) {
    return ContentType.isImageType(attachment.getContentType());
  }

  public static boolean isAudio(Attachment attachment) {
    return ContentType.isAudioType(attachment.getContentType());
  }

  public static boolean isVideo(Attachment attachment) {
    return ContentType.isVideoType(attachment.getContentType());
  }

  public static @Nullable String getDiscreteMimeType(@NonNull String mimeType) {
    final String[] sections = mimeType.split("/", 2);
    return sections.length > 1 ? sections[0] : null;
  }

  public static class ThumbnailData {
    Bitmap bitmap;
    float aspectRatio;

    public ThumbnailData(Bitmap bitmap) {
      this.bitmap      = bitmap;
      this.aspectRatio = (float) bitmap.getWidth() / (float) bitmap.getHeight();
    }

    public Bitmap getBitmap() {
      return bitmap;
    }

    public float getAspectRatio() {
      return aspectRatio;
    }

    public InputStream toDataStream() {
      return BitmapUtil.toCompressedJpeg(bitmap);
    }
  }
}

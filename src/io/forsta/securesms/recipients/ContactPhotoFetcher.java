package io.forsta.securesms.recipients;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;

import com.bumptech.glide.Glide;

import java.util.concurrent.ExecutionException;

import io.forsta.securesms.contacts.avatars.BitmapContactPhoto;

/**
 * Created by john on 2/12/2018.
 */

public class ContactPhotoFetcher extends AsyncTask<String, Void, BitmapContactPhoto> {
  private Context context;
  private Callbacks callbacks;

  public ContactPhotoFetcher(Context context, Callbacks callbacks) {
    this.context = context;
    this.callbacks = callbacks;
  }
  @Override
  protected BitmapContactPhoto doInBackground(String... params) {
    String url = params[0];
    try {
      Bitmap bitmap = Glide.with(context).load(url).asBitmap().into(-1,-1).get();
      return new BitmapContactPhoto(bitmap);
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (ExecutionException e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  protected void onPostExecute(BitmapContactPhoto contactPhoto) {
    if (callbacks != null) {
      callbacks.onComplete(contactPhoto);
    }
  }

  public interface Callbacks {
    void onComplete(BitmapContactPhoto contactPhoto);
  }
}

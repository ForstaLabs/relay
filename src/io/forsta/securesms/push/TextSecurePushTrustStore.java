package io.forsta.securesms.push;

import android.content.Context;

import io.forsta.securesms.R;
import org.whispersystems.signalservice.api.push.TrustStore;

import java.io.InputStream;

public class TextSecurePushTrustStore implements TrustStore {

  private final Context context;

  public TextSecurePushTrustStore(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public InputStream getKeyStoreInputStream() {
    return context.getResources().openRawResource(R.raw.forsta);
  }

  @Override
  public String getKeyStorePassword() {
    return "foobar";
  }
}

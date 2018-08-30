package io.forsta.ccsm;

import android.support.annotation.Nullable;
import android.os.Bundle;

import io.forsta.securesms.PassphraseRequiredActionBarActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.crypto.MasterSecret;

public class PasswordActivity extends PassphraseRequiredActionBarActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState, @Nullable MasterSecret masterSecret) {
    getSupportActionBar().setTitle(R.string.AndroidManifest__authentication);
    setContentView(R.layout.activity_password);
  }

  private void initializeView() {

  }
}

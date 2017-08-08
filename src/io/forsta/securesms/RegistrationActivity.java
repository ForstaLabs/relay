// vim: ts=2:sw=2:expandtab
package io.forsta.securesms;

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.util.Dialogs;
import io.forsta.securesms.util.TextSecurePreferences;
import io.forsta.securesms.util.Util;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

/* XXX Deprecated activity in CCSM onboarding model.
 * Just forwards to the progress activity now. */
public class RegistrationActivity extends BaseActionBarActivity {

  private MasterSecret masterSecret;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    this.masterSecret = getIntent().getParcelableExtra("master_secret");
    int gcmStatus = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
    if (gcmStatus != ConnectionResult.SUCCESS) {
      if (GooglePlayServicesUtil.isUserRecoverableError(gcmStatus)) {
        GooglePlayServicesUtil.getErrorDialog(gcmStatus, this, 9000).show();
      } else {
        Dialogs.showAlertDialog(this, getString(R.string.RegistrationActivity_unsupported),
                                getString(R.string.RegistrationActivity_sorry_this_device_is_not_supported_for_data_messaging));
      }
      return;
    }
    Intent intent = new Intent(this, RegistrationProgressActivity.class);
    intent.putExtra("master_secret", masterSecret);
    startActivity(intent);
    finish();
  }
}

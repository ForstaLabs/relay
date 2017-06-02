package io.forsta.ccsm;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Date;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.securesms.PassphraseRequiredActionBarActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.crypto.MasterCipher;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.util.DirectoryHelper;

public class DirectoryActivity extends PassphraseRequiredActionBarActivity {

  private static final String TAG = DirectoryActivity.class.getSimpleName();

  private MasterSecret mMasterSecret;
  private MasterCipher mMasterCipher;
  private TextView lastSync;
  private Button mRefresh;
  private Button mClear;
  private DirectoryFragment fragment;

  @Override
  protected void onCreate(Bundle savedInstanceState, @Nullable MasterSecret masterSecret) {
    mMasterSecret = masterSecret;
    mMasterCipher = new MasterCipher(mMasterSecret);
    setContentView(R.layout.activity_directory);
    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME);
    lastSync = (TextView) findViewById(R.id.forsta_directory_sync);
    long syncTime = ForstaPreferences.getForstaContactSync(getApplicationContext());
    Date dt = new Date(syncTime);
    lastSync.setText("Last Sync: " + dt.toString());

    FragmentManager fm = getSupportFragmentManager();
    Fragment fragment = fm.findFragmentById(R.id.forsta_contacts_list);

    if (fragment == null) {
      fragment = new DirectoryFragment();
      Bundle args = new Bundle();
      args.putParcelable("master_secret", masterSecret);
      fragment.setArguments(args);
      fm.beginTransaction().add(R.id.forsta_contacts_list, fragment).commit();
    }
  }
}

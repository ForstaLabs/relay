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
import android.widget.Toast;

import java.io.IOException;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.securesms.PassphraseRequiredActionBarActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.crypto.MasterCipher;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.util.DirectoryHelper;

public class DirectoryActivity extends PassphraseRequiredActionBarActivity {

  private static final String TAG = DirectoryActivity.class.getSimpleName();

  private MasterSecret mMasterSecret;
  private MasterCipher mMasterCipher;
  private Button mRefresh;

  @Override
  protected void onCreate(Bundle savedInstanceState, @Nullable MasterSecret masterSecret) {
    mMasterSecret = masterSecret;
    mMasterCipher = new MasterCipher(mMasterSecret);
    setContentView(R.layout.activity_directory);
    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME);
    initView();
  }

  private void initView() {
    mRefresh = (Button) findViewById(R.id.forsta_update_contacts);
    mRefresh.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        handleSyncContacts();
      }
    });
  }

  private void handleSyncContacts() {
    final ProgressDialog syncDialog = new ProgressDialog(this);
    syncDialog.setTitle("Forsta Contacts");
    syncDialog.setMessage("Downloading and updating contacts and groups.");
    syncDialog.show();

    new AsyncTask<Void, Void, Boolean>() {

      @Override
      protected Boolean doInBackground(Void... voids) {
        try {
          CcsmApi.syncForstaContacts(getApplicationContext());
          DirectoryHelper.refreshDirectory(getApplicationContext(), mMasterSecret);
          CcsmApi.syncForstaGroups(getApplicationContext(), mMasterSecret);
          return true;
        } catch (Exception e) {
          e.printStackTrace();
        }
        return false;
      }

      @Override
      protected void onPostExecute(Boolean result) {
        syncDialog.dismiss();
      }

      @Override
      protected void onCancelled() {
        syncDialog.dismiss();
      }
    }.execute();
  }
}

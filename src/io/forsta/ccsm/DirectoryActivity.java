package io.forsta.ccsm;

import android.os.Bundle;
import android.app.Activity;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;

import io.forsta.securesms.PassphraseRequiredActionBarActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.crypto.MasterCipher;
import io.forsta.securesms.crypto.MasterSecret;

public class DirectoryActivity extends PassphraseRequiredActionBarActivity {
  private MasterSecret mMasterSecret;
  private MasterCipher mMasterCipher;
  private ForstaContactsFragment contactsFragment;

  @Override
  protected void onCreate(Bundle savedInstanceState, @Nullable MasterSecret masterSecret) {
    mMasterSecret = masterSecret;
    mMasterCipher = new MasterCipher(mMasterSecret);
    setContentView(R.layout.activity_directory);
    getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME);

    FragmentManager fm = getSupportFragmentManager();
    Fragment fragment = fm.findFragmentById(R.id.forsta_contacts_list);

    if (fragment == null) {
      contactsFragment = new ForstaContactsFragment();
      fm.beginTransaction().add(R.id.forsta_contacts_list, contactsFragment).commit();
    }
  }
}

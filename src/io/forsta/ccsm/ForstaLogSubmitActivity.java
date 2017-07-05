package io.forsta.ccsm;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import io.forsta.securesms.BaseActionBarActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.util.DynamicTheme;

/**
 * Created by jlewis on 7/5/17.
 */

public class ForstaLogSubmitActivity  extends BaseActionBarActivity implements ForstaLogSubmitFragment.OnLogSubmittedListener {

  private static final String TAG = ForstaLogSubmitActivity.class.getSimpleName();
  private DynamicTheme dynamicTheme = new DynamicTheme();

  @Override
  protected void onCreate(Bundle icicle) {
    dynamicTheme.onCreate(this);
    super.onCreate(icicle);
    setContentView(R.layout.log_submit_activity);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    ForstaLogSubmitFragment fragment = ForstaLogSubmitFragment.newInstance();
    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
    transaction.replace(R.id.fragment_container, fragment);
    transaction.commit();
  }

  @Override
  protected void onResume() {
    dynamicTheme.onResume(this);
    super.onResume();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
    }

    return false;
  }

  @Override
  public void onSuccess() {
    Toast.makeText(getApplicationContext(), R.string.log_submit_activity__thanks, Toast.LENGTH_LONG).show();
    finish();
  }

  @Override
  public void onFailure() {
    Toast.makeText(getApplicationContext(), R.string.log_submit_activity__log_fetch_failed, Toast.LENGTH_LONG).show();
    finish();
  }

  @Override
  public void onCancel() {
    finish();
  }

  @Override
  public void startActivity(Intent intent) {
    try {
      super.startActivity(intent);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, e);
      Toast.makeText(this, R.string.log_submit_activity__no_browser_installed, Toast.LENGTH_LONG).show();
    }
  }
}

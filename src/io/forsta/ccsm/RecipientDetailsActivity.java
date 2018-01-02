package io.forsta.ccsm;

import android.os.Bundle;
import android.app.Activity;

import io.forsta.ccsm.database.DbFactory;
import io.forsta.ccsm.database.model.ForstaUser;
import io.forsta.securesms.R;
import io.forsta.securesms.database.DatabaseFactory;

public class RecipientDetailsActivity extends Activity {
  private static final String TAG = RecipientDetailsActivity.class.getSimpleName();
  public static final String CONTACT_UID_EXTRA = "contact_uid";
  private String contactUid;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_recipient_details);
    contactUid = getIntent().getStringExtra(CONTACT_UID_EXTRA);

    initializeView();
  }

  private void initializeView() {
    ForstaUser user = DbFactory.getContactDb(RecipientDetailsActivity.this).getUserByAddress(contactUid);

  }
}

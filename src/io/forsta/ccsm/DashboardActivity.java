package io.forsta.ccsm;

import android.database.Cursor;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactsDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.RecipientPreferenceDatabase;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.util.List;

public class DashboardActivity extends BaseActionBarActivity {
    private static final String TAG = DashboardActivity.class.getSimpleName();
    private TextView mContactDebug;
    private TextView mIdentityDebug;
    private TextView mDirectoryDebug;
    private TextView mContactListDebug;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        initView();
    }

    private void initView() {
        mContactDebug = (TextView) findViewById(R.id.contact_debug);
        mIdentityDebug = (TextView) findViewById(R.id.identity_debug);
        mDirectoryDebug = (TextView) findViewById(R.id.directory_debug);
        mContactListDebug = (TextView) findViewById(R.id.contact_list_debug);
        TextSecureDirectory dir = TextSecureDirectory.getInstance(this);
        ContactsDatabase db = DatabaseFactory.getContactsDatabase(this);
        RecipientPreferenceDatabase rdb = DatabaseFactory.getRecipientPreferenceDatabase(this);
        IdentityDatabase idb = DatabaseFactory.getIdentityDatabase(this);

        Cursor cdb = idb.getIdentities();

        StringBuilder sbout = new StringBuilder();
        while (cdb.moveToNext()) {
            String[] cols = cdb.getColumnNames();
            for (int i=0;i < cdb.getColumnCount(); i++) {
                sbout.append(cdb.getColumnName(i)).append(": ");
                try {
                    sbout.append(cdb.getString(i)).append(" ");
                } catch(Exception e) {
                    sbout.append(" bad value");
                }
            }
            sbout.append("\n");
        }

        mIdentityDebug.setText(sbout.toString());
//        Cursor c = db.querySystemContacts(null);
        Cursor c = db.queryTextSecureContacts(null);

        StringBuilder sb = new StringBuilder();
        while (c.moveToNext()) {
            String[] cols = c.getColumnNames();
            for (int i=0;i < c.getColumnCount(); i++) {
                sb.append(c.getColumnName(i)).append(": ");
                try {
                    sb.append(c.getString(i)).append(" ");
                } catch (Exception e) {
                    sb.append(c.getInt(i)).append(" ");
                }
                sb.append("\n");
            }
        }
        mContactListDebug.setText(sb.toString());

        List<String> numbers = dir.getAllNumbers();
        StringBuilder out = new StringBuilder();
        for (String num : numbers) {
            out.append(num).append("\n");
        }

        mDirectoryDebug.setText(out.toString());
        RecipientsList task = new RecipientsList();
        task.execute();

    }

    private class RecipientsList extends AsyncTask<Void, Void, Recipients> {

        @Override
        protected Recipients doInBackground(Void... params) {
            TextSecureDirectory dir = TextSecureDirectory.getInstance(DashboardActivity.this);
            List<String> dirNumbers = dir.getActiveNumbers();
            Recipients recipients = RecipientFactory.getRecipientsFromStrings(DashboardActivity.this, dirNumbers, false);
            return recipients;
        }

        @Override
        protected void onPostExecute(Recipients recipients) {
            List<Recipient> list = recipients.getRecipientsList();
            StringBuilder sb = new StringBuilder();
            for (Recipient item : list) {
                sb.append("Number: ").append(item.getNumber()).append(" ID: ").append(item.getRecipientId());
                sb.append(" Name: ").append(item.getName());
                sb.append("\n");
            }
            mContactDebug.setText(sb.toString());
        }
    }
}

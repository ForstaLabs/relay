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
    private TextView mContactsDebug;
    private TextView mDirectoryDebug;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        initView();
    }

    private void initView() {
        mContactsDebug = (TextView) findViewById(R.id.contacts_debug);
        mDirectoryDebug = (TextView) findViewById(R.id.directory_debug);

        RecipientPreferenceDatabase rdb = DatabaseFactory.getRecipientPreferenceDatabase(this);

        ContactsDatabase db = DatabaseFactory.getContactsDatabase(this);
//        Cursor c = db.querySystemContacts(null);
        Cursor c = db.queryTextSecureContacts(null);

        StringBuilder sb = new StringBuilder();
        sb.append("TextSecureContacts\n");
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

        TextSecureDirectory dir = TextSecureDirectory.getInstance(this);
        List<String> numbers = dir.getAllNumbers();
        sb.append("\nTextSecureDirectory\n");
        for (String num : numbers) {
            sb.append(num).append("\n");
        }

        mDirectoryDebug.setText(sb.toString());
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

            IdentityDatabase idb = DatabaseFactory.getIdentityDatabase(DashboardActivity.this);
            Cursor cdb = idb.getIdentities();

            sb.append("\nIdentities\n");
            while (cdb.moveToNext()) {
                String[] cols = cdb.getColumnNames();
                for (int i=0;i < cdb.getColumnCount(); i++) {
                    sb.append(cdb.getColumnName(i)).append(": ");
                    try {
                        sb.append(cdb.getString(i)).append(" ");
                    } catch(Exception e) {
                        sb.append(" bad value");
                    }
                }
                sb.append("\n");
            }
            mContactsDebug.setText(sb.toString());
        }
    }
}

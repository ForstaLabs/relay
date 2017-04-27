package io.forsta.ccsm;

import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.api.ForstaUser;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.securesms.ConversationListActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.contacts.ContactsDatabase;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.util.DirectoryHelper;

/**
 * Created by jlewis on 4/25/17.
 */

public class ForstaContactsFragment extends Fragment {

  private RecyclerView list;
  private Button refreshContacts;
  private Button refreshUsers;
  private Button refreshGroups;
  private ForstaContactsAdapter adapter;
  private ProgressBar loading;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    final View view = inflater.inflate(R.layout.forsta_contacts_fragment, container, false);

    list = (RecyclerView) view.findViewById(R.id.forsta_contacts_recycler_view);
    list.setLayoutManager(new LinearLayoutManager(getActivity()));
    loading = (ProgressBar) view.findViewById(R.id.forsta_contacts_loading);
    refreshGroups = (Button) view.findViewById(R.id.forsta_directory_groups);
    refreshGroups.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {

      }
    });

    refreshContacts = (Button) view.findViewById(R.id.forsta_update_contacts);
    refreshContacts.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        loading.setVisibility(View.VISIBLE);

        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... voids) {
            CcsmApi.syncForstaContacts(getActivity().getApplicationContext());
            return null;
          }

          @Override
          protected void onPostExecute(Void aVoid) {
            List<ForstaUser> contacts = getContacts();
            adapter.contacts = contacts;
            adapter.notifyDataSetChanged();
            loading.setVisibility(View.GONE);
          }
        }.execute();
      }
    });

    return view;
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);

    initializeAdapter();
  }

  @Override
  public void onResume() {
    super.onResume();

//    list.getAdapter().notifyDataSetChanged();
  }

  private void initView() {

  }

  private List<ForstaUser> getContacts() {
    ContactsDatabase db = DatabaseFactory.getContactsDatabase(getActivity());
    Cursor c = db.queryTextSecureContacts(null);
    List<ForstaUser> contacts = new ArrayList<>();
    while (c.moveToNext()) {
      ForstaUser user = new ForstaUser(c);
      contacts.add(user);
    }
    return contacts;
  }

  private void initializeAdapter() {
    List<ForstaUser> contacts = getContacts();
    adapter = new ForstaContactsAdapter(contacts);
    list.setAdapter(adapter);
  }

  private class ForstaContactsAdapter extends RecyclerView.Adapter<ContactHolder> {
    private List<ForstaUser> contacts;


    public ForstaContactsAdapter(List<ForstaUser> contacts) {
      this.contacts = contacts;
    }

    @Override
    public ContactHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      LayoutInflater inflater = LayoutInflater.from(getActivity());
      View view = inflater.inflate(R.layout.forsta_contacts_list_item, parent, false);
      return new ContactHolder(view);
    }

    @Override
    public void onBindViewHolder(ContactHolder holder, int position) {
      ForstaUser item = contacts.get(position);
      holder.name.setText(item.username);
      holder.number.setText(item.phone);
      holder.registered.setVisibility(item.tsRegistered ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
      return contacts.size();
    }
  }

  private class ContactHolder extends RecyclerView.ViewHolder {
    public TextView name;
    public TextView number;
    public ImageView registered;

    public ContactHolder(View itemView) {
      super(itemView);
      name = (TextView) itemView.findViewById(R.id.forsta_contact_name);
      number = (TextView) itemView.findViewById(R.id.forsta_contact_number);
      registered = (ImageView) itemView.findViewById(R.id.forsta_contact_registered);
      registered.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {

        }
      });
    }
  }
}

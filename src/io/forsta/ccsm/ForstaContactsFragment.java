package io.forsta.ccsm;

/**
 * Created by jlewis on 5/3/17.
 */

import android.app.ProgressDialog;
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
import java.util.Date;
import java.util.List;

import io.forsta.ccsm.api.CcsmApi;
import io.forsta.ccsm.api.ForstaGroup;
import io.forsta.ccsm.api.ForstaUser;
import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.DbFactory;
import io.forsta.securesms.ConversationListActivity;
import io.forsta.securesms.R;
import io.forsta.securesms.contacts.ContactsDatabase;
import io.forsta.securesms.crypto.MasterCipher;
import io.forsta.securesms.crypto.MasterSecret;
import io.forsta.securesms.database.DatabaseFactory;
import io.forsta.securesms.database.GroupDatabase;
import io.forsta.securesms.util.DirectoryHelper;

/**
 * Created by jlewis on 4/25/17.
 */

public class ForstaContactsFragment extends Fragment {

  private MasterSecret masterSecret;
  private RecyclerView list;
  private RecyclerView groupList;
  private Button refreshContacts;
  private Button clearContacts;
  private Button clearSystemContacts;
  private ForstaContactsAdapter adapter;
  private ForstaGroupsAdapter groupAdapter;
  private ProgressBar loading;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.masterSecret = getArguments().getParcelable("master_secret");
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    final View view = inflater.inflate(R.layout.forsta_contacts_fragment, container, false);

    list = (RecyclerView) view.findViewById(R.id.forsta_contacts_recycler_view);
    list.setLayoutManager(new LinearLayoutManager(getActivity()));
    groupList = (RecyclerView) view.findViewById(R.id.forsta_groups_recycler_view);
    groupList.setLayoutManager(new LinearLayoutManager(getActivity()));

    loading = (ProgressBar) view.findViewById(R.id.forsta_contacts_loading);
    refreshContacts = (Button) view.findViewById(R.id.forsta_update_contacts);
    refreshContacts.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        handleSyncContacts();
      }
    });
    clearContacts = (Button) view.findViewById(R.id.forsta_clear_contacts);
    clearContacts.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        ContactDb db = DbFactory.getContactDb(getActivity());
        db.removeAll();
        List<ForstaUser> contacts = db.getUsers();
        adapter.contacts = contacts;
        adapter.notifyDataSetChanged();
      }
    });
    clearSystemContacts = (Button) view.findViewById(R.id.forsta_clear_system_contacts);
    clearSystemContacts.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        CcsmApi.removeAllContacts(getActivity().getApplicationContext());
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
  }

  private List<ForstaUser> getContacts() {
    ContactDb db = DbFactory.getContactDb(getActivity());
    List<ForstaUser> contacts = db.getUsers();
    return contacts;
  }

  private List<ForstaGroup> getGroups() {
    List<ForstaGroup> groups = new ArrayList<>();
    GroupDatabase db = DatabaseFactory.getGroupDatabase(getActivity());
    GroupDatabase.Reader reader = db.getGroups();
    GroupDatabase.GroupRecord record;
    GroupDatabase.GroupRecord existing = null;
    while ((record = reader.getNext()) != null) {
      ForstaGroup group = new ForstaGroup(record);
      groups.add(group);
    }
    reader.close();
    return groups;
  }

  private void initializeAdapter() {
    List<ForstaUser> contacts = getContacts();
    adapter = new ForstaContactsAdapter(contacts);
    list.setAdapter(adapter);

    List<ForstaGroup> groups = getGroups();
    groupAdapter = new ForstaGroupsAdapter(groups);
    groupList.setAdapter(groupAdapter);
  }

  private void handleSyncContacts() {
    final ProgressDialog syncDialog = new ProgressDialog(getActivity());
    syncDialog.setTitle("Forsta Contacts");
    syncDialog.setMessage("Downloading and updating contacts and groups.");
    syncDialog.show();

    new AsyncTask<Void, Void, Boolean>() {

      @Override
      protected Boolean doInBackground(Void... voids) {
        try {
          CcsmApi.syncForstaContacts(getActivity().getApplicationContext());
          CcsmApi.syncForstaGroups(getActivity().getApplicationContext(), masterSecret);
          DirectoryHelper.refreshDirectory(getActivity().getApplicationContext(), masterSecret);
          ForstaPreferences.setForstaContactSync(getActivity().getApplicationContext(), new Date().getTime());
          return true;
        } catch (Exception e) {
          e.printStackTrace();
        }
        return false;
      }

      @Override
      protected void onPostExecute(Boolean result) {
        syncDialog.dismiss();
        List<ForstaUser> contacts = getContacts();
        adapter.contacts = contacts;
        adapter.notifyDataSetChanged();

        List<ForstaGroup> groups = getGroups();
        groupAdapter.groups = groups;
        groupAdapter.notifyDataSetChanged();
      }

      @Override
      protected void onCancelled() {
        syncDialog.dismiss();
      }
    }.execute();
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
      holder.name.setText(item.getName());
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

  private class ForstaGroupsAdapter extends RecyclerView.Adapter<GroupHolder> {
    private List<ForstaGroup> groups;

    public ForstaGroupsAdapter(List<ForstaGroup> groups) {
      this.groups = groups;
    }

    @Override
    public GroupHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      LayoutInflater inflater = LayoutInflater.from(getActivity());
      View view = inflater.inflate(R.layout.forsta_groups_list_item, parent, false);
      return new GroupHolder(view);
    }

    @Override
    public void onBindViewHolder(GroupHolder holder, int position) {
      ForstaGroup item = groups.get(position);
      holder.name.setText(item.description);
      holder.number.setText(item.id);
    }

    @Override
    public int getItemCount() {
      return groups.size();
    }
  }

  private class GroupHolder extends RecyclerView.ViewHolder {
    public TextView name;
    public TextView number;

    public GroupHolder(View itemView) {
      super(itemView);
      name = (TextView) itemView.findViewById(R.id.forsta_group_name);
      number = (TextView) itemView.findViewById(R.id.forsta_group_number);
    }
  }
}
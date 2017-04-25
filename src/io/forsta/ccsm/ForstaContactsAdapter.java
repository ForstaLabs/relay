package io.forsta.ccsm;

import android.support.v4.app.FragmentManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import io.forsta.securesms.R;

/**
 * Created by jlewis on 4/25/17.
 */

public class ForstaContactsAdapter extends RecyclerView.Adapter<ForstaContactsAdapter.ContactHolder> {
  private List<String> contacts;

  public ForstaContactsAdapter(List<String> contacts) {
    this.contacts = contacts;
  }

  @Override
  public ContactHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    View view = inflater.inflate(R.layout.forsta_contacts_list_item, parent, false);
    return new ContactHolder(view);
  }

  @Override
  public void onBindViewHolder(ContactHolder holder, int position) {
    String contactName = contacts.get(position);
    holder.name.setText(contactName);
  }

  @Override
  public int getItemCount() {
    return contacts.size();
  }

  public class ContactHolder extends RecyclerView.ViewHolder {

    public TextView name;

    public ContactHolder(View itemView) {
      super(itemView);
      name = (TextView) itemView.findViewById(R.id.forsta_contact_name);
    }
  }
}

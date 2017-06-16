package io.forsta.ccsm.database;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import io.forsta.ccsm.database.model.ForstaRecipient;
import io.forsta.securesms.R;

/**
 * Created by jlewis on 5/22/17.
 */

public class DirectoryAdapter extends RecyclerView.Adapter<DirectoryAdapter.ContactHolder> {
  List<ForstaRecipient> recipientList;
  ItemClickListener clickListener;

  public DirectoryAdapter(List<ForstaRecipient> recipients) {
    this.recipientList = recipients;
  }

  @Override
  public ContactHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    View view = inflater.inflate(R.layout.forsta_directory_list_item, parent, false);
    return new ContactHolder(view);
  }

  @Override
  public void onBindViewHolder(ContactHolder holder, final int position) {
    ForstaRecipient current = recipientList.get(position);
    holder.name.setText(current.name);
    holder.number.setText(current.number);
    holder.slug.setText(current.slug);
    holder.layout.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (clickListener != null) {
          clickListener.onItemClick(recipientList.get(position));
        }
      }
    });
  }

  @Override
  public int getItemCount() {
    return recipientList.size();
  }

  public void setItemClickListener(ItemClickListener listener) {
    this.clickListener = listener;
  }

  public interface ItemClickListener {
    void onItemClick(ForstaRecipient recipient);
  }

  class ContactHolder extends RecyclerView.ViewHolder {
    public TextView name;
    public TextView number;
    public TextView slug;
    public LinearLayout layout;

    public ContactHolder(View itemView) {
      super(itemView);
      name = (TextView) itemView.findViewById(R.id.forsta_directory_item_name);
      number = (TextView) itemView.findViewById(R.id.forsta_directory_item_number);
      slug = (TextView) itemView.findViewById(R.id.forsta_directory_item_slug);
      layout = (LinearLayout) itemView.findViewById(R.id.forsta_directory_item);
    }
  }
}




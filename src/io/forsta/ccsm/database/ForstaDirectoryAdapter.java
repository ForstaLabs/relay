package io.forsta.ccsm.database;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import io.forsta.securesms.R;
import io.forsta.securesms.database.CursorRecyclerViewAdapter;

/**
 * Created by jlewis on 6/2/17.
 */

public class ForstaDirectoryAdapter extends CursorRecyclerViewAdapter {

  public ForstaDirectoryAdapter(Context context, Cursor cursor) {
    super(context, cursor);
  }

  @Override
  public RecyclerView.ViewHolder onCreateItemViewHolder(ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    View view = inflater.inflate(R.layout.forsta_directory_list_item, parent, false);
    return new DirectoryViewHolder(view);
  }

  @Override
  public void onBindItemViewHolder(RecyclerView.ViewHolder viewHolder, @NonNull Cursor cursor) {
    DirectoryViewHolder holder = (DirectoryViewHolder) viewHolder;
    cursor.moveToPosition(cursor.getPosition());
    holder.setData(cursor);
  }

  public class DirectoryViewHolder extends RecyclerView.ViewHolder {
    public TextView name;
    public TextView number;
    public TextView slug;
    public CheckBox selected;

    public DirectoryViewHolder(View itemView) {
      super(itemView);
      name = (TextView) itemView.findViewById(R.id.forsta_directory_item_name);
      number = (TextView) itemView.findViewById(R.id.forsta_directory_item_number);
      slug = (TextView) itemView.findViewById(R.id.forsta_directory_item_slug);
      selected = (CheckBox) itemView.findViewById(R.id.forsta_directory_selected);
    }

    public void setData(Cursor cursor) {
      name.setText(cursor.getString(cursor.getColumnIndex(ContactDb.NAME)));
      number.setText(cursor.getString(cursor.getColumnIndex(ContactDb.NUMBER)));
      slug.setText(cursor.getString(cursor.getColumnIndex(ContactDb.USERNAME)));
    }
  }
}

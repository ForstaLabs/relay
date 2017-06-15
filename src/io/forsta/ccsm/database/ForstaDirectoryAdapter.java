package io.forsta.ccsm.database;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import io.forsta.securesms.R;
import io.forsta.securesms.database.CursorRecyclerViewAdapter;

/**
 * Created by jlewis on 6/2/17.
 */

public class ForstaDirectoryAdapter extends CursorRecyclerViewAdapter<ForstaDirectoryAdapter.DirectoryViewHolder> {

  private static final String TAG = ForstaDirectoryAdapter.class.getSimpleName();
  private ItemClickListener clickListener;

  public ForstaDirectoryAdapter(Context context, Cursor cursor) {
    super(context, cursor);
  }

  @Override
  public ForstaDirectoryAdapter.DirectoryViewHolder onCreateItemViewHolder(ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    View view = inflater.inflate(R.layout.forsta_directory_list_item, parent, false);
    view.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        TextView slug = (TextView) view.findViewById(R.id.forsta_directory_item_slug);
        String slugText = slug.getText().toString();
        if (clickListener != null) {
          clickListener.onItemClick(slugText);
        }
      }
    });
    return new DirectoryViewHolder(view);
  }

  @Override
  public void onBindItemViewHolder(DirectoryViewHolder viewHolder, @NonNull Cursor cursor) {
    DirectoryViewHolder holder = (DirectoryViewHolder) viewHolder;
    cursor.moveToPosition(cursor.getPosition());
    holder.setData(cursor);
  }

  public void setItemClickListener(ItemClickListener listener) {
    this.clickListener = listener;
  }

  public interface ItemClickListener {
    void onItemClick(String slug);
  }

  public class DirectoryViewHolder extends RecyclerView.ViewHolder {
    public TextView name;
    public TextView number;
    public TextView slug;

    public DirectoryViewHolder(View itemView) {
      super(itemView);
      name = (TextView) itemView.findViewById(R.id.forsta_directory_item_name);
      number = (TextView) itemView.findViewById(R.id.forsta_directory_item_number);
      slug = (TextView) itemView.findViewById(R.id.forsta_directory_item_slug);
    }

    public void setData(Cursor cursor) {
      name.setText(cursor.getString(cursor.getColumnIndex(ContactDb.NAME)));
      number.setText(cursor.getString(cursor.getColumnIndex(ContactDb.NUMBER)));
      slug.setText(cursor.getString(cursor.getColumnIndex(ContactDb.USERNAME)));
    }
  }
}

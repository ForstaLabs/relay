package io.forsta.ccsm.database;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import io.forsta.securesms.R;

/**
 * Created by jlewis on 6/12/17.
 */

public class DirectorySpinnerAdapter extends SimpleCursorAdapter {
  private LayoutInflater inflater;
  private TextView item;

  public DirectorySpinnerAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
    super(context, layout, c, from, to, flags);
    inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
  }

  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    super.bindView(view, context, cursor);
  }

  @Override
  public View newDropDownView(Context context, Cursor cursor, ViewGroup parent) {
    return super.newDropDownView(context, cursor, parent);
  }


  private View customView(int position, View convertView, ViewGroup parent) {
    View view = inflater.inflate(R.layout.forsta_directory_spinner_item, parent, false);
    item = (TextView) view.findViewById(R.id.forsta_spinner_text);

    return view;
  }


}

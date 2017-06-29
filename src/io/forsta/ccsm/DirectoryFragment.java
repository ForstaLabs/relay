package io.forsta.ccsm;

import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
import android.widget.TextView;

import io.forsta.ccsm.database.ContactDb;
import io.forsta.ccsm.database.ForstaDirectoryAdapter;
import io.forsta.ccsm.database.loaders.DirectoryLoader;
import io.forsta.securesms.R;
import io.forsta.securesms.contacts.ContactsCursorLoader;

/**
 * Created by jlewis on 6/2/17.
 */

public class DirectoryFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
  private TextView noItems;
  private RecyclerView list;
  private ForstaDirectoryAdapter adapter;
  private String query = "";
  private ItemSelectedListener selectionListener;

  public DirectoryFragment() {

  }

  private void initAdapter() {
    adapter = new ForstaDirectoryAdapter(getActivity(), null);
    adapter.setItemClickListener(new ForstaDirectoryAdapter.ItemClickListener() {
      @Override
      public void onItemClick(String slug) {
        if (selectionListener != null) {
          selectionListener.onItemSelected(slug);
        }
      }
    });
    list.setAdapter(adapter);
    getLoaderManager().restartLoader(0, Bundle.EMPTY, this);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.forsta_directory_fragment, container, false);
    list = (RecyclerView) view.findViewById(R.id.forsta_directory_recycler_view);
    noItems = (TextView) view.findViewById(R.id.forsta_directory_noitems);
    list.setLayoutManager(new LinearLayoutManager(getActivity()));
    return view;
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    initAdapter();
  }

  public void setQueryFilter(String query) {
    this.query = query;
    getLoaderManager().restartLoader(0, null, this);
  }

  public void resetQueryFilter() {
      setQueryFilter("");
  }

  public void setItemSelectedListener(ItemSelectedListener listener) {
    this.selectionListener = listener;
  }

  public interface ItemSelectedListener {
    void onItemSelected(String slug);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new DirectoryLoader(getActivity(), query);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    adapter.swapCursor(cursor);
    int itemCount = adapter.getItemCount();
    if (itemCount < 1) {
      //Show no items found message.
      noItems.setVisibility(View.VISIBLE);
    } else {
      noItems.setVisibility(View.GONE);
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    adapter.swapCursor(null);
  }
}

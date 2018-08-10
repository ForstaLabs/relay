package io.forsta.securesms;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.CursorAdapter;
import android.widget.TextView;

import io.forsta.securesms.components.AvatarImageView;
import io.forsta.securesms.recipients.Recipient;

public class ReplyListAdapter extends CursorAdapter {

    private Context mContext;
    private int mResource;
    private Recipient mAuthor;


    public ReplyListAdapter(@NonNull Context context, int resource, Cursor cursor, Recipient author) {
        super(context, cursor);
        mContext = context;
        mResource = resource;
        mAuthor = author;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return LayoutInflater.from(context).inflate(mResource, viewGroup, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        //String title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
        String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
        int vote = cursor.getInt(cursor.getColumnIndexOrThrow("vote"));

        TextView voteCount = view.findViewById(R.id.reply_vote);
        AvatarImageView contactPhoto = view.findViewById(R.id.reply_contact_photo);
        TextView bodyText = view.findViewById(R.id.reply_text);

        if(vote > 0) {
            voteCount.setVisibility(View.VISIBLE);
            voteCount.setText("(" + String.valueOf(vote) + ")");
        } else {
            voteCount.setVisibility(View.GONE);
        }
        contactPhoto.setAvatar(mAuthor, true);
        bodyText.setText(body);
    }
}

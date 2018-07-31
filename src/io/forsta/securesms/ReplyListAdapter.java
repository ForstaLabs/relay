package io.forsta.securesms;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;

import io.forsta.securesms.components.AvatarImageView;
import io.forsta.securesms.components.ReplyListView;
import io.forsta.securesms.database.model.Reply;
import io.forsta.securesms.recipients.Recipient;

public class ReplyListAdapter extends ArrayAdapter<Reply> {

    private static final String TAG = "ReplyListAdapter";
    private Context mContext;
    private int mResource;


    public ReplyListAdapter(@NonNull Context context, int resource,  @NonNull ArrayList<Reply> objects) {
        super(context, resource, objects);
        mContext = context;
        mResource = resource;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        long id = getItem(position).getId();
        Recipient author = getItem(position).getAuthor();
        String body = getItem(position) .getText();
        int vote = getItem(position).getVote();

        LayoutInflater inflater = LayoutInflater.from(mContext);
        convertView = inflater.inflate(mResource, parent,false);

        TextView voteCount = convertView.findViewById(R.id.reply_vote);
        AvatarImageView contactPhoto = convertView.findViewById(R.id.reply_contact_photo);
        TextView bodyText = convertView.findViewById(R.id.reply_text);

        voteCount.setText(String.valueOf(vote));
        contactPhoto.setAvatar(author, false);
        bodyText.setText(body);

        return convertView;

    }
}

package eshque.com.imagebrowserlol;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

public class PicturesAdapter extends RecyclerView.Adapter<PictureViewHolder> {

    Context context;
    SearchResultsProvider provider;
    ArrayList<PictureViewHolder> allHolders = new ArrayList<>();

    public PicturesAdapter(Context context, SearchResultsProvider provider) {
        this.context = context;
        this.provider = provider;
    }

    @Override
    public PictureViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View layout = LayoutInflater.from(context).inflate(R.layout.pic_item, null);
        PictureViewHolder holder = new PictureViewHolder(context, layout);
        allHolders.add(holder);
        return holder;
    }

    @Override
    public void onBindViewHolder(PictureViewHolder holder, int position) {
        PictureInfoItem item = provider.getResultAt(position);
        holder.setInfoItem(item);
    }

    @Override
    public int getItemCount() {
        return provider.getResultsCount();
    }

    public void purge() {
        cleanupHoldersList();
        for(int i=0; i<allHolders.size(); ++i) {
            allHolders.get(i).cancelDownload();
        }
    }

    void cleanupHoldersList() {
        for(int i=allHolders.size()-1; i>=0; --i) {
            PictureViewHolder h = allHolders.get(i);
            if (!h.isDownloading() && h.getLayoutPosition() < 0 )
                allHolders.remove(i);
        }
    }
}

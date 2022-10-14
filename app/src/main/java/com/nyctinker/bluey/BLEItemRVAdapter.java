package com.nyctinker.bluey;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

// Uses code from https://www.geeksforgeeks.org/how-to-update-recycleview-adapter-data-in-android/
public class BLEItemRVAdapter extends RecyclerView.Adapter<BLEItemRVAdapter.ViewHolder> {
    private ArrayList<String> bleItemRVModalArrayList;


    public BLEItemRVAdapter(ArrayList<String> bleItemRVModalArrayList) {
        this.bleItemRVModalArrayList = bleItemRVModalArrayList;
    }

    @NonNull
    @Override
    public BLEItemRVAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the layout file
        // with recycler view
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.ble_rv_item, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BLEItemRVAdapter.ViewHolder holder, int position) {
        holder.bleItemTV.setText(bleItemRVModalArrayList.get(position));
    }

    @Override
    public int getItemCount() {
        return bleItemRVModalArrayList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private TextView bleItemTV;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Initialize
            bleItemTV = itemView.findViewById(R.id.idTVBLEItemName);
        }
    }
}

package com.example.httpconnectvpn.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.httpconnectvpn.R;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.ViewHolder> {

    public static class ProfileItem {
        public String id;
        public String name;
        public String server;
        public String mode;
        public String jsonConfig;

        public ProfileItem(String id, String name, String server, String mode, String jsonConfig) {
            this.id = id;
            this.name = name;
            this.server = server;
            this.mode = mode;
            this.jsonConfig = jsonConfig;
        }
    }

    public interface OnProfileSelectListener {
        void onSelect(ProfileItem profile);
    }

    private final List<ProfileItem> items;
    private int selectedPosition = 0;
    private final OnProfileSelectListener listener;

    public ProfileAdapter(List<ProfileItem> items, OnProfileSelectListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProfileItem item = items.get(position);
        holder.tvName.setText(item.name);
        holder.tvServer.setText(item.server);
        holder.tvMode.setText(item.mode);

        boolean isSelected = (position == selectedPosition);
        holder.radioButton.setChecked(isSelected);

        if (isSelected) {
            holder.card.setStrokeColor(Color.parseColor("#00E5FF"));
            holder.card.setCardBackgroundColor(Color.parseColor("#1A2B4C"));
        } else {
            holder.card.setStrokeColor(Color.parseColor("#1E293B"));
            holder.card.setCardBackgroundColor(Color.parseColor("#131B2E"));
        }

        holder.itemView.setOnClickListener(v -> {
            int oldPos = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(oldPos);
            notifyItemChanged(selectedPosition);
            if (listener != null) {
                listener.onSelect(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public ProfileItem getSelectedProfile() {
        if (items.isEmpty() || selectedPosition >= items.size()) return null;
        return items.get(selectedPosition);
    }

    public void addProfile(ProfileItem profile) {
        items.add(profile);
        selectedPosition = items.size() - 1;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        RadioButton radioButton;
        TextView tvName, tvServer, tvMode;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.cardContainer);
            radioButton = itemView.findViewById(R.id.radioButton);
            tvName = itemView.findViewById(R.id.tvProfileName);
            tvServer = itemView.findViewById(R.id.tvProfileServer);
            tvMode = itemView.findViewById(R.id.tvProfileMode);
        }
    }
}

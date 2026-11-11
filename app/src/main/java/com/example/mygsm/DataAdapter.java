package com.example.mygsm;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.mygsm.database.Data;

import java.util.Date;
import java.util.List;

public class DataAdapter extends RecyclerView.Adapter<DataAdapter.ViewHolder> {

    private List<Data> dataList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Data item, int position);
    }

    public DataAdapter(List<Data> dataList, OnItemClickListener listener) {
        this.dataList = dataList;
        this.listener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtId,txtName, txtAlert, txtDate;

        public ViewHolder(View itemView) {
            super(itemView);
            txtId = itemView.findViewById(R.id.txtId);
            txtName = itemView.findViewById(R.id.txtName);
            txtAlert = itemView.findViewById(R.id.txtAlert);
            txtDate = itemView.findViewById(R.id.txtDate);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_data, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Data item = dataList.get(position);
        holder.txtName.setText(item.name);
        holder.txtAlert.setText((item.alert ? "ON": "OFF"));
        holder.txtId.setText(item.key);
        holder.txtDate.setText(new Date(item.date).toString());

        holder.itemView.setOnClickListener(v -> {
            int myposition = holder.getBindingAdapterPosition();
            if (myposition != RecyclerView.NO_POSITION) {
                listener.onItemClick(item, myposition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return (dataList == null) ? 0 : dataList.size();
    }

    public void updateData(List<Data> newList) {
        this.dataList = newList;
    }

}

package com.google.android.cameraview.demo.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.cameraview.demo.R;
import com.google.android.cameraview.demo.models.GridViewItem;


import java.util.List;

public class SimilarImageAdapter extends BaseAdapter {

    LayoutInflater inflater;
    List<GridViewItem> items;


    public SimilarImageAdapter(Context context, List<GridViewItem> items) {
        this.items = items;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }


    @Override
    public int getCount() {
        return items.size();
    }


    @Override
    public Object getItem(int position) {
        return (items != null && items.size() > 0) ? items.get(position) : null;
    }


    @Override
    public long getItemId(int position) {
        return position;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.grid_item, null);
        }

        TextView text = (TextView) convertView.findViewById(R.id.textView);
        String filename1 = items.get(position).getPath().substring(items.get(position).getPath().lastIndexOf("/") + 1);
        text.setText(filename1);
        ImageView imageView = (ImageView) convertView.findViewById(R.id.imageView);
        Bitmap image = items.get(position).getImage();
        imageView.setImageBitmap(image);

        return convertView;
    }

    public void updateView(List<GridViewItem> gridItems) {
        if (items != null && items.size() > 0) {
            this.items.clear();
            this.items.addAll(gridItems);
            this.notifyDataSetChanged();
        }

    }
}

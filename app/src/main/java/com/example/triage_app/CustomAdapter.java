package com.example.triage_app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.triage.model.Victim;

import java.util.ArrayList;

//klasa służąca do wyświetlania obiektów Endpoint w widoku ListView
public class CustomAdapter extends BaseAdapter {
    private Context context;
    private ArrayList<Endpoint> endpointList;
    private LayoutInflater inflater;

    public CustomAdapter(Context applicationContext, ArrayList<Endpoint> endpointList) {
        this.context = applicationContext;
        this.endpointList = endpointList;
        inflater = (LayoutInflater.from(applicationContext));
    }

    @Override
    public int getCount() {
        return endpointList.size();
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        view = inflater.inflate(R.layout.endpoint_list_item, null);
        TextView id = (TextView) view.findViewById(R.id.endpoint_id);
        id.setText("Czujnik: "+String.format("%s", endpointList.get(i).getId()));

        return view;
    }
}

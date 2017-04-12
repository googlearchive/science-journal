package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;

import java.util.ArrayList;

class ExperimentsSpinnerAdapter extends ArrayAdapter<Experiment> {
    public ExperimentsSpinnerAdapter(Context context, ArrayList<Experiment> experiments) {
        super(context, R.layout.experiment_spinner_item, new ArrayList<>(experiments));
        // Add a "new experiment" placeholder which is null.
        add(null);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getSpinnerView(position, convertView, parent, R.layout.experiment_spinner_item);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getSpinnerView(position, convertView, parent,
                R.layout.experiment_spinner_dropdown_item);
    }

    private View getSpinnerView(int position, View convertView, ViewGroup parent,
            int resource) {
        Experiment experiment = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(resource, null);
        }
        TextView textView = (TextView) convertView.findViewById(R.id.experiment_title);
        if (isNewExperimentPlaceholder(position)) {
            textView.setText(R.string.new_experiment_spinner_item);
        } else {
            textView.setText(experiment.getExperiment().getDisplayTitle(parent.getContext()));
        }
        return convertView;
    }

    public boolean isNewExperimentPlaceholder(int position) {
        return getItem(position) == null;
    }

}

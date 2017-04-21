package com.google.android.apps.forscience.whistlepunk.filemetadata;

import android.content.Context;
import android.text.TextUtils;

import com.google.common.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.List;

/**
 * Class which has a list of labels, and setters / getters / modifiers for those labels.
 */
public abstract class LabelListHolder {
    // mLabels should be initialized by the class which implements this class in its constructor.
    List<Label> mLabels;

    public int getLabelCount() {
        return mLabels.size();
    }

    /**
     * Gets the current list of labels in this object, ordered by timestamp.
     * Objects in this list should not be modified and expect that state to be saved, instead
     * editing of labels should happen using updateLabel, addTrialLabel, removeLabel.
     */
    public List<Label> getLabels() {
        return mLabels;
    }

    /**
     * Updates a label in the list. Maintains label sort order.
     */
    public void updateLabel(Label label) {
        for (int i = 0; i < mLabels.size(); i++) {
            Label next = mLabels.get(i);
            if (!TextUtils.equals(label.getLabelId(), next.getLabelId())) {
                continue;
            }
            mLabels.set(i, label);
        }
        sortLabels();
    }

    /**
     * Adds a label to the object's list of labels. The list will still be sorted by timestamp.
     */
    public void addLabel(Label label) {
        mLabels.add(label);
        sortLabels();
    }

    /**
     * Deletes a label from this object and also deletes any assets associated with that label.
     */
    public void deleteLabel(Label label, Context context) {
        label.deleteAssets(context);
        mLabels.remove(label);
    }

    private void sortLabels() {
        Collections.sort(mLabels, Label.COMPARATOR_BY_TIMESTAMP);
    }

    @VisibleForTesting
    void setLabels(List<Label> labels) {
        mLabels = labels;
    }
}

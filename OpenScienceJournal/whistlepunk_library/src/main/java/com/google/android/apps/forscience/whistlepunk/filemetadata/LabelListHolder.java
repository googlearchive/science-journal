/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk.filemetadata;

import android.content.Context;
import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;

import java.util.Collections;
import java.util.List;

import io.reactivex.functions.Consumer;

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
        updateLabelWithoutSorting(label);
        sortLabels();
    }

    public void updateLabelWithoutSorting(Label label) {
        for (int i = 0; i < mLabels.size(); i++) {
            Label next = mLabels.get(i);
            if (!TextUtils.equals(label.getLabelId(), next.getLabelId())) {
                continue;
            }
            mLabels.set(i, label);
        }
    }

    /**
     * Adds a label to the object's list of labels. The list will still be sorted by timestamp.
     */
    public void addLabel(Label label) {
        mLabels.add(label);
        sortLabels();
        if (label.getType() == GoosciLabel.Label.PICTURE) {
            onPictureLabelAdded(label);
        }
    }

    /**
     * Deletes a label from this object.
     *
     * Returns a Runnable that also deletes the assets this label referenced.  This should be
     * executed when the deletion is final (user opts not to undo).
     */
    public Consumer<Context> deleteLabelAndReturnAssetDeleter(Label toDelete, String experimentId) {
        for (Label label : mLabels) {
            if (TextUtils.equals(label.getLabelId(), toDelete.getLabelId())) {
                mLabels.remove(label);
                break;
            }
        }
        return context -> deleteLabelAssets(toDelete, context, experimentId);
    }

    protected void deleteLabelAssets(Label toDelete, Context context, String experimentId) {
        if (toDelete.getType() == GoosciLabel.Label.PICTURE) {
            beforeDeletingPictureLabel(toDelete);
        }
        toDelete.deleteAssets(context, experimentId);
    }

    private void sortLabels() {
        Collections.sort(mLabels, Label.COMPARATOR_BY_TIMESTAMP);
    }

    protected void setLabels(List<Label> labels) {
        mLabels = labels;
    }

    protected abstract void onPictureLabelAdded(Label label);

    protected abstract void beforeDeletingPictureLabel(Label label);
}

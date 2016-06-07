package com.google.android.apps.forscience.whistlepunk;

import android.util.SparseIntArray;

import com.google.common.base.Preconditions;

/**
 * Stores a list of master colors and provides a way to get the next color that should be picked
 * from the master list given a list of already taken colors.
 */
public class ColorAllocator {

    private int[] mMasterColors;

    public ColorAllocator(int[] masterColors) {
        Preconditions.checkArgument(masterColors != null, "Cannot have null array.");
        Preconditions.checkArgument(masterColors.length > 0, "Cannot have empty array.");

        mMasterColors = masterColors;
    }

    /**
     * Gets the next color that is least used from the master color array, based on the input used
     * color array.
     */
    public int getNextColor(int[] usedColors) {
        SparseIntArray usedColorSparse = new SparseIntArray(usedColors != null ? usedColors.length
                : 1);
        if (usedColors != null) {
            for (int index = 0; index < usedColors.length; index++) {
                usedColorSparse.put(usedColors[index], usedColorSparse.get(usedColors[index]) + 1);
            }
        }
        int foundColor = -1;
        int leastUsed = 0;
        while (true) {
            for (int index = 0; index < mMasterColors.length; index++) {
                if (usedColorSparse.get(mMasterColors[index], 0) <= leastUsed) {
                    foundColor = mMasterColors[index];
                    break;
                }
            }
            if (foundColor == -1) {
                leastUsed++;
            } else {
                break;
            }
        }
        return foundColor;
    }
}

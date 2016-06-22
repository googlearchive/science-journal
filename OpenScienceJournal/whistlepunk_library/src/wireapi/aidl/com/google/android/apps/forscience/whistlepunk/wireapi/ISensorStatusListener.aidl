/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.wireapi;

interface ISensorStatusListener {
    /**
     * Called when the status of the source changes.
     *
     * @param id ID of the source.
     * @param status one of the {@link Status} values.
     */
    void onSourceStatus(String id, int status) = 0;

    /**
     * Called if there was an error in the source.
     *
     * @param id ID of the source.
     * @param error one of the {@link Error} values.
     * @param errorMessage human readable error message which will be displayed to the user
     */
    void onSourceError(String id, int error, String errorMessage) = 1;
}

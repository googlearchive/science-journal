package com.google.android.apps.forscience.whistlepunk;

import android.support.annotation.NonNull;
import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.android.apps.forscience.whistlepunk.sensordb.MonotonicClock;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MetadataControllerTest extends AndroidTestCase {
    public void testReorderExperiments() {
        MemoryMetadataManager mmm = new MemoryMetadataManager();
        Project p = mmm.newProject();
        Experiment e1 = mmm.newExperiment(p, 1, "e1");
        Experiment e2 = mmm.newExperiment(p, 2, "e2");
        DataControllerImpl dc = buildDataController(mmm);
        final ExplodingFactory explodingFactory = new ExplodingFactory();
        MetadataController mc = new MetadataController(dc, explodingFactory);
        RecordingMetadataListener listener = new RecordingMetadataListener();
        String e1id = e1.getExperimentId();
        String e2id = e2.getExperimentId();

        listener.expectedProjectId = p.getProjectId();
        listener.expectedExperimentIds = Arrays.asList(e2id, e1id);
        mc.setExperimentChangeListener(listener);
        listener.assertListenerCalled(1);

        // e1 is now first in the list
        listener.expectedExperimentIds = Arrays.asList(e1id, e2id);
        mc.changeSelectedExperiment(e1);
        listener.assertListenerCalled(1);

        mc.clearExperimentChangeListener();

        listener.expectedProjectId = p.getProjectId();
        listener.expectedExperimentIds = Arrays.asList(e1id, e2id);
        mc.setExperimentChangeListener(listener);
        listener.assertListenerCalled(1);
    }

    public void testGetNameAfterSettingListener() {
        MemoryMetadataManager mmm = new MemoryMetadataManager();
        Project p = mmm.newProject();
        Experiment e1 = mmm.newExperiment(p, 1, "e1");
        e1.setTitle("E1 title");
        Experiment e2 = mmm.newExperiment(p, 2, "e2");
        e2.setTitle("E2 title");
        DataControllerImpl dc = buildDataController(mmm);
        final ExplodingFactory explodingFactory = new ExplodingFactory();
        MetadataController mc = new MetadataController(dc, explodingFactory);
        RecordingMetadataListener listener = new RecordingMetadataListener();
        String e1id = e1.getExperimentId();
        String e2id = e2.getExperimentId();

        listener.expectedProjectId = p.getProjectId();
        listener.expectedExperimentIds = Arrays.asList(e2id, e1id);
        mc.setExperimentChangeListener(listener);
        assertEquals(e2.getTitle(), mc.getExperimentName(getContext()));
    }

    @NonNull
    private DataControllerImpl buildDataController(MemoryMetadataManager mmm) {
        return new DataControllerImpl(new InMemorySensorDatabase(), MoreExecutors.directExecutor(),
                MoreExecutors.directExecutor(), MoreExecutors.directExecutor(), mmm,
                new MonotonicClock());
    }

    private static class RecordingMetadataListener implements MetadataController
            .MetadataChangeListener {
        public String expectedProjectId;
        public List<String> expectedExperimentIds;
        private int mListenerCalls = 0;

        @Override
        public void onMetadataChanged(Project newProject, List<Experiment> newExperiments) {
            assertEquals(expectedProjectId, newProject.getProjectId());
            List<String> ids = new ArrayList<>();
            for (Experiment currentExperiment : newExperiments) {
                ids.add(currentExperiment.getExperimentId());
            }
            assertEquals(expectedExperimentIds, ids);
            mListenerCalls++;
        }

        public void assertListenerCalled(int times) {
            assertEquals(times, mListenerCalls);
            mListenerCalls = 0;
        }
    }

}
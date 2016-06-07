package com.google.android.apps.forscience.whistlepunk.sensordb;

import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.common.collect.Lists;

public class MemoryMetadataManagerTest extends AndroidTestCase {
    public void testLastUsed() {
        MemoryMetadataManager mmm = new MemoryMetadataManager();
        Project project = mmm.newProject();
        assertEquals(project.getProjectId(), mmm.getLastUsedProject().getProjectId());
    }

    public void testExperimentOrdering() {
        MemoryMetadataManager mmm = new MemoryMetadataManager();
        Project project = mmm.newProject();
        Experiment e1 = mmm.newExperiment(project, 1, "e1");
        Experiment e2 = mmm.newExperiment(project, 2, "e2");
        Experiment e3 = mmm.newExperiment(project, 3, "e3");
        assertEquals(Lists.newArrayList(e3, e2, e1), mmm.getExperimentsForProject(project, false));
        mmm.updateLastUsedExperiment(e2);
        assertEquals(Lists.newArrayList(e2, e3, e1), mmm.getExperimentsForProject(project, false));
    }
}
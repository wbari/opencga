package org.opencb.opencga.core.models.stats;

/**
 * Created by wasim on 25/06/18.
 */
public class StudyStats {

    public int numberOfVariableSets;
    public int numberOfPermissionRules;
    public int numberOfGroups;
    public int totalNumberOfUsers;

    // Project or Study Level ????

    public int numberOfAdmin;
    public int numberOfROU;
    public int numberOfAnalyst;

    public CohortStats cohortStats;
    public FamilyStats familyStats;
    public FileStats fileStats;
    public IndividualStats individualStats;
    public SampleStats sampleStats;




}

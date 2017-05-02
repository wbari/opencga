/*
 * Copyright 2015-2016 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.app.cli.main;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import org.opencb.commons.utils.CommandLineUtils;
import org.opencb.opencga.app.cli.GeneralCliOptions;
import org.opencb.opencga.app.cli.admin.AdminCliOptionsParser;
import org.opencb.opencga.app.cli.analysis.options.AlignmentCommandOptions;
import org.opencb.opencga.app.cli.analysis.options.VariantCommandOptions;
import org.opencb.opencga.app.cli.main.options.*;
import org.opencb.opencga.core.common.GitRepositoryState;

import java.util.*;

/**
 * Created by imedina on AdminMain.
 */
public class OpencgaCliOptionsParser {

    private final JCommander jCommander;

    private final GeneralCliOptions.GeneralOptions generalOptions;
    private final GeneralCliOptions.CommonCommandOptions commonCommandOptions;
    private final GeneralCliOptions.DataModelOptions dataModelOptions;
    private final GeneralCliOptions.NumericOptions numericOptions;

    // Catalog commands
    private UserCommandOptions usersCommandOptions;
    private ProjectCommandOptions projectCommandOptions;
    private StudyCommandOptions studyCommandOptions;
    private FileCommandOptions fileCommandOptions;
    private JobCommandOptions jobCommandOptions;
    private IndividualCommandOptions individualCommandOptions;
    private SampleCommandOptions sampleCommandOptions;
    private VariableCommandOptions variableCommandOptions;
    private CohortCommandOptions cohortCommandOptions;
    private PanelCommandOptions panelCommandOptions;
    private ToolCommandOptions toolCommandOptions;

    // Analysis commands
    private AlignmentCommandOptions alignmentCommandOptions;
    private VariantCommandOptions variantCommandOptions;

    enum OutputFormat {IDS, ID_CSV, NAME_ID_MAP, ID_LIST, RAW, PRETTY_JSON, PLAIN_JSON}


    public OpencgaCliOptionsParser() {
        this(false);
    }

    public OpencgaCliOptionsParser(boolean interactive) {
        generalOptions = new GeneralCliOptions.GeneralOptions();

        jCommander = new JCommander(generalOptions);
        jCommander.setExpandAtSign(false);

        commonCommandOptions = new GeneralCliOptions.CommonCommandOptions();
        dataModelOptions = new GeneralCliOptions.DataModelOptions();
        numericOptions = new GeneralCliOptions.NumericOptions();

        usersCommandOptions = new UserCommandOptions(this.commonCommandOptions, this.dataModelOptions, this.numericOptions, this.jCommander);
        jCommander.addCommand("users", usersCommandOptions);
        JCommander userSubCommands = jCommander.getCommands().get("users");
        userSubCommands.addCommand("create", usersCommandOptions.createCommandOptions);
        userSubCommands.addCommand("info", usersCommandOptions.infoCommandOptions);
        userSubCommands.addCommand("update", usersCommandOptions.updateCommandOptions);
        userSubCommands.addCommand("change-password", usersCommandOptions.changePasswordCommandOptions);
        userSubCommands.addCommand("delete", usersCommandOptions.deleteCommandOptions);
        userSubCommands.addCommand("projects", usersCommandOptions.projectsCommandOptions);
        userSubCommands.addCommand("login", usersCommandOptions.loginCommandOptions);
        userSubCommands.addCommand("logout", usersCommandOptions.logoutCommandOptions);
        userSubCommands.addCommand("reset-password", usersCommandOptions.resetPasswordCommandOptions);

        projectCommandOptions = new ProjectCommandOptions(this.commonCommandOptions, this.dataModelOptions, this.numericOptions, jCommander);
        jCommander.addCommand("projects", projectCommandOptions);
        JCommander projectSubCommands = jCommander.getCommands().get("projects");
        projectSubCommands.addCommand("create", projectCommandOptions.createCommandOptions);
        projectSubCommands.addCommand("info", projectCommandOptions.infoCommandOptions);
        projectSubCommands.addCommand("studies", projectCommandOptions.studiesCommandOptions);
        projectSubCommands.addCommand("update", projectCommandOptions.updateCommandOptions);
        projectSubCommands.addCommand("delete", projectCommandOptions.deleteCommandOptions);

        studyCommandOptions = new StudyCommandOptions(this.commonCommandOptions, this.dataModelOptions, this.numericOptions, jCommander);
        jCommander.addCommand("studies", studyCommandOptions);
        JCommander studySubCommands = jCommander.getCommands().get("studies");
        studySubCommands.addCommand("create", studyCommandOptions.createCommandOptions);
        studySubCommands.addCommand("info", studyCommandOptions.infoCommandOptions);
        studySubCommands.addCommand("search", studyCommandOptions.searchCommandOptions);
        studySubCommands.addCommand("summary", studyCommandOptions.summaryCommandOptions);
        studySubCommands.addCommand("delete", studyCommandOptions.deleteCommandOptions);
        studySubCommands.addCommand("update", studyCommandOptions.updateCommandOptions);
        studySubCommands.addCommand("scan-files", studyCommandOptions.scanFilesCommandOptions);
        studySubCommands.addCommand("resync-files", studyCommandOptions.resyncFilesCommandOptions);
        studySubCommands.addCommand("files", studyCommandOptions.filesCommandOptions);
        studySubCommands.addCommand("jobs", studyCommandOptions.jobsCommandOptions);
        studySubCommands.addCommand("samples", studyCommandOptions.samplesCommandOptions);
        studySubCommands.addCommand("variants", studyCommandOptions.variantsCommandOptions);
        studySubCommands.addCommand("help", studyCommandOptions.helpCommandOptions);
        studySubCommands.addCommand("groups", studyCommandOptions.groupsCommandOptions);
        studySubCommands.addCommand("groups-create", studyCommandOptions.groupsCreateCommandOptions);
        studySubCommands.addCommand("groups-delete", studyCommandOptions.groupsDeleteCommandOptions);
        studySubCommands.addCommand("groups-info", studyCommandOptions.groupsInfoCommandOptions);
        studySubCommands.addCommand("groups-update", studyCommandOptions.groupsUpdateCommandOptions);
        studySubCommands.addCommand("acl", studyCommandOptions.aclsCommandOptions);
        studySubCommands.addCommand("acl-update", studyCommandOptions.aclsUpdateCommandOptions);

        fileCommandOptions = new FileCommandOptions(this.commonCommandOptions,dataModelOptions, numericOptions, jCommander);
        jCommander.addCommand("files", fileCommandOptions);
        JCommander fileSubCommands = jCommander.getCommands().get("files");
//        fileSubCommands.addCommand("copy", fileCommandOptions.copyCommandOptions);
        fileSubCommands.addCommand("create-folder", fileCommandOptions.createFolderCommandOptions);
        fileSubCommands.addCommand("info", fileCommandOptions.infoCommandOptions);
        fileSubCommands.addCommand("download", fileCommandOptions.downloadCommandOptions);
        fileSubCommands.addCommand("grep", fileCommandOptions.grepCommandOptions);
        fileSubCommands.addCommand("search", fileCommandOptions.searchCommandOptions);
        fileSubCommands.addCommand("list", fileCommandOptions.listCommandOptions);
        fileSubCommands.addCommand("tree", fileCommandOptions.treeCommandOptions);
//        fileSubCommands.addCommand("index", fileCommandOptions.indexCommandOptions);
        fileSubCommands.addCommand("content", fileCommandOptions.contentCommandOptions);
//        fileSubCommands.addCommand("fetch", fileCommandOptions.fetchCommandOptions);
        fileSubCommands.addCommand("update", fileCommandOptions.updateCommandOptions);
        fileSubCommands.addCommand("upload", fileCommandOptions.uploadCommandOptions);
        fileSubCommands.addCommand("link", fileCommandOptions.linkCommandOptions);
        fileSubCommands.addCommand("unlink", fileCommandOptions.unlinkCommandOptions);
        fileSubCommands.addCommand("relink", fileCommandOptions.relinkCommandOptions);
        fileSubCommands.addCommand("delete", fileCommandOptions.deleteCommandOptions);
        fileSubCommands.addCommand("refresh", fileCommandOptions.refreshCommandOptions);
//        fileSubCommands.addCommand("variants", fileCommandOptions.variantsCommandOptions);
        fileSubCommands.addCommand("acl", fileCommandOptions.aclsCommandOptions);
        fileSubCommands.addCommand("acl-update", fileCommandOptions.aclsUpdateCommandOptions);

        jobCommandOptions = new JobCommandOptions(this.commonCommandOptions, dataModelOptions, numericOptions, jCommander);
        jCommander.addCommand("jobs", jobCommandOptions);
        JCommander jobSubCommands = jCommander.getCommands().get("jobs");
        jobSubCommands.addCommand("create", jobCommandOptions.createCommandOptions);
        jobSubCommands.addCommand("info", jobCommandOptions.infoCommandOptions);
        jobSubCommands.addCommand("search", jobCommandOptions.searchCommandOptions);
        jobSubCommands.addCommand("visit", jobCommandOptions.visitCommandOptions);
        jobSubCommands.addCommand("delete", jobCommandOptions.deleteCommandOptions);
        jobSubCommands.addCommand("group-by", jobCommandOptions.groupByCommandOptions);
        jobSubCommands.addCommand("acl", jobCommandOptions.aclsCommandOptions);
        jobSubCommands.addCommand("acl-update", jobCommandOptions.aclsUpdateCommandOptions);
       // jobSubCommands.addCommand("status", jobCommandOptions.statusCommandOptions);

        individualCommandOptions = new IndividualCommandOptions(this.commonCommandOptions, dataModelOptions, numericOptions, jCommander);
        jCommander.addCommand("individuals", individualCommandOptions);
        JCommander individualSubCommands = jCommander.getCommands().get("individuals");
        individualSubCommands.addCommand("create", individualCommandOptions.createCommandOptions);
        individualSubCommands.addCommand("info", individualCommandOptions.infoCommandOptions);
        individualSubCommands.addCommand("search", individualCommandOptions.searchCommandOptions);
        individualSubCommands.addCommand("update", individualCommandOptions.updateCommandOptions);
        individualSubCommands.addCommand("delete", individualCommandOptions.deleteCommandOptions);
        individualSubCommands.addCommand("group-by", individualCommandOptions.groupByCommandOptions);
        individualSubCommands.addCommand("samples", individualCommandOptions.sampleCommandOptions);
        individualSubCommands.addCommand("acl", individualCommandOptions.aclsCommandOptions);
        individualSubCommands.addCommand("acl-update", individualCommandOptions.aclsUpdateCommandOptions);
        individualSubCommands.addCommand("annotation-sets-create", individualCommandOptions.annotationCreateCommandOptions);
        individualSubCommands.addCommand("annotation-sets-all-info", individualCommandOptions.annotationAllInfoCommandOptions);
        individualSubCommands.addCommand("annotation-sets-info", individualCommandOptions.annotationInfoCommandOptions);
        individualSubCommands.addCommand("annotation-sets-search", individualCommandOptions.annotationSearchCommandOptions);
        individualSubCommands.addCommand("annotation-sets-update", individualCommandOptions.annotationUpdateCommandOptions);
        individualSubCommands.addCommand("annotation-sets-delete", individualCommandOptions.annotationDeleteCommandOptions);

        sampleCommandOptions = new SampleCommandOptions(this.commonCommandOptions, dataModelOptions, numericOptions, jCommander);
        jCommander.addCommand("samples", sampleCommandOptions);
        JCommander sampleSubCommands = jCommander.getCommands().get("samples");
        sampleSubCommands.addCommand("create", sampleCommandOptions.createCommandOptions);
        sampleSubCommands.addCommand("load", sampleCommandOptions.loadCommandOptions);
        sampleSubCommands.addCommand("info", sampleCommandOptions.infoCommandOptions);
        sampleSubCommands.addCommand("search", sampleCommandOptions.searchCommandOptions);
        sampleSubCommands.addCommand("update", sampleCommandOptions.updateCommandOptions);
        sampleSubCommands.addCommand("delete", sampleCommandOptions.deleteCommandOptions);
        sampleSubCommands.addCommand("group-by", sampleCommandOptions.groupByCommandOptions);
        sampleSubCommands.addCommand("individuals", sampleCommandOptions.individualCommandOptions);
        sampleSubCommands.addCommand("acl", sampleCommandOptions.aclsCommandOptions);
        sampleSubCommands.addCommand("acl-update", sampleCommandOptions.aclsUpdateCommandOptions);
        sampleSubCommands.addCommand("annotation-sets-create", sampleCommandOptions.annotationCreateCommandOptions);
        sampleSubCommands.addCommand("annotation-sets-all-info", sampleCommandOptions.annotationAllInfoCommandOptions);
        sampleSubCommands.addCommand("annotation-sets-info", sampleCommandOptions.annotationInfoCommandOptions);
        sampleSubCommands.addCommand("annotation-sets-search", sampleCommandOptions.annotationSearchCommandOptions);
        sampleSubCommands.addCommand("annotation-sets-update", sampleCommandOptions.annotationUpdateCommandOptions);
        sampleSubCommands.addCommand("annotation-sets-delete", sampleCommandOptions.annotationDeleteCommandOptions);

        variableCommandOptions = new VariableCommandOptions(this.commonCommandOptions, jCommander);
        jCommander.addCommand("variables", variableCommandOptions);
        JCommander variableSubCommands = jCommander.getCommands().get("variables");
        variableSubCommands.addCommand("create", variableCommandOptions.createCommandOptions);
        variableSubCommands.addCommand("info", variableCommandOptions.infoCommandOptions);
        variableSubCommands.addCommand("search", variableCommandOptions.searchCommandOptions);
        variableSubCommands.addCommand("delete", variableCommandOptions.deleteCommandOptions);
        variableSubCommands.addCommand("update", variableCommandOptions.updateCommandOptions);
        variableSubCommands.addCommand("field-add", variableCommandOptions.fieldAddCommandOptions);
        variableSubCommands.addCommand("field-delete", variableCommandOptions.fieldDeleteCommandOptions);
        variableSubCommands.addCommand("field-rename", variableCommandOptions.fieldRenameCommandOptions);

        cohortCommandOptions = new CohortCommandOptions(this.commonCommandOptions, dataModelOptions, numericOptions, jCommander);
        jCommander.addCommand("cohorts", cohortCommandOptions);
        JCommander cohortSubCommands = jCommander.getCommands().get("cohorts");
        cohortSubCommands.addCommand("create", cohortCommandOptions.createCommandOptions);
        cohortSubCommands.addCommand("info", cohortCommandOptions.infoCommandOptions);
        cohortSubCommands.addCommand("search", cohortCommandOptions.searchCommandOptions);
        cohortSubCommands.addCommand("samples", cohortCommandOptions.samplesCommandOptions);
        cohortSubCommands.addCommand("update", cohortCommandOptions.updateCommandOptions);
        cohortSubCommands.addCommand("delete", cohortCommandOptions.deleteCommandOptions);
        cohortSubCommands.addCommand("stats", cohortCommandOptions.statsCommandOptions);
        cohortSubCommands.addCommand("group-by", cohortCommandOptions.groupByCommandOptions);
        cohortSubCommands.addCommand("acl", cohortCommandOptions.aclsCommandOptions);
        cohortSubCommands.addCommand("acl-update", cohortCommandOptions.aclsUpdateCommandOptions);
        cohortSubCommands.addCommand("annotation-sets-create", cohortCommandOptions.annotationCreateCommandOptions);
        cohortSubCommands.addCommand("annotation-sets-all-info", cohortCommandOptions.annotationAllInfoCommandOptions);
        cohortSubCommands.addCommand("annotation-sets-info", cohortCommandOptions.annotationInfoCommandOptions);
        cohortSubCommands.addCommand("annotation-sets-search", cohortCommandOptions.annotationSearchCommandOptions);
        cohortSubCommands.addCommand("annotation-sets-update", cohortCommandOptions.annotationUpdateCommandOptions);
        cohortSubCommands.addCommand("annotation-sets-delete", cohortCommandOptions.annotationDeleteCommandOptions);

//        toolCommandOptions = new ToolCommandOptions(this.commonCommandOptions, jCommander);
//        jCommander.addCommand("tools", toolCommandOptions);
//        JCommander toolSubCommands = jCommander.getCommands().get("tools");
//        toolSubCommands.addCommand("help", toolCommandOptions.helpCommandOptions);
//        toolSubCommands.addCommand("info", toolCommandOptions.infoCommandOptions);
//        toolSubCommands.addCommand("search", toolCommandOptions.searchCommandOptions);
//        toolSubCommands.addCommand("update", toolCommandOptions.updateCommandOptions);
//        toolSubCommands.addCommand("delete", toolCommandOptions.deleteCommandOptions);

//        panelCommandOptions = new PanelCommandOptions(this.commonCommandOptions, jCommander);
//        jCommander.addCommand("panels", panelCommandOptions);
//        JCommander panelSubCommands = jCommander.getCommands().get("panels");
//        panelSubCommands.addCommand("create", panelCommandOptions.createCommandOptions);
//        panelSubCommands.addCommand("info", panelCommandOptions.infoCommandOptions);
//        panelSubCommands.addCommand("acl", panelCommandOptions.aclsCommandOptions);
//        panelSubCommands.addCommand("acl-update", panelCommandOptions.aclsUpdateCommandOptions);


        alignmentCommandOptions = new AlignmentCommandOptions(this.commonCommandOptions, jCommander);
        jCommander.addCommand("alignments", alignmentCommandOptions);
        JCommander alignmentSubCommands = jCommander.getCommands().get("alignments");
        alignmentSubCommands.addCommand("index", alignmentCommandOptions.indexAlignmentCommandOptions);
        alignmentSubCommands.addCommand("query", alignmentCommandOptions.queryAlignmentCommandOptions);
        alignmentSubCommands.addCommand("stats", alignmentCommandOptions.statsAlignmentCommandOptions);
        alignmentSubCommands.addCommand("coverage", alignmentCommandOptions.coverageAlignmentCommandOptions);

        variantCommandOptions = new VariantCommandOptions(this.commonCommandOptions, dataModelOptions, numericOptions, jCommander);
        jCommander.addCommand("variant", variantCommandOptions);
        JCommander variantSubCommands = jCommander.getCommands().get("variant");
        variantSubCommands.addCommand("index", variantCommandOptions.indexVariantCommandOptions);
        variantSubCommands.addCommand("query", variantCommandOptions.queryVariantCommandOptions);

    }


    public void parse(String[] args) throws ParameterException {
        jCommander.parse(args);
    }

    public String getCommand() {
        return (jCommander.getParsedCommand() != null) ? jCommander.getParsedCommand() : "";
    }

    public String getSubCommand() {
        String parsedCommand = jCommander.getParsedCommand();
        if (jCommander.getCommands().containsKey(parsedCommand)) {
            String subCommand = jCommander.getCommands().get(parsedCommand).getParsedCommand();
            return subCommand != null ? subCommand: "";
        } else {
            return null;
        }
    }

    public boolean isHelp() {
        String parsedCommand = jCommander.getParsedCommand();
        if (parsedCommand != null) {
            JCommander jCommander2 = jCommander.getCommands().get(parsedCommand);
            List<Object> objects = jCommander2.getObjects();
            if (!objects.isEmpty() && objects.get(0) instanceof AdminCliOptionsParser.AdminCommonCommandOptions) {
                return ((AdminCliOptionsParser.AdminCommonCommandOptions) objects.get(0)).help;
            }
        }
        return commonCommandOptions.help;
    }


    public void printUsage() {
        String parsedCommand = getCommand();
        if (parsedCommand.isEmpty()) {
            System.err.println("");
            System.err.println("Program:     OpenCGA (OpenCB)");
            System.err.println("Version:     " + GitRepositoryState.get().getBuildVersion());
            System.err.println("Git commit:  " + GitRepositoryState.get().getCommitId());
            System.err.println("Description: Big Data platform for processing and analysing NGS data");
            System.err.println("");
            System.err.println("Usage:       opencga.sh [-h|--help] [--version] <command> [options]");
            System.err.println("");
            printMainUsage();
            System.err.println("");
        } else {
            String parsedSubCommand = getSubCommand();
            if (parsedSubCommand.isEmpty()) {
                System.err.println("");
                System.err.println("Usage:   opencga.sh " + parsedCommand + " <subcommand> [options]");
                System.err.println("");
                System.err.println("Subcommands:");
                printCommands(jCommander.getCommands().get(parsedCommand));
                System.err.println("");
            } else {
                System.err.println("");
                System.err.println("Usage:   opencga.sh " + parsedCommand + " " + parsedSubCommand + " [options]");
                System.err.println("");
                System.err.println("Options:");
                CommandLineUtils.printCommandUsage(jCommander.getCommands().get(parsedCommand).getCommands().get(parsedSubCommand));
                System.err.println("");
            }
        }
    }


    private void printMainUsage() {
        Set<String> analysisCommands = new HashSet<>(Arrays.asList("alignments", "variant"));

        System.err.println("Catalog commands:");
        for (String command : jCommander.getCommands().keySet()) {
            if (!analysisCommands.contains(command)) {
                System.err.printf("%14s  %s\n", command, jCommander.getCommandDescription(command));
            }
        }

        System.err.println("");
        System.err.println("Analysis commands:");
        for (String command : jCommander.getCommands().keySet()) {
            if (analysisCommands.contains(command)) {
                System.err.printf("%14s  %s\n", command, jCommander.getCommandDescription(command));
            }
        }
    }

    private void printCommands(JCommander commander) {
        for (Map.Entry<String, JCommander> entry : commander.getCommands().entrySet()) {
            System.err.printf("%14s  %s\n", entry.getKey(), commander.getCommandDescription(entry.getKey()));
        }
    }

    public GeneralCliOptions.GeneralOptions getGeneralOptions() {
        return generalOptions;
    }

    public GeneralCliOptions.CommonCommandOptions getCommonCommandOptions() {
        return commonCommandOptions;
    }

    public UserCommandOptions getUsersCommandOptions() {
        return usersCommandOptions;
    }

    public ProjectCommandOptions getProjectCommandOptions() {
        return projectCommandOptions;
    }

    public StudyCommandOptions getStudyCommandOptions() {
        return studyCommandOptions;
    }

    public FileCommandOptions getFileCommands() {
        return fileCommandOptions;
    }

    public JobCommandOptions getJobsCommands() {
        return jobCommandOptions;
    }

    public IndividualCommandOptions getIndividualsCommands() {
        return individualCommandOptions;
    }

    public SampleCommandOptions getSampleCommands() {
        return sampleCommandOptions;
    }

    public VariableCommandOptions getVariableCommands() {
        return variableCommandOptions;
    }

    public CohortCommandOptions getCohortCommands() {
        return cohortCommandOptions;
    }

    public PanelCommandOptions getPanelCommands() {
        return panelCommandOptions;
    }

    public ToolCommandOptions getToolCommands() {
        return toolCommandOptions;
    }

    public AlignmentCommandOptions getAlignmentCommands() {
        return alignmentCommandOptions;
    }

    public VariantCommandOptions getVariantCommands() {
        return variantCommandOptions;
    }

}

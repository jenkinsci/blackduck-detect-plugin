/*
 * blackduck-detect
 *
 * Copyright (c) 2024 Black Duck Software, Inc.
 *
 * Use subject to the terms and conditions of the Black Duck Software End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.blackduck.integration.jenkins.detect.extensions.postbuild;

import com.blackduck.integration.jenkins.detect.extensions.DetectDownloadStrategy;
import com.blackduck.integration.jenkins.detect.extensions.InheritFromGlobalDownloadStrategy;
import com.blackduck.integration.jenkins.detect.service.DetectCommandsFactory;
import com.blackduck.integration.jenkins.annotations.HelpMarkdown;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;

public class DetectPostBuildStep extends Recorder {
    public static final String DISPLAY_NAME = "Synopsys Detect";

    @HelpMarkdown("The command line options to pass to Synopsys Detect")
    private final String detectProperties;

    @Nullable
    private DetectDownloadStrategy downloadStrategyOverride;

    @DataBoundConstructor
    public DetectPostBuildStep(String detectProperties) {
        this.detectProperties = detectProperties;
    }

    public String getDetectProperties() {
        return detectProperties;
    }

    public DetectDownloadStrategy getDownloadStrategyOverride() {
        return downloadStrategyOverride;
    }

    @DataBoundSetter
    public void setDownloadStrategyOverride(DetectDownloadStrategy downloadStrategyOverride) {
        this.downloadStrategyOverride = downloadStrategyOverride;
    }

    public DetectDownloadStrategy getDefaultDownloadStrategyOverride() {
        return new InheritFromGlobalDownloadStrategy();
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    // Freestyle
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        DetectCommandsFactory.fromPostBuild(build, launcher, listener)
            .runDetect(detectProperties, downloadStrategyOverride);
        return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> implements Serializable {
        private static final long serialVersionUID = 9059602791947799261L;

        public DescriptorImpl() {
            super(DetectPostBuildStep.class);
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

    }

}

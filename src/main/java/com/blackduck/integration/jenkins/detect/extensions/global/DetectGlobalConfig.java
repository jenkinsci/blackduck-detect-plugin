/*
 * blackduck-detect
 *
 * Copyright (c) 2024 Black Duck Software, Inc.
 *
 * Use subject to the terms and conditions of the Black Duck Software End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.blackduck.integration.jenkins.detect.extensions.global;

import com.blackduck.integration.jenkins.detect.extensions.AirGapDownloadStrategy;
import com.blackduck.integration.jenkins.detect.extensions.DetectDownloadStrategy;
import com.blackduck.integration.jenkins.detect.extensions.ScriptOrJarDownloadStrategy;
import com.blackduck.integration.jenkins.wrapper.BlackduckCredentialsHelper;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.blackduck.integration.blackduck.configuration.BlackDuckServerConfig;
import com.blackduck.integration.blackduck.configuration.BlackDuckServerConfigBuilder;
import com.blackduck.integration.exception.IntegrationException;
import com.blackduck.integration.jenkins.annotations.HelpMarkdown;
import com.blackduck.integration.jenkins.wrapper.JenkinsProxyHelper;
import com.blackduck.integration.jenkins.wrapper.JenkinsWrapper;
import com.blackduck.integration.jenkins.wrapper.BlackduckCredentialsHelper;
import com.blackduck.integration.log.LogLevel;
import com.blackduck.integration.log.PrintStreamIntLogger;
import com.blackduck.integration.rest.proxy.ProxyInfo;
import com.blackduck.integration.rest.response.Response;
import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.IOUtils;
import hudson.util.ListBoxModel;
import hudson.util.Messages;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.util.xml.XMLUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.EnglishReasonPhraseCatalog;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.verb.POST;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

@Extension
public class DetectGlobalConfig extends GlobalConfiguration implements Serializable {
    private static final long serialVersionUID = -7629542889827231313L;

    @HelpMarkdown("Provide the URL that lets you access your Black Duck server.")
    private String blackDuckUrl;

    @HelpMarkdown("Choose the saved API Token from the list to authenticate to the Black Duck server.  \r\n" +
        "If the saved secret text containing your API Token is not in the list, you can add it with the Add button.\r\n\n" +
        "As of Detect 7.0.0, an API Token saved as secret text is the only supported means for authentication.  \r\n" +
        "Username with password is no longer supported.")
    private String blackDuckCredentialsId;

    @HelpMarkdown("If selected, Detect will automatically trust certificates when communicating with your Black Duck server.")
    private boolean trustBlackDuckCertificates;

    private int blackDuckTimeout = 120;

    @Nullable
    private DetectDownloadStrategy downloadStrategy;

    @DataBoundConstructor
    public DetectGlobalConfig() {
        load();
    }

    public String getBlackDuckUrl() {
        return blackDuckUrl;
    }

    @DataBoundSetter
    public void setBlackDuckUrl(String blackDuckUrl) {
        this.blackDuckUrl = blackDuckUrl;
        save();
    }

    public int getBlackDuckTimeout() {
        return blackDuckTimeout;
    }

    @DataBoundSetter
    public void setBlackDuckTimeout(int blackDuckTimeout) {
        this.blackDuckTimeout = blackDuckTimeout;
        save();
    }

    public String getBlackDuckCredentialsId() {
        return blackDuckCredentialsId;
    }

    @DataBoundSetter
    public void setBlackDuckCredentialsId(String blackDuckCredentialsId) {
        this.blackDuckCredentialsId = blackDuckCredentialsId;
        save();
    }

    public boolean getTrustBlackDuckCertificates() {
        return trustBlackDuckCertificates;
    }

    @DataBoundSetter
    public void setTrustBlackDuckCertificates(boolean trustBlackDuckCertificates) {
        this.trustBlackDuckCertificates = trustBlackDuckCertificates;
        save();
    }

    public DetectDownloadStrategy getDownloadStrategy() {
        return downloadStrategy;
    }

    @DataBoundSetter
    public void setDownloadStrategy(DetectDownloadStrategy downloadStrategy) {
        this.downloadStrategy = downloadStrategy;
    }

    public DetectDownloadStrategy getDefaultDownloadStrategy() {
        return new ScriptOrJarDownloadStrategy();
    }

    public Collection<Descriptor<DetectDownloadStrategy>> getAllowedDownloadStrategyDescriptors() {
        Jenkins jenkins = Jenkins.get();
        return Arrays.asList(jenkins.getDescriptor(AirGapDownloadStrategy.class), jenkins.getDescriptor(ScriptOrJarDownloadStrategy.class));
    }

    public BlackDuckServerConfig getBlackDuckServerConfig(JenkinsProxyHelper jenkinsProxyHelper, BlackduckCredentialsHelper blackduckCredentialsHelper) {
        return getBlackDuckServerConfigBuilder(jenkinsProxyHelper, blackduckCredentialsHelper).build();
    }

    public BlackDuckServerConfigBuilder getBlackDuckServerConfigBuilder(JenkinsProxyHelper jenkinsProxyHelper, BlackduckCredentialsHelper blackduckCredentialsHelper) {
        return createBlackDuckServerConfigBuilder(
            jenkinsProxyHelper,
            blackduckCredentialsHelper,
            blackDuckUrl,
            blackDuckCredentialsId,
            blackDuckTimeout,
            trustBlackDuckCertificates
        );
    }

    public ListBoxModel doFillBlackDuckCredentialsIdItems() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return new StandardListBoxModel().includeEmptyValue();
        }
        jenkins.checkPermission(Jenkins.ADMINISTER);
        return new StandardListBoxModel()
            .includeEmptyValue()
            .includeMatchingAs(ACL.SYSTEM, jenkins, BaseStandardCredentials.class, Collections.emptyList(), BlackduckCredentialsHelper.API_TOKEN_CREDENTIALS);
    }

    @POST
    public FormValidation doTestBlackDuckConnection(
        @QueryParameter("blackDuckUrl") String blackDuckUrl,
        @QueryParameter("blackDuckCredentialsId") String blackDuckCredentialsId,
        @QueryParameter("blackDuckTimeout") String blackDuckTimeout,
        @QueryParameter("trustBlackDuckCertificates") boolean trustBlackDuckCertificates
    ) {
        JenkinsWrapper jenkinsWrapper = JenkinsWrapper.initializeFromJenkinsJVM();
        if (!jenkinsWrapper.getJenkins().isPresent()) {
            return FormValidation.warning(
                "Connection validation could not be completed: Validation couldn't retrieve the instance of Jenkins from the JVM. This may happen if Jenkins is still starting up or if this code is running on a different JVM than your Jenkins server.");
        }
        jenkinsWrapper.getJenkins().get().checkPermission(Jenkins.ADMINISTER);

        BlackduckCredentialsHelper blackduckCredentialsHelper = jenkinsWrapper.getCredentialsHelper();
        JenkinsProxyHelper jenkinsProxyHelper = jenkinsWrapper.getProxyHelper();

        try {
            BlackDuckServerConfig blackDuckServerConfig = createBlackDuckServerConfigBuilder(
                jenkinsProxyHelper,
                blackduckCredentialsHelper,
                blackDuckUrl,
                blackDuckCredentialsId,
                Integer.parseInt(blackDuckTimeout),
                trustBlackDuckCertificates
            ).build();
            Response response = blackDuckServerConfig.createBlackDuckHttpClient(new PrintStreamIntLogger(System.out, LogLevel.DEBUG)).attemptAuthentication();

            if (response.isStatusCodeError()) {
                int statusCode = response.getStatusCode();
                String validationMessage = determineValidationMessage(statusCode);

                // This is how Jenkins constructs an error with an exception stack trace, we're using it here because often a status code and phrase are not enough, but also (especially with proxies) the failure message can be too much.
                String moreDetailsHtml = Optional.ofNullable(response.getContentString())
                    .map(Util::escape)
                    .map(msg -> String.format("<a href='#' class='showDetails'>%s</a><pre style='display:none'>%s</pre>", Messages.FormValidation_Error_Details(), msg))
                    .orElse(StringUtils.EMPTY);

                return FormValidation.errorWithMarkup(String.join(" ", validationMessage, moreDetailsHtml));
            }
        } catch (IllegalArgumentException | IntegrationException e) {
            return FormValidation.error(e.getMessage());
        }

        return FormValidation.ok("Connection successful.");
    }

    private String determineValidationMessage(int statusCode) {
        String validationMessage;
        try {
            String statusPhrase = EnglishReasonPhraseCatalog.INSTANCE.getReason(statusCode, Locale.ENGLISH);
            validationMessage = String.format("ERROR: Connection attempt returned %s %s", statusCode, statusPhrase);
        } catch (IllegalArgumentException ignored) {
            // EnglishReasonPhraseCatalog throws an IllegalArgumentException if the status code is outside of the 100-600 range --rotte AUG 2020
            validationMessage = "ERROR: Connection could not be established.";
        }
        return validationMessage;
    }

    // EX: http://localhost:8080/descriptorByName/com.synopsys.integration.jenkins.detect.extensions.global.DetectGlobalConfig/config.xml
    @WebMethod(name = "config.xml")
    public void doConfigDotXml(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, ParserConfigurationException {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        boolean changed = false;
        try {
            if (this.getClass().getClassLoader() != originalClassLoader) {
                changed = true;
                Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
            }

            Functions.checkPermission(Jenkins.ADMINISTER);
            if (req.getMethod().equals("GET")) {
                // read
                rsp.setContentType("application/xml");
                IOUtils.copy(getConfigFile().getFile(), rsp.getOutputStream());
                return;
            }
            Functions.checkPermission(Jenkins.ADMINISTER);
            if (req.getMethod().equals("POST")) {
                // submission
                updateByXml(new StreamSource(req.getReader()));
                return;
            }
            // huh?
            rsp.sendError(javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST);
        } finally {
            if (changed) {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }
        }
    }

    private void updateByXml(Source source) throws IOException, ParserConfigurationException {
        Document doc;
        try (StringWriter out = new StringWriter()) {
            // this allows us to use UTF-8 for storing data,
            // plus it checks any well-formedness issue in the submitted
            // data
            XMLUtils.safeTransform(source, new StreamResult(out));

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource is = new InputSource(new StringReader(out.toString()));

            doc = builder.parse(is);
        } catch (TransformerException | SAXException e) {
            throw new IOException("Failed to persist configuration.xml", e);
        }

        String url = getNodeValue(doc, "blackDuckUrl").orElse(StringUtils.EMPTY);
        String credentialsId = getNodeValue(doc, "blackDuckCredentialsId").orElse(StringUtils.EMPTY);
        int timeout = getNodeIntegerValue(doc, "blackDuckTimeout").orElse(120);
        boolean trustCerts = getNodeBooleanValue(doc, "trustBlackDuckCertificates").orElse(false);

        setBlackDuckUrl(url);
        setBlackDuckCredentialsId(credentialsId);
        setBlackDuckTimeout(timeout);
        setTrustBlackDuckCertificates(trustCerts);
        save();
    }

    private Optional<String> getNodeValue(Document doc, String tagName) {
        return Optional.ofNullable(doc.getElementsByTagName(tagName).item(0))
            .map(Node::getFirstChild)
            .map(Node::getNodeValue)
            .map(String::trim);
    }

    private Optional<Integer> getNodeIntegerValue(Document doc, String tagName) {
        try {
            return getNodeValue(doc, tagName).map(Integer::valueOf);
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Boolean> getNodeBooleanValue(Document doc, String tagName) {
        return getNodeValue(doc, tagName).map(Boolean::valueOf);
    }

    private BlackDuckServerConfigBuilder createBlackDuckServerConfigBuilder(
        JenkinsProxyHelper jenkinsProxyHelper, BlackduckCredentialsHelper blackduckCredentialsHelper,
        String blackDuckUrl, String credentialsId, int timeout, boolean alwaysTrust
    ) {
        ProxyInfo proxyInfo = jenkinsProxyHelper.getProxyInfo(blackDuckUrl);
        String apiToken = blackduckCredentialsHelper.getApiTokenByCredentialsId(credentialsId).orElse(null);

        return BlackDuckServerConfig.newApiTokenBuilder()
            .setUrl(blackDuckUrl)
            .setTimeoutInSeconds(timeout)
            .setTrustCert(alwaysTrust)
            .setProxyInfo(proxyInfo)
            .setApiToken(apiToken);
    }

}

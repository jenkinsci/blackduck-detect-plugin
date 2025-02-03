import groovy.xml.MarkupBuilder
import groovy.xml.XmlUtil
import hudson.diagnosis.OldDataMonitor;
import hudson.util.VersionNumber;

start = System.currentTimeMillis()

jenkins = Jenkins.getInstance()
plugin = jenkins.getPluginManager().getPlugins().find { it.getShortName() == 'blackduck-detect' }

// MIGRATE GLOBAL DETECT PLUGIN SETTINGS 
synopsysGlobalConfigXmlPath = new FilePath(jenkins.getRootPath(), 'com.synopsys.integration.jenkins.detect.extensions.global.DetectGlobalConfig.xml')
blackduckGlobalConfigXmlPath = new FilePath(jenkins.getRootPath(), 'com.blackduck.integration.jenkins.detect.extensions.global.DetectGlobalConfig.xml')

if (synopsysGlobalConfigXmlPath && synopsysGlobalConfigXmlPath.exists()) {
    println('Found existing Synopsys Detect global configuration.')
    println('Attempting to migrate Synopsys Detect global configuration to Black Duck Detect global configuration... ')
    try {
        synopsysGlobalConfig = new XmlSlurper().parse(synopsysGlobalConfigXmlPath.read())

        detectGlobalConfig = jenkins.model.GlobalConfiguration.all().get(com.blackduck.integration.jenkins.detect.extensions.global.DetectGlobalConfig.class)

        // MIGRATE GLOBAL AIRGAP SETTINGS
        // Find download strategy
        synopsysDetectGlobalDownloadStrategyClass = synopsysGlobalConfig.downloadStrategy.@class.text()
        if (synopsysDetectGlobalDownloadStrategyClass) {
            println("Attempting to migrate global download strategy: " + synopsysDetectGlobalDownloadStrategyClass)
            // Instantiate the appropriate DetectDownloadStrategy
            def downloadStrategyInstance
            if (synopsysDetectGlobalDownloadStrategyClass == 'com.synopsys.integration.jenkins.detect.extensions.AirGapDownloadStrategy') {
                downloadStrategyInstance = new com.blackduck.integration.jenkins.detect.extensions.AirGapDownloadStrategy()
                downloadStrategyInstance.airGapInstallationName = synopsysGlobalConfig.downloadStrategy.airGapInstallationName
            } else if (synopsysDetectGlobalDownloadStrategyClass == 'com.synopsys.integration.jenkins.detect.extensions.ScriptOrJarDownloadStrategy') {
                downloadStrategyInstance = new com.blackduck.integration.jenkins.detect.extensions.ScriptOrJarDownloadStrategy()
            }

            if (downloadStrategyInstance != null) {
                detectGlobalConfig.downloadStrategy = downloadStrategyInstance
                println("Migrated download strategy.")
            }
        }

        detectGlobalConfig.setBlackDuckUrl(synopsysGlobalConfig.blackDuckUrl.text())
        detectGlobalConfig.setBlackDuckCredentialsId(synopsysGlobalConfig.blackDuckCredentialsId.text())
        detectGlobalConfig.setBlackDuckTimeout(Integer.valueOf(synopsysGlobalConfig.blackDuckTimeout.text()))
        detectGlobalConfig.setTrustBlackDuckCertificates(Boolean.valueOf(synopsysGlobalConfig.trustBlackDuckCertificates.text()))
        print('Migrated Detect Jenkins Plugin global configuration successfully.')
    } catch (Exception e) {
        println("Detect Jenkins Plugin global configuration migration failed because ${e.getMessage()}.")
        // Uncomment the following line to debug
        // e.printStackTrace()
        return
    }
    println('')
}

// MIGRATE AIRGAP CONFIGURATIONS
synopsysAirGapInstallations = new FilePath(jenkins.getRootPath(), 'com.synopsys.integration.jenkins.detect.extensions.tool.DetectAirGapInstallation.xml')
blackdDuckAirGapInstallations = new FilePath(jenkins.getRootPath(),'com.blackduck.integration.jenkins.detect.extensions.tool.DetectAirGapInstallation.xml')

println('Found existing Synopsys Detect airgap configuration.')
println('Attempting to migrate Synopsys Detect air gap configuration to Black Duck Detect air gap configuration... ')
try {

    def xmlContent = new XmlSlurper().parse(synopsysAirGapInstallations.read())

    xmlContent.replaceNode {
        'com.blackduck.integration.jenkins.detect.extensions.tool.DetectAirGapInstallation_-DescriptorImpl' (it.attributes(), it.children())
    }

    xmlContent.installations.'com.synopsys.integration.jenkins.detect.extensions.tool.DetectAirGapInstallation'.replaceNode {
        'com.blackduck.integration.jenkins.detect.extensions.tool.DetectAirGapInstallation' (it.attributes(), it.children())
    }

    xmlContent.installations[0].@class = 'com.blackduck.integration.jenkins.detect.extensions.tool.DetectAirGapInstallation-array'
    xmlContent.@plugin = 'blackduck-detect@10.0.0'


    def newBlackDuckAirGapFile = new File(blackdDuckAirGapInstallations.getRemote())
    def writer = new FileWriter(newBlackDuckAirGapFile)
    XmlUtil.serialize(xmlContent, writer)

    println('Migrated Detect Jenkins Plugin air gap configuration successfully.')
} catch (Exception e) {
    println("Detect Jenkins Plugin air gap configuration migration failed because ${e.getMessage()}.")
    // Uncomment the following line to debug
    // e.printStackTrace()
    return
}

// MIGRATE FREESTYLE JOB CONFIGURATION(S)
oldDataMonitor = OldDataMonitor.get(jenkins); // Tracks whether any data structure changes were corrected when loading XML, that could be resaved to migrate that data to the new format.
items = null
if (oldDataMonitor != null && oldDataMonitor.isActivated()) {
    // If possible, we use the OldDataMonitor so we don't have to iterate through all items (jobs, views, etc.)
    items = oldDataMonitor.getData().keySet()
} else {
    // But if that's not available, we fall back to iterating through all items
    items = jenkins.getItems()
}

// If performance is an issue, you can comment this line out-- this is just to make the migration output prettier
items = items.sort{it.getFullName()}

for (item in items) {
    // Items can be many things-- only FreeStyle jobs are migratable
    if (item instanceof FreeStyleProject) {
        configXml = item.getConfigFile().getFile();
        // Get existing Detect post-build configuration for this FreeStyle job
        synopsysDetectConfig = new XmlSlurper()
                .parse(configXml)
                .'**'
                .find { it.name() == 'com.synopsys.integration.jenkins.detect.extensions.postbuild.DetectPostBuildStep' }

        if (synopsysDetectConfig) {
            println("Attempting to migrate ${item.getFullName()}... ")
            try {
                detectPropertiesToMigrate = synopsysDetectConfig.detectProperties.text()
                blackDuckDetectConfig = new com.blackduck.integration.jenkins.detect.extensions.postbuild.DetectPostBuildStep(detectPropertiesToMigrate)
                println("Migrated Detect properties.")

                synopsysDetectDownloadStrategyClass = synopsysDetectConfig.downloadStrategyOverride.@class.text()
                if (synopsysDetectDownloadStrategyClass) {
                    println("Attempting to migrate download strategy: " + synopsysDetectDownloadStrategyClass)
                    // Instantiate the appropriate DetectDownloadStrategy
                    def downloadStrategyInstance
                    if (synopsysDetectDownloadStrategyClass == 'com.synopsys.integration.jenkins.detect.extensions.AirGapDownloadStrategy') {
                        downloadStrategyInstance = new com.blackduck.integration.jenkins.detect.extensions.AirGapDownloadStrategy()
                        downloadStrategyInstance.airGapInstallationName = synopsysDetectConfig.downloadStrategyOverride.airGapInstallationName
                    } else if (synopsysDetectDownloadStrategyClass == 'com.synopsys.integration.jenkins.detect.extensions.ScriptOrJarDownloadStrategy') {
                        downloadStrategyInstance = new com.blackduck.integration.jenkins.detect.extensions.ScriptOrJarDownloadStrategy()
                    } else if (synopsysDetectDownloadStrategyClass == 'com.synopsys.integration.jenkins.detect.extensions.InheritFromGlobalDownloadStrategy') {
                        downloadStrategyInstance = new com.blackduck.integration.jenkins.detect.extensions.InheritFromGlobalDownloadStrategy()
                    }

                    if (downloadStrategyInstance != null) {
                        blackDuckDetectConfig.downloadStrategyOverride = downloadStrategyInstance
                        println("Migrated download strategy.")
                    }
                }

                item.publishersList.add(blackDuckDetectConfig)
                item.save()
                println('Migrated FreeStyle job configuration successfully.')
            } catch (Exception e) {
                println("Synopsys FreeStyle job configuration migration failed because ${e.getMessage()}.")
                // Uncomment the following line to debug
                // e.getStackTrace().each { println(it.toString() + "\r\n") }
                return
            }
            println("\r\n")
        }
        else {
            println("Did not find any Synopsys FreeStyle job configurations to migrate.")
        }
    }
}

end = System.currentTimeMillis()
println("Migrated in ${end-start}ms")

import hudson.diagnosis.OldDataMonitor;
import hudson.util.VersionNumber;

start = System.currentTimeMillis()

jenkins = Jenkins.getInstance()
plugin = jenkins.getPluginManager().getPlugins().find { it.getShortName() == 'blackduck-detect' }

if (plugin == null || !plugin.isActive() || plugin.isOlderThan(new VersionNumber('10.0.0'))) {
    System.err.println('Version 10.0.0 of Black Duck Detect Jenkins Plugin is either not installed or not activated.')
    System.err.println('Please install and activate Black Duck Detect Jenkins Plugin version 10.0.0 before running this migration script.')
    return
}

// MIGRATE GLOBAL DETECT PLUGIN SETTINGS 
synopsysGlobalConfigXmlPath = new FilePath(jenkins.getRootPath(), 'com.synopsys.integration.jenkins.detect.extensions.global.DetectGlobalConfig.xml')
blackduckGlobalConfigXmlPath = new FilePath(jenkins.getRootPath(), 'com.blackduck.integration.jenkins.detect.extensions.global.DetectGlobalConfig.xml')

if (synopsysGlobalConfigXmlPath && synopsysGlobalConfigXmlPath.exists()) {
    println('Found existing Synopsys Detect global configuration.')
    println('Attempting to migrate Synopsys Detect global configuration to Black Duck Detect global configuration... ')
    try {
        synopsysGlobalConfig = new XmlSlurper().parse(synopsysGlobalConfigXmlPath.read())

        detectGlobalConfig = jenkins.model.GlobalConfiguration.all().get(com.blackduck.integration.jenkins.detect.extensions.global.DetectGlobalConfig.class)

        detectGlobalConfig.setBlackDuckUrl(synopsysGlobalConfig.blackDuckUrl.text())
        detectGlobalConfig.setBlackDuckCredentialsId(synopsysGlobalConfig.blackDuckCredentialsId.text())
        detectGlobalConfig.setBlackDuckTimeout(Integer.valueOf(synopsysGlobalConfig.blackDuckTimeout.text()))
        detectGlobalConfig.setTrustBlackDuckCertificates(Boolean.valueOf(synopsysGlobalConfig.trustBlackDuckCertificates.text()))
        synopsysGlobalConfigXmlPath.delete()
        print('Migrated Detect Jenkins Plugin global configuration successfully.')
    } catch (Exception e) {
        System.err.print("Detect Jenkins Plugin global configuration migration failed because ${e.getMessage()}.")
        // Uncomment the following line to debug
        // e.printStackTrace()
        return
    }
    println('')
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

builder = new StringBuilder()
for (item in items) {
    // Items can be many things-- only FreeStyle jobs are migratable
    if (item instanceof FreeStyleProject) {
        configXml = item.getConfigFile().getFile();
        synopsysDetectConfig = new XmlSlurper()
                .parse(configXml)
                .'**'
                .find { it.name() == 'com.synopsys.integration.jenkins.detect.extensions.postbuild.DetectPostBuildStep' }

        if (synopsysDetectConfig) {
            builder.append("Attempting to migrate ${item.getFullName()}... ")
            try {
                detectPropertiesToMigrate = synopsysDetectConfig.detectProperties.text()
                blackDuckDetectConfig = new com.blackduck.integration.jenkins.detect.extensions.postbuild.DetectPostBuildStep(detectPropertiesToMigrate)
                item.publishersList.add(blackDuckDetectConfig)
                item.save()
                builder.append('Migrated FreeStyle job configuration successfully.')
            } catch (Exception e) {
                builder.append("Synopsys FreeStyle job configuration migration failed because ${e.getMessage()}.")
                // Uncomment the following line to debug
                // e.getStackTrace().each { builder.append(it.toString() + "\r\n") }
                return
            }
            builder.append("\r\n")
        }
        else {
            println("Did not find any Synopsys FreeStyle job configurations to migrate.")
        }
    }
}
println(builder.toString())

end = System.currentTimeMillis()
println("Migrated in ${end-start}ms")

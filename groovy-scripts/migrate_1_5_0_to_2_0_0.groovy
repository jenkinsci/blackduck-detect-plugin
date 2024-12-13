import hudson.diagnosis.OldDataMonitor;
import hudson.util.VersionNumber;

start = System.currentTimeMillis()

jenkins = Jenkins.getInstance()
plugin = jenkins.getPluginManager().getPlugins().find { it.getShortName() == 'blackduck-detect' }

if (plugin == null || !plugin.isActive() || plugin.isOlderThan(new VersionNumber('2.0.0'))) {
  System.err.println('Version 2.0.0 or later of Synopsys Detect for Jenkins is either not installed or not activated.')
  System.err.println('Please upgrade and activate version 2.0.0 or later before running this script.')
  return
}

oldGlobalConfigXmlPath = new FilePath(jenkins.getRootPath(), 'com.blackducksoftware.integration.detect.jenkins.post.DetectPostBuildStep.xml')

if (oldGlobalConfigXmlPath && oldGlobalConfigXmlPath.exists()) {
    print('Attempting to migrate Synopsys Detect global config... ')
    try {
        oldGlobalConfig = new XmlSlurper()
        						.parse(oldGlobalConfigXmlPath.read())

        detectGlobalConfig = com.synopsys.integration.jenkins.detect.PluginHelper.getDetectGlobalConfig()
        detectGlobalConfig.setBlackDuckUrl(oldGlobalConfig.hubUrl.text())
        detectGlobalConfig.setBlackDuckCredentialsId(oldGlobalConfig.hubCredentialsId.text())
        detectGlobalConfig.setBlackDuckTimeout(Integer.valueOf(oldGlobalConfig.hubTimeout.text()))
        detectGlobalConfig.setTrustBlackDuckCertificates(Boolean.valueOf(oldGlobalConfig.trustSSLCertificates.text()))
        oldGlobalConfigXmlPath.delete()
        print('migrated successfully.')
    } catch (Exception e) {
        System.err.print("migration failed because ${e.getMessage()}.")
        // Uncomment the following line to debug
        // e.printStackTrace()
    }
    println('')
}

oldDataMonitor = OldDataMonitor.get(jenkins);
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
      oldDetectConfig = new XmlSlurper()
                        .parse(configXml)
                        .'**'
                        .find { it.name() == 'com.blackducksoftware.integration.detect.jenkins.post.DetectPostBuildStep' }

      if (oldDetectConfig) {
        builder.append("Attempting to migrate ${item.getFullName()}... ")
        try {
            detectPropertiesToMigrate = oldDetectConfig.detectProperties.text()
            newDetectConfig = new com.synopsys.integration.jenkins.detect.extensions.postbuild.DetectPostBuildStep(detectPropertiesToMigrate)
            item.publishersList.add(newDetectConfig)
            item.save()
            builder.append('migrated successfully.')
        } catch (Exception e) {
            builder.append("migration failed because ${e.getMessage()}.")
            // Uncomment the following line to debug
            // e.getStackTrace().each { builder.append(it.toString() + "\r\n") }
        }
        builder.append("\r\n")
    }
  }
}
println(builder.toString())

end = System.currentTimeMillis()
println("Migrated in ${end-start}ms")

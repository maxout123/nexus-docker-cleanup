//import org.sonatype.nexus.common.app.GlobalComponentLookupHelper
import org.sonatype.nexus.repository.maintenance.MaintenanceService
//import org.sonatype.nexus.repository.storage.ComponentMaintenance
import org.sonatype.nexus.repository.storage.Query
import org.sonatype.nexus.repository.storage.StorageFacet
import org.sonatype.nexus.repository.Repository
//import org.sonatype.nexus.script.plugin.RepositoryApi
//import org.sonatype.nexus.script.plugin.internal.provisioning.RepositoryApiImpl
import com.google.common.collect.ImmutableList
import org.joda.time.DateTime
import java.util.regex.Pattern
//import org.slf4j.Logger

// ----------------------------------------------------
// delete these rows when this script is added to nexus
/* RepositoryApiImpl repository = null;
Logger log = null;
GlobalComponentLookupHelper container = null; */
// ----------------------------------------------------

log.info(":::Cleanup script started!")

repository.repositoryManager.browse().each { Repository myRepo ->
    def retentionDays = 5
    def retentionCount = 3
    def pattern = ~/^([a-zA-Z]+)_/
    def patternSemver = ~/^\d+\.\d+\.\d+-([a-zA-Z0-9-]+)/
    def whitelist = ["org.javaee7.sample/javaee7-simple-sample", "org.javaee7.next/javaee7-another-sample"].toArray()

    //log.info("Repository: $myRepo");
    def repositoryName = myRepo.name
    if (myRepo.getFormat().toString() != 'docker') return

    log.info("*** Proceeding with repository: $repositoryName")

    def alterRetention = [:]

    alterRetention['etr_s7middleware_docker'] = [retentionCount: 30]
    //alterRetention['etr_ncenter_docker'] = [pattern: ~/^\d+\.\d+\.\d+-([a-zA-Z0-9-]+)/]
    //alterRetention['etr_s7common_docker'] = [retentionCount: 30]

    if(alterRetention.containsKey(repositoryName)){
        def alter = alterRetention[repositoryName] as Map
        log.info("Altering retention params: ${alter}")
        if(alter.containsKey('retentionDays')){
            retentionDays = alter['retentionDays'] as Integer
        }
        if(alter.containsKey('retentionCount')){
            retentionCount = alter['retentionCount'] as Integer
        }
        if(alter.containsKey('pattern')){
            pattern = alter['pattern'] as Pattern
        }
    }

    MaintenanceService service = container.lookup("org.sonatype.nexus.repository.maintenance.MaintenanceService") as MaintenanceService
    def repo = repository.repositoryManager.get(repositoryName)
    def tx = repo.facet(StorageFacet.class).txSupplier().get()
    def components = null
    try {
        tx.begin()
        //components = ImmutableList.copyOf(tx.browseComponents(tx.findBucket(repo)))
        components = ImmutableList.copyOf(tx.findComponents(Query.builder().suffix(' ORDER BY name ASC, last_updated ASC').build(), [repo]))
    }catch(Exception e){
        log.info("Error: "+e)
    }finally{
        if(tx!=null)
            tx.close()
    }

    if(components != null && components.size() > 0) {
        //log.info("${components}");
        //noinspection GrDeprecatedAPIUsage
        def retentionDate = DateTime.now().minusDays(retentionDays).dayOfMonth().roundFloorCopy()
        int deletedComponentCount = 0
        int compCount = 0
        def listOfComponents = components
        def tagCount = [:]

        def previousComp = listOfComponents.head().group() + listOfComponents.head().name()

        listOfComponents.reverseEach{comp ->
            //log.info("Processing Component - group: ${comp.group()}, ${comp.name()}, version: ${comp.version()}")
            if(!whitelist.contains(comp.group()+"/"+comp.name())){
                //log.info("group: ${comp.group()}, ${comp.name()}, version: ${comp.version()}")
                //log.info("previous: ${previousComp}");

                if(previousComp != (comp.group() + comp.name())) {
                    compCount = 0
                    tagCount = [:]
                    previousComp = comp.group() + comp.name()
                    log.info("group: ${comp.group()}, ${comp.name()}")
                }

                if(comp.version() == 'latest'){
                    log.info("    version: ${comp.version()}, skipping")
                    return
                }

                def prefix = null
                def matcher = comp.version() =~ pattern
                if(matcher) {
                    prefix = matcher.group(1)
                } else{
                    matcher = comp.version() =~ patternSemver
                    if(matcher) {
                        prefix = matcher.group(1)
                    }
                }
                def actualCount
                if(prefix != null) {
                    if(tagCount[prefix] == null) {
                        tagCount[prefix] = 0
                    }
                    tagCount[prefix]++
                    actualCount = tagCount[prefix]
                    log.info("    version: ${comp.version()}, prefix: ${prefix}")
                } else {
                    compCount++
                    actualCount = compCount
                    log.info("    version: ${comp.version()}")
                }
                log.info("    CompCount: ${actualCount}, ReteCount: ${retentionCount}")
                if(actualCount > retentionCount) {
                    //log.info("        CompDate: ${comp.lastUpdated()} RetDate: ${retentionDate}")
                    if (comp.lastUpdated().isBefore(retentionDate)) {
                        //log.info("compDate after retentionDate: ${comp.lastUpdated()} isAfter ${retentionDate}")
                        //log.info("deleting ${comp.group()}, ${comp.name()}, version: ${comp.version()}")
                        log.info("        !!!Deleting")

                        // ------------------------------------------------
                        // uncomment to delete components and their assets
                        //service.deleteComponent(repo, comp)
                        // ------------------------------------------------

                        //log.info("component deleted");
                        deletedComponentCount++
                    }
                }
            }else{
                log.info("Component skipped: ${comp.group()} ${comp.name()}")
            }
        }

        log.info("Deleted Component count: ${deletedComponentCount}")
    }

}


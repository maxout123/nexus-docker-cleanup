# nexus-docker-cleanup
Smart cleanup of docker images in Sonatype Nexus OSS Repository

This script keeps last `retentionCount` versions of each build type of each docker image, while doesn't touch versions younger than `retentionDays` at all.

Build type (i.e. dev, stage, prod) is determined from image tag with use of `pattern`.

If build type is not found in image tag, script tries to interpret the tag with **SemVer** notation and extract build type from SemVer label.

+ Add this script to Nexus Tasks with type "Admin - Execute script".
+ Set `retentionDays`, `retentionCount`, `whitelist` params.
+ Set `pattern` and `patternSemver` params if appropriate.
+ Alter retentions params for specific repos with `alterRetention`.

Uncomment `service.deleteComponent` line to proceed with deletion.

Don't forget to execute "Docker - Delete unused manifests and images" and "Admin - Compact blob store" tasks after the script.

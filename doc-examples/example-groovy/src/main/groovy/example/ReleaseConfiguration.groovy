package example

import com.fasterxml.jackson.annotation.JsonMerge
import io.micronaut.serde.annotation.Serdeable

@Serdeable
class ReleaseConfiguration {
    String service = ""
    String owner = ""
    @JsonMerge
    DeploymentWindow deploymentWindow = new DeploymentWindow()
    @JsonMerge
    Map<String, String> labels = new LinkedHashMap<>()

    @Serdeable
    static class DeploymentWindow {
        String day = ""
        String timeZone = ""
    }
}

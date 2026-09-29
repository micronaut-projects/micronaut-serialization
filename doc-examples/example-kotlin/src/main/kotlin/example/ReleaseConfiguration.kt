package example

import com.fasterxml.jackson.annotation.JsonMerge
import io.micronaut.serde.annotation.Serdeable

@Serdeable
class ReleaseConfiguration {
    var service: String = ""
    var owner: String = ""
    @JsonMerge
    var deploymentWindow: DeploymentWindow = DeploymentWindow()
    @JsonMerge
    var labels: MutableMap<String, String> = LinkedHashMap()

    @Serdeable
    class DeploymentWindow {
        var day: String = ""
        var timeZone: String = ""
    }
}

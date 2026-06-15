package example;

import com.fasterxml.jackson.annotation.JsonMerge;
import io.micronaut.serde.annotation.Serdeable;

import java.util.LinkedHashMap;
import java.util.Map;

@Serdeable
public class ReleaseConfiguration {
    private String service = "";
    private String owner = "";
    @JsonMerge
    private DeploymentWindow deploymentWindow = new DeploymentWindow();
    @JsonMerge
    private Map<String, String> labels = new LinkedHashMap<>();

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public DeploymentWindow getDeploymentWindow() {
        return deploymentWindow;
    }

    public void setDeploymentWindow(DeploymentWindow deploymentWindow) {
        this.deploymentWindow = deploymentWindow;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    @Serdeable
    public static class DeploymentWindow {
        private String day = "";
        private String timeZone = "";

        public String getDay() {
            return day;
        }

        public void setDay(String day) {
            this.day = day;
        }

        public String getTimeZone() {
            return timeZone;
        }

        public void setTimeZone(String timeZone) {
            this.timeZone = timeZone;
        }
    }
}

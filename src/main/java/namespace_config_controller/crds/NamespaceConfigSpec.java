package namespace_config_controller.crds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;

import java.util.Map;

/**
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = JsonDeserializer.None.class)
public class NamespaceConfigSpec implements KubernetesResource {

    private boolean multicast;
    private String owner;
    private String email;
    private String description;
    private String slack;

    private Map<String, String> extraAnnotations;
    private Map<String, String> extraLabels;

    public boolean isMulticast() {
        return multicast;
    }

    public void setMulticast(boolean multicast) {
        this.multicast = multicast;
    }

    public Map<String, String> getExtraAnnotations() {
        return extraAnnotations;
    }

    public void setExtraAnnotations(Map<String, String> extraAnnotations) {
        this.extraAnnotations = extraAnnotations;
    }

    public Map<String, String> getExtraLabels() {
        return extraLabels;
    }

    public void setExtraLabels(Map<String, String> extraLabels) {
        this.extraLabels = extraLabels;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSlack() {
        return slack;
    }

    public void setSlack(String slack) {
        this.slack = slack;
    }

    @Override
    public String toString() {
        return "NamespaceConfigSpec{" +
                "multicast=" + multicast +
                ", owner='" + owner + '\'' +
                ", email='" + email + '\'' +
                ", description='" + description + '\'' +
                ", slack='" + slack + '\'' +
                ", extraAnnotations=" + extraAnnotations +
                ", extraLabels=" + extraLabels +
                '}';
    }
}

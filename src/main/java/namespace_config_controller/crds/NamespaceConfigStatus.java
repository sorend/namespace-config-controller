package namespace_config_controller.crds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = JsonDeserializer.None.class)
public class NamespaceConfigStatus implements KubernetesResource {
    private long observedGeneration;
    private NamespaceConfigPhase phase;
    private String message;

    public long getObservedGeneration() {
        return observedGeneration;
    }

    public void setObservedGeneration(long observedGeneration) {
        this.observedGeneration = observedGeneration;
    }

    public NamespaceConfigPhase getPhase() {
        return phase;
    }

    public void setPhase(NamespaceConfigPhase phase) {
        this.phase = phase;
    }


    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "NamespaceConfigStatus{" +
                "observedGeneration=" + observedGeneration +
                ", phase=" + phase +
                ", message='" + message + '\'' +
                '}';
    }
}

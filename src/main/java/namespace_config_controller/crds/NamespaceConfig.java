package namespace_config_controller.crds;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group(NamespaceConfig.GROUP)
@Version(NamespaceConfig.VERSION)
public class NamespaceConfig extends CustomResource<NamespaceConfigSpec, NamespaceConfigStatus> implements Namespaced {
    public static final String GROUP = "openshift.bankdata.dk";
    public static final String VERSION = "v1";
}

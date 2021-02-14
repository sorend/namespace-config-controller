package namespace_config_controller.reconciler;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import namespace_config_controller.controller.Reconciler;
import namespace_config_controller.crds.NamespaceConfig;
import namespace_config_controller.crds.NamespaceConfigPhase;
import namespace_config_controller.crds.NamespaceConfigStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

public class NamespaceConfigReconciler implements Reconciler<NamespaceConfig> {

    private static final String NETNAMESPACE_MULTICAST = "netnamespace.network.openshift.io/multicast-enabled";
    private static final Logger logger = LoggerFactory.getLogger(NamespaceConfigReconciler.class);

    private static final Counter totalCounter = Counter.build()
            .name("namespace_config_total").help("Total number of namespaces registered")
            .register();

    private static final Gauge successGauge = Gauge.build()
            .name("namespace_config_reconcile_success").help("Failed reconcilation of namespace configs")
            .labelNames("namespace_config_namespace", "namespace_config_name")
            .register();

    private final KubernetesClient client;

    public NamespaceConfigReconciler(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public void reconcileAdd(Resource<NamespaceConfig> resourceRef) {
        doReconcile(resourceRef);
    }

    @Override
    public void reconcileUpdate(NamespaceConfig oldObject, Resource<NamespaceConfig> resourceRef) {
        doReconcile(resourceRef);
    }

    @Override
    public void reconcileDelete(Resource<NamespaceConfig> object) {
        logger.debug("Delete, ignoring");
    }

    private void doReconcile(Resource<NamespaceConfig> resourceRef) {
        // lookup obj
        NamespaceConfig originalObj = resourceRef.require();
        String namespace = originalObj.getMetadata().getNamespace();
        String name = originalObj.getMetadata().getName();
        logger.debug("{}/{}: Reconciling", originalObj.getMetadata().getNamespace(), originalObj.getMetadata().getName());

        try {
            postStatus(resourceRef, NamespaceConfigPhase.New, "Reconciling");


            Map<String, String> annotations = originalObj.getSpec().getExtraAnnotations();
            Map<String, String> labels = originalObj.getSpec().getExtraLabels();
            logger.info("Namespace={}, annotations={}, labels={}", namespace, annotations, labels);

            if (annotations.size() == 0 && labels.size() == 0) {
                postStatus(resourceRef, NamespaceConfigPhase.Succeeded, "Applied, no configuration required");
                return;
            }

            postStatus(resourceRef, NamespaceConfigPhase.Upgrading, "Applying configuration");
            logger.info("Editing namespace {}", namespace);

            // transfer config labels and annotations to namespace
            client.namespaces().withName(namespace).edit(namespaceObj -> {
                if (namespaceObj.getMetadata().getAnnotations() == null)
                    namespaceObj.getMetadata().setAnnotations(annotations);
                else
                    namespaceObj.getMetadata().getAnnotations().putAll(annotations);

                if (namespaceObj.getMetadata().getLabels() == null)
                    namespaceObj.getMetadata().setLabels(labels);
                else
                    namespaceObj.getMetadata().getLabels().putAll(labels);

                return namespaceObj;
            });

            // apply netnamespace multicast policy
            /*
            boolean multicast = originalObj.getSpec().isMulticast();
            OpenShiftClient openShiftClient = client.adapt(OpenShiftClient.class);
            openShiftClient.netNamespaces().withName(namespace).edit(new UnaryOperator<NetNamespace>() {
                @Override
                public NetNamespace apply(NetNamespace netNamespace) {
                    if (multicast)
                        netNamespace.getMetadata().getAnnotations().put(NETNAMESPACE_MULTICAST, "true");
                    else
                        netNamespace.getMetadata().getAnnotations().remove(NETNAMESPACE_MULTICAST);
                    return netNamespace;
                }
            });
             */

            postStatus(resourceRef, NamespaceConfigPhase.Succeeded, "Namespace config applied", originalObj.getMetadata().getGeneration());
            successGauge.labels(namespace, name).set(1);
        }
        catch (Exception e) {
            logger.error("{}/{} Error reconciling", namespace, name, e);
            successGauge.labels(namespace, name).set(0);
            postStatus(resourceRef, NamespaceConfigPhase.UpgradeFailed, "Failed: " + e.getMessage());
        }
    }

    private void postStatus(Resource<NamespaceConfig> resourceRef, NamespaceConfigPhase phase, String message) {
        postStatus(resourceRef, phase, message, -1);
    }

    private void postStatus(Resource<NamespaceConfig> resourceRef, NamespaceConfigPhase phase, String message, long generation) {
        NamespaceConfig currentObj = resourceRef.get();
        if (currentObj.getStatus() == null)
            currentObj.setStatus(new NamespaceConfigStatus());
        currentObj.getStatus().setPhase(phase);
        currentObj.getStatus().setMessage(message);
        if (generation != -1)
            currentObj.getStatus().setObservedGeneration(generation);
        resourceRef.updateStatus(currentObj);
    }
}

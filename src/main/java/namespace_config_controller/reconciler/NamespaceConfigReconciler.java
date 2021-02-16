package namespace_config_controller.reconciler;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.NetNamespace;
import io.fabric8.openshift.client.OpenShiftClient;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import namespace_config_controller.controller.Reconciler;
import namespace_config_controller.crds.NamespaceConfig;
import namespace_config_controller.crds.NamespaceConfigPhase;
import namespace_config_controller.crds.NamespaceConfigSpec;
import namespace_config_controller.crds.NamespaceConfigStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class NamespaceConfigReconciler implements Reconciler<NamespaceConfig> {

    private static final String NETNAMESPACE_MULTICAST = "netnamespace.network.openshift.io/multicast-enabled";

    private static final String ANNOTATION_OWNER = "bankdata.dk/project-owner";
    private static final String ANNOTATION_EMAIL = "bankdata.dk/project-email";
    private static final String ANNOTATION_SLACK = "bankdata.dk/project-slack";
    private static final String OPENSHIFT_REQUESTER = "openshift.io/requester";
    private static final String OPENSHIFT_DESCRIPTION = "openshift.io/description";
    private static final String OPENSHIFT_DISPLAY_NAME = "openshift.io/display-name";
    private static final String MANAGED_ANNOTATIONS = "openshift.bankdata.dk/v1/managed-annotations";
    private static final String MANAGED_LABELS = "openshift.bankdata.dk/v1/managed-labels";

    private static final Logger logger = LoggerFactory.getLogger(NamespaceConfigReconciler.class);

    private static final Counter totalCounter = Counter.build()
            .name("namespace_config_total").help("Total number of namespaces registered")
            .register();

    private static final Gauge successGauge = Gauge.build()
            .name("namespace_config_reconcile_success").help("Failed reconcilation of namespace configs")
            .labelNames("namespace_config_namespace", "namespace_config_name")
            .register();

    private final KubernetesClient client;
    private final boolean provisionNetnamespace;

    public NamespaceConfigReconciler(KubernetesClient client, boolean provisionNetnamespace) {
        this.client = client;
        this.provisionNetnamespace = provisionNetnamespace;
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

            NamespaceConfigSpec spec = originalObj.getSpec();
            Map<String, String> annotations = spec.getExtraAnnotations();
            Map<String, String> labels = spec.getExtraLabels();

            annotations.put(ANNOTATION_OWNER, spec.getOwner());
            annotations.put(OPENSHIFT_REQUESTER, spec.getOwner());
            annotations.put(ANNOTATION_EMAIL, spec.getEmail());
            if (spec.getSlack() != null)
                annotations.put(ANNOTATION_SLACK, spec.getSlack());
            annotations.put(OPENSHIFT_DESCRIPTION, spec.getDescription());
            annotations.put(OPENSHIFT_DISPLAY_NAME, spec.getDescription());

            String managedAnnotations = String.join(" ", annotations.keySet());
            annotations.put(MANAGED_ANNOTATIONS, managedAnnotations);
            if (!labels.isEmpty()) {
                String managedLabels = String.join(" ", labels.keySet());
                annotations.put(MANAGED_LABELS, managedLabels);
            }

            logger.info("Namespace={}, annotations={}, labels={}", namespace, annotations, labels);

            if (annotations.size() == 0 && labels.size() == 0) {
                postStatus(resourceRef, NamespaceConfigPhase.Succeeded, "Applied, no configuration required");
                return;
            }

            postStatus(resourceRef, NamespaceConfigPhase.Upgrading, "Applying Namespace configuration");
            logger.info("Editing namespace {}", namespace);

            // transfer config labels and annotations to namespace
            client.namespaces().withName(namespace).edit(namespaceObj -> {
                if (namespaceObj.getMetadata().getAnnotations() == null)
                    namespaceObj.getMetadata().setAnnotations(annotations);
                else {
                    String[] currentManagedAnnotations = namespaceObj.getMetadata().getAnnotations().getOrDefault(MANAGED_ANNOTATIONS, "").split(" +");
                    for (String currentManagedAnnotation : currentManagedAnnotations)
                        namespaceObj.getMetadata().getAnnotations().remove(currentManagedAnnotation);
                    namespaceObj.getMetadata().getAnnotations().putAll(annotations);
                }

                if (namespaceObj.getMetadata().getLabels() == null)
                    namespaceObj.getMetadata().setLabels(labels);
                else {
                    String[] currentManagedLabels = namespaceObj.getMetadata().getAnnotations().getOrDefault(MANAGED_LABELS, "").split(" +");
                    for (String currentManagedLabel : currentManagedLabels)
                        namespaceObj.getMetadata().getLabels().remove(currentManagedLabel);
                    namespaceObj.getMetadata().getLabels().putAll(labels);
                }

                return namespaceObj;
            });

            // apply netnamespace multicast policy
            if (provisionNetnamespace) {
                postStatus(resourceRef, NamespaceConfigPhase.Upgrading, "Applying NetNamespace configuration");
                logger.info("Editing netnamespace {}", namespace);
                boolean multicast = originalObj.getSpec().isMulticast();
                OpenShiftClient openShiftClient = client.adapt(OpenShiftClient.class);
                doReconcileNetnamespace(openShiftClient,namespace, multicast);
            }

            postStatus(resourceRef, NamespaceConfigPhase.Succeeded, "Namespace config applied", originalObj.getMetadata().getGeneration());
            successGauge.labels(namespace, name).set(1);
        } catch (Exception e) {
            logger.error("{}/{} Error reconciling", namespace, name, e);
            successGauge.labels(namespace, name).set(0);
            postStatus(resourceRef, NamespaceConfigPhase.UpgradeFailed, "Failed: " + e.getMessage());
        }
    }

    private void doReconcileNetnamespace(OpenShiftClient openShiftClient, String namespace, boolean multicast) {
        Resource<NetNamespace> netnamespaceRef = ensureWithRetry(openShiftClient.netNamespaces().withName(namespace), 5);

        netnamespaceRef.edit(netNamespace -> {
            if (multicast)
                netNamespace.getMetadata().getAnnotations().put(NETNAMESPACE_MULTICAST, "true");
            else
                netNamespace.getMetadata().getAnnotations().remove(NETNAMESPACE_MULTICAST);
            return netNamespace;
        });
    }

    private <K> Resource<K> ensureWithRetry(Resource<K> resourceRef, int times) {
        try {
            for (int i = 0; i < times; i++) {
                if (resourceRef.get() != null)
                    return resourceRef;
                Thread.sleep(1000);
            }
            throw new IllegalArgumentException(resourceRef + ": could not lookup after " + times + " retries");
        }
        catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Interrupted while waiting", ie);
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

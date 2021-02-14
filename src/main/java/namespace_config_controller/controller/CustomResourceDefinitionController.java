package namespace_config_controller.controller;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class CustomResourceDefinitionController<K extends CustomResource, L extends CustomResourceList<K>> {

    private static final Logger logger = LoggerFactory.getLogger(CustomResourceDefinitionController.class);

    private final KubernetesClient client;

    private SharedInformerFactory informerFactory = null;
    private Class<K> kindClz;
    private Class<L> kindListClz;
    private Reconciler<K> reconciler;

    public CustomResourceDefinitionController(KubernetesClient client, Class<K> kindClz, Class<L> kindListClz, Reconciler<K> reconciler) {
        this.client = client;
        this.kindClz = kindClz;
        this.kindListClz = kindListClz;
        this.reconciler = reconciler;
    }

    public void startup() throws Exception {
        // setup crd
        CustomResourceDefinition crd = lookupCrd(client, kindClz);
        KubernetesDeserializer.registerCustomKind(HasMetadata.getApiVersion(kindClz), crd.getKind(), kindClz);
        // create client for the crd
        MixedOperation<K, L, Resource<K>> crdClient = client.customResources(kindClz, kindListClz);
        // start shared informer
        informerFactory = startSharedInformer(crd, crdClient);
    }

    private static <K extends CustomResource> CustomResourceDefinition lookupCrd(KubernetesClient client, Class<K> kindClz) {
        String crdName = CustomResource.getCRDName(kindClz);

        // check if crd is installed, fail if not
        Optional<CustomResourceDefinition> found = client.apiextensions().v1().customResourceDefinitions().list().getItems().stream().filter(x -> crdName.equals(x.getMetadata().getName())).findFirst();
        if (found.isEmpty()) {
            logger.error("crd {} not installed", crdName);
            throw new IllegalArgumentException(crdName + ": crd not found.");
        }

        return found.get();
    }

    private SharedInformerFactory startSharedInformer(CustomResourceDefinition crd, MixedOperation<K, L, Resource<K>> crdClient) throws Exception {
        // setup event handler for crd
        SharedInformerFactory informerFactory = client.informers();
        SharedInformer<K> crdInformer = informerFactory.sharedIndexInformerForCustomResource(CustomResourceDefinitionContext.fromCrd(crd), kindClz, kindListClz, 10 * 60 * 1000);
        crdInformer.addEventHandler(new ResourceEventHandler<>() {
            @Override
            public void onAdd(K obj) {
                logger.debug("{}/{} reconciling - ADD", obj.getMetadata().getNamespace(), obj.getMetadata().getName());
                reconciler.reconcileAdd(resourceRef(crdClient, obj));
            }

            @Override
            public void onUpdate(K oldObj, K newObj) {
                if (oldObj.getMetadata().getGeneration() != newObj.getMetadata().getGeneration()) {
                    logger.debug("{}/{} reconciling - UPDATE", newObj.getMetadata().getNamespace(), newObj.getMetadata().getName());
                    reconciler.reconcileUpdate(oldObj, resourceRef(crdClient, newObj));
                } else
                    logger.debug("{}/{}: Not reconciling same generation of an object {}", newObj.getMetadata().getNamespace(), newObj.getMetadata().getName(), newObj.getMetadata().getGeneration());
            }

            @Override
            public void onDelete(K obj, boolean deletedFinalStateUnknown) {
                logger.debug("{}/{} deleted.", obj.getMetadata().getNamespace(), obj.getMetadata().getName());
                reconciler.reconcileDelete(resourceRef(crdClient, obj));
            }
        });
        // sharedlistener
        informerFactory.addSharedInformerEventListener(exception -> {
            logger.error("Exception occurred, but caught", exception);
            System.exit(2); // application error
        });
        informerFactory.startAllRegisteredInformers();
        return informerFactory;
    }

    public void stop() {
        informerFactory.stopAllRegisteredInformers();
    }

    private Resource<K> resourceRef(MixedOperation<K, L, Resource<K>> crdClient, K obj) {
        return crdClient.inNamespace(obj.getMetadata().getNamespace()).withName(obj.getMetadata().getName());
    }
}

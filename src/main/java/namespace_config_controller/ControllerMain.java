package namespace_config_controller;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.prometheus.client.exporter.HTTPServer;
import namespace_config_controller.controller.AsyncReconciler;
import namespace_config_controller.controller.CustomResourceDefinitionController;
import namespace_config_controller.controller.Reconciler;
import namespace_config_controller.crds.NamespaceConfig;
import namespace_config_controller.crds.NamespaceConfigList;
import namespace_config_controller.reconciler.NamespaceConfigReconciler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ControllerMain {

    public static void main(String[] args) throws Exception {
        // access to kubernetes
        KubernetesClient client = new DefaultKubernetesClient();

        Reconciler<NamespaceConfig> reconciler = new NamespaceConfigReconciler(client);

        ExecutorService executorService = Executors.newFixedThreadPool(5);
        Reconciler<NamespaceConfig> asyncReconciler = new AsyncReconciler<>(executorService, reconciler);

        CustomResourceDefinitionController<NamespaceConfig, NamespaceConfigList> customResourceDefinitionController =
                new CustomResourceDefinitionController<>(client, NamespaceConfig.class, NamespaceConfigList.class, asyncReconciler);

        customResourceDefinitionController.startup();

        HTTPServer metricsServer = new HTTPServer(8080);
    }

    private ControllerMain() {
    }
}

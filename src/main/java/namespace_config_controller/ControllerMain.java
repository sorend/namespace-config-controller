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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ControllerMain {

    private static final Logger logger = LoggerFactory.getLogger(ControllerMain.class);

    public static void main(String[] args) throws Exception {

        boolean provisionNetnamespace = Boolean.parseBoolean(System.getProperty("provision.netnamespace", "false"));
        logger.info("Provision netnamespace={}", provisionNetnamespace);

        KubernetesClient client = new DefaultKubernetesClient();

        Reconciler<NamespaceConfig> reconciler = new NamespaceConfigReconciler(client, provisionNetnamespace);

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

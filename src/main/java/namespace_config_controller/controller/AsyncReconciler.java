package namespace_config_controller.controller;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.dsl.Resource;

import java.util.concurrent.ExecutorService;

public class AsyncReconciler<K extends CustomResource> implements Reconciler<K> {

    ExecutorService executorService;
    Reconciler<K> inner;

    public AsyncReconciler(ExecutorService executorService, Reconciler<K> inner) {
        this.executorService = executorService;
        this.inner = inner;
    }

    @Override
    public void reconcileAdd(Resource<K> object) {
        executorService.submit(() -> inner.reconcileAdd(object));
    }

    @Override
    public void reconcileUpdate(K oldObject, Resource<K> object) {
        executorService.submit(() -> inner.reconcileUpdate(oldObject, object));
    }

    @Override
    public void reconcileDelete(Resource<K> object) {
        executorService.submit(() -> inner.reconcileDelete(object));
    }
}

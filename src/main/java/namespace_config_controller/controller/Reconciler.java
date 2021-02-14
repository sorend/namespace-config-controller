package namespace_config_controller.controller;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.dsl.Resource;

public interface Reconciler<K extends CustomResource> {

    void reconcileAdd(Resource<K> object);

    void reconcileUpdate(K oldObject, Resource<K> object);

    void reconcileDelete(Resource<K> object);
}

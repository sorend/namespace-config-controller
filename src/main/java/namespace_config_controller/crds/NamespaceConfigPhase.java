package namespace_config_controller.crds;

public enum NamespaceConfigPhase {
    New,
    Upgrading,
    UpgradeFailed,
    Succeeded
}

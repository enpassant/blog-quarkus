package hu.rapids.common;
/** HTTP module metadata; individual resource paths remain owned by that module. */
public interface ModuleDescriptor {
    String serviceId();
    String url();
    String title();
    int order();
    default boolean navigation() { return true; }
}

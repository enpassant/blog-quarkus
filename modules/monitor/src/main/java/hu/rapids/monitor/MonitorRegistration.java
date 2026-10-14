package hu.rapids.monitor;
import jakarta.enterprise.context.ApplicationScoped;
import hu.rapids.common.ModuleDescriptor;
@ApplicationScoped
public class MonitorRegistration implements ModuleDescriptor {
 public String serviceId(){return "monitor";}
 public String url(){return "/monitor";}
 public String title(){return "Monitor";}
 public int order(){return 10;}
 public boolean navigation(){return true;}
}

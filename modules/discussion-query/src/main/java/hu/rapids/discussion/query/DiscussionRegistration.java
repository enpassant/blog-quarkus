package hu.rapids.discussion.query;
import jakarta.enterprise.context.ApplicationScoped;
import hu.rapids.common.ModuleDescriptor;
@ApplicationScoped
public class DiscussionRegistration implements ModuleDescriptor {
 public String serviceId(){return "discussion-query";}
 public String url(){return "/discussion";}
 public String title(){return "Discussion";}
 public int order(){return 5;}
 public boolean navigation(){return false;}
}

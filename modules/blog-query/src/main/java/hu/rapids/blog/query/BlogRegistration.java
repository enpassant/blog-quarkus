package hu.rapids.blog.query;
import jakarta.enterprise.context.ApplicationScoped;
import hu.rapids.common.ModuleDescriptor;
@ApplicationScoped
public class BlogRegistration implements ModuleDescriptor {
 public String serviceId(){return "blog-query";}
 public String url(){return "/blog";}
 public String title(){return "Blogs";}
 public int order(){return 0;}
 public boolean navigation(){return true;}
}

package hu.rapids.blog.storage;
import hu.rapids.config.MongoProvider;
import hu.rapids.common.BlogReadModel;
import hu.rapids.common.DocumentRepository;
import hu.rapids.common.Domain;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.List;
import hu.rapids.common.Domain.Blog;
@ApplicationScoped
public class BlogStore implements BlogReadModel {
    @Inject MongoProvider mongo;
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="blog.data-directory",defaultValue="data") String directory;
    private DocumentRepository<Blog> repository;
    @PostConstruct void open() { repository=new DocumentRepository<>(mongo,mapper,"blog",Blog.class,directory); }
    public Blog get(String id) { return repository.get(id); }
    public List<Blog> all() { return repository.all(); }
    public long count() { return repository.count(); }
    public void save(Blog blog) { repository.save(blog.id,blog); }
}

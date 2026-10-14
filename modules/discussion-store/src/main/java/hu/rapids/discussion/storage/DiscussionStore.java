package hu.rapids.discussion.storage;
import hu.rapids.config.MongoProvider;
import hu.rapids.common.DiscussionReadModel;
import hu.rapids.common.DocumentRepository;
import hu.rapids.common.Domain;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.List;
import hu.rapids.common.Domain.Discussion;
@ApplicationScoped
public class DiscussionStore implements DiscussionReadModel {
    @Inject MongoProvider mongo;
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="blog.data-directory",defaultValue="data") String directory;
    private DocumentRepository<Discussion> repository;
    @PostConstruct void open() { repository=new DocumentRepository<>(mongo,mapper,"discussion",Discussion.class,directory); }
    public Discussion get(String id) { return repository.get(id); }
    public List<Discussion> all() { return repository.all(); }
    public long count() { return repository.count(); }
    public void save(Discussion discussion) { repository.save(discussion.id,discussion); }
}

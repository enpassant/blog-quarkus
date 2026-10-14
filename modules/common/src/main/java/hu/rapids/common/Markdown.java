package hu.rapids.common;

import jakarta.enterprise.context.ApplicationScoped;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
@ApplicationScoped
public class Markdown {
    public String render(String text) {
        return HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build().render(Parser.builder().build().parse(text==null?"":text));
    }
}

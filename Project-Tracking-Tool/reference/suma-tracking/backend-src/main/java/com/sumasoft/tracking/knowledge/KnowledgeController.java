package com.sumasoft.tracking.knowledge;

import com.sumasoft.tracking.knowledge.KnowledgeDtos.Article;
import com.sumasoft.tracking.knowledge.KnowledgeDtos.ArticleForm;
import com.sumasoft.tracking.knowledge.KnowledgeDtos.ArticleListItem;
import com.sumasoft.tracking.knowledge.KnowledgeDtos.AskRequest;
import com.sumasoft.tracking.knowledge.KnowledgeDtos.AskResult;
import com.sumasoft.tracking.knowledge.KnowledgeDtos.Feedback;
import com.sumasoft.tracking.knowledge.KnowledgeDtos.SearchResult;
import com.sumasoft.tracking.knowledge.KnowledgeDtos.Unanswered;
import com.sumasoft.tracking.knowledge.KnowledgeDtos.Version;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Knowledge Base (Step 38).
 *   GET  /api/kb/articles?all=false        list of guides
 *   GET  /api/kb/articles/{slug}           one guide (counts a view)
 *   GET  /api/kb/search?q=                 search with simple words
 *   POST /api/kb/ask        {question}     ask in plain words
 *   POST /api/kb/articles/{slug}/feedback  {helpful: true}
 *   Super Admin:
 *   POST /api/kb/articles                  add a guide
 *   PUT  /api/kb/articles/{id}             change a guide (new version)
 *   GET  /api/kb/articles/{id}/versions    change history of a guide
 *   GET  /api/kb/unanswered                searches that found nothing
 */
@RestController
@RequestMapping("/api/kb")
public class KnowledgeController {

    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @GetMapping("/articles")
    public List<ArticleListItem> list(@RequestParam(defaultValue = "false") boolean all) {
        return service.list(all);
    }

    @GetMapping("/articles/{slug}")
    public Article read(@PathVariable String slug) {
        return service.read(slug, true);
    }

    @GetMapping("/search")
    public List<SearchResult> search(@RequestParam(defaultValue = "") String q) {
        return service.search(q);
    }

    @PostMapping("/ask")
    public AskResult ask(@Valid @RequestBody AskRequest request) {
        return service.ask(request.question());
    }

    @PostMapping("/articles/{slug}/feedback")
    public void feedback(@PathVariable String slug, @Valid @RequestBody Feedback feedback) {
        service.feedback(slug, feedback.helpful());
    }

    @PostMapping("/articles")
    public Article create(@Valid @RequestBody ArticleForm form) {
        return service.create(form);
    }

    @PutMapping("/articles/{id}")
    public Article update(@PathVariable Long id, @Valid @RequestBody ArticleForm form) {
        return service.update(id, form);
    }

    @GetMapping("/articles/{id}/versions")
    public List<Version> versions(@PathVariable Long id) {
        return service.versions(id);
    }

    @GetMapping("/unanswered")
    public List<Unanswered> unanswered() {
        return service.unanswered();
    }
}
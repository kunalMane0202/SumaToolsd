package com.sumasoft.tracking.knowledge;

import com.sumasoft.tracking.audit.AuditService;
import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import com.sumasoft.tracking.rbac.RbacService;
import com.sumasoft.tracking.knowledge.KnowledgeDtos.AnswerPart;
import com.sumasoft.tracking.knowledge.KnowledgeDtos.Article;
import com.sumasoft.tracking.knowledge.KnowledgeDtos.ArticleForm;
import com.sumasoft.tracking.knowledge.KnowledgeDtos.ArticleListItem;
import com.sumasoft.tracking.knowledge.KnowledgeDtos.AskResult;
import com.sumasoft.tracking.knowledge.KnowledgeDtos.SearchResult;
import com.sumasoft.tracking.knowledge.KnowledgeDtos.Unanswered;
import com.sumasoft.tracking.knowledge.KnowledgeDtos.Version;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Knowledge Base (Step 38): guides that explain how Suma Tracking works.
 *  - Every signed-in employee can read and search. Search works with simple words, small spelling
 *    mistakes and common Hindi words (KnowledgeSearch). "Ask" mode answers a question in plain words.
 *  - It never opens or changes tickets; it only explains the tool.
 *  - Searches with no answer are saved, so the Super Admin can add a guide for them.
 *  - Roles with KB_MANAGE (Super Admin by default) add or edit guides. Guides are never deleted (status HIDDEN), and every
 *    saved version is kept (kb_article_versions) and audited.
 */
@Service
public class KnowledgeService {

    private static final int MAX_RESULTS = 10;

    private final AccessService access;
    private final AuditService audit;
    private final RbacService rbac;
    private final JdbcTemplate jdbc;

    public KnowledgeService(AccessService access, AuditService audit, RbacService rbac, DataSource dataSource) {
        this.access = access;
        this.audit = audit;
        this.rbac = rbac;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ================================================================ reading

    @Transactional(readOnly = true)
    public List<ArticleListItem> list(boolean includeHidden) {
        AuthenticatedUser me = access.current();
        boolean all = includeHidden && rbac.has(me, "KB_MANAGE");
        return jdbc.query("SELECT id, slug, category, title, summary, status, sort_order, view_count, updated_at FROM kb_articles "
                        + (all ? "" : "WHERE status = 'PUBLISHED' ") + "ORDER BY sort_order, id",
                (rs, n) -> new ArticleListItem(rs.getLong("id"), rs.getString("slug"), rs.getString("category"),
                        rs.getString("title"), rs.getString("summary"), rs.getString("status"), rs.getInt("sort_order"),
                        rs.getInt("view_count"), instant(rs.getTimestamp("updated_at"))));
    }

    @Transactional
    public Article read(String slug, boolean countView) {
        AuthenticatedUser me = access.current();
        List<Article> found = jdbc.query(ARTICLE_SELECT + " WHERE a.slug = ?", (rs, n) -> article(rs), slug);
        if (found.isEmpty() || ("HIDDEN".equals(found.get(0).status()) && !rbac.has(me, "KB_MANAGE"))) {
            throw AccessService.notFound("Guide");
        }
        if (countView) {
            jdbc.update("UPDATE kb_articles SET view_count = view_count + 1 WHERE slug = ?", slug);
        }
        return found.get(0);
    }

    @Transactional
    public List<SearchResult> search(String query) {
        AuthenticatedUser me = access.current();
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        if (q.length() > 200) {
            q = q.substring(0, 200);
        }
        List<KnowledgeSearch.Hit> hits = KnowledgeSearch.search(docs(), q, MAX_RESULTS, true);
        if (hits.isEmpty()) {
            logUnanswered(q, "SEARCH", me.userId());
        }
        return hits.stream().map(KnowledgeService::result).toList();
    }

    @Transactional
    public AskResult ask(String question) {
        AuthenticatedUser me = access.current();
        String q = question.trim();
        List<KnowledgeSearch.Doc> docs = docs();
        KnowledgeSearch.Answer answer = KnowledgeSearch.ask(docs, q);
        if (answer == null) {
            logUnanswered(q, "ASK", me.userId());
            List<SearchResult> popular = jdbc.query("SELECT slug, category, title, summary FROM kb_articles "
                            + "WHERE status = 'PUBLISHED' ORDER BY view_count DESC, sort_order LIMIT 5",
                    (rs, n) -> new SearchResult(rs.getString("slug"), rs.getString("category"), rs.getString("title"),
                            rs.getString("summary"), rs.getString("summary")));
            return new AskResult(false, List.of(), null, List.of(), popular);
        }
        KnowledgeSearch.Doc d = answer.hit().doc();
        List<SearchResult> related = KnowledgeSearch.search(docs, q, 5, false).stream()
                .filter(h -> !h.doc().slug().equals(d.slug())).limit(4).map(KnowledgeService::result).toList();
        return new AskResult(true, answer.understood(),
                new AnswerPart(d.slug(), d.category(), d.title(), answer.section().heading(), answer.section().text()),
                related, List.of());
    }

    @Transactional
    public void feedback(String slug, boolean helpful) {
        access.current();
        int changed = jdbc.update("UPDATE kb_articles SET " + (helpful ? "helpful_yes = helpful_yes + 1" : "helpful_no = helpful_no + 1")
                + " WHERE slug = ? AND status = 'PUBLISHED'", slug);
        if (changed == 0) {
            throw AccessService.notFound("Guide");
        }
    }

    // ================================================================ Super Admin

    @Transactional
    public Article create(ArticleForm f) {
        AuthenticatedUser me = access.current();
        rbac.require(me, "KB_MANAGE");
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM kb_articles WHERE slug = ?", Integer.class, f.slug());
        if (exists != null && exists > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A guide with this short web name already exists");
        }
        int sort = f.sortOrder() != null ? f.sortOrder()
                : jdbc.queryForObject("SELECT COALESCE(MAX(sort_order), 0) + 10 FROM kb_articles", Integer.class);
        Long id = jdbc.queryForObject("INSERT INTO kb_articles (slug, category, title, summary, body, keywords, status, "
                        + "sort_order, updated_by_user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                f.slug(), f.category().trim(), f.title().trim(), f.summary().trim(), normalise(f.body()),
                f.keywords() == null ? "" : f.keywords().trim(), f.status(), sort, me.userId());
        saveVersion(id, me.userId());
        Article a = read(f.slug(), false);
        audit.record("KB_ARTICLE_CREATED", "KB_ARTICLE", id, null, null, null, facts(a));
        return a;
    }

    @Transactional
    public Article update(Long id, ArticleForm f) {
        AuthenticatedUser me = access.current();
        rbac.require(me, "KB_MANAGE");
        List<Article> found = jdbc.query(ARTICLE_SELECT + " WHERE a.id = ?", (rs, n) -> article(rs), id);
        if (found.isEmpty()) {
            throw AccessService.notFound("Guide");
        }
        Article before = found.get(0);
        if (!before.slug().equals(f.slug())) {
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM kb_articles WHERE slug = ? AND id <> ?", Integer.class, f.slug(), id);
            if (exists != null && exists > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A guide with this short web name already exists");
            }
        }
        jdbc.update("UPDATE kb_articles SET slug = ?, category = ?, title = ?, summary = ?, body = ?, keywords = ?, status = ?, "
                        + "sort_order = ?, version_no = version_no + 1, updated_at = now(), updated_by_user_id = ? WHERE id = ?",
                f.slug(), f.category().trim(), f.title().trim(), f.summary().trim(), normalise(f.body()),
                f.keywords() == null ? "" : f.keywords().trim(), f.status(),
                f.sortOrder() != null ? f.sortOrder() : before.sortOrder(), me.userId(), id);
        saveVersion(id, me.userId());
        Article after = read(f.slug(), false);
        audit.record("KB_ARTICLE_UPDATED", "KB_ARTICLE", id, null, null, facts(before), facts(after));
        return after;
    }

    @Transactional(readOnly = true)
    public List<Version> versions(Long id) {
        rbac.require(access.current(), "KB_MANAGE");
        return jdbc.query("SELECT v.version_no, v.title, v.category, v.status, v.changed_at, u.full_name FROM kb_article_versions v "
                        + "LEFT JOIN users u ON u.id = v.changed_by_user_id WHERE v.article_id = ? ORDER BY v.version_no DESC",
                (rs, n) -> new Version(rs.getInt("version_no"), rs.getString("title"), rs.getString("category"),
                        rs.getString("status"), instant(rs.getTimestamp("changed_at")), rs.getString("full_name")), id);
    }

    @Transactional(readOnly = true)
    public List<Unanswered> unanswered() {
        rbac.require(access.current(), "KB_MANAGE");
        return jdbc.query("SELECT q.id, q.question, q.mode, q.asked_at, u.full_name FROM kb_unanswered q "
                        + "LEFT JOIN users u ON u.id = q.asked_by_user_id ORDER BY q.asked_at DESC LIMIT 200",
                (rs, n) -> new Unanswered(rs.getLong("id"), rs.getString("question"), rs.getString("mode"),
                        rs.getString("full_name"), instant(rs.getTimestamp("asked_at"))));
    }

    // ================================================================ helpers

    private static final String ARTICLE_SELECT = "SELECT a.id, a.slug, a.category, a.title, a.summary, a.body, a.keywords, "
            + "a.status, a.sort_order, a.view_count, a.helpful_yes, a.helpful_no, a.version_no, a.updated_at, "
            + "u.full_name AS updated_by FROM kb_articles a LEFT JOIN users u ON u.id = a.updated_by_user_id";

    private static Article article(ResultSet rs) throws SQLException {
        return new Article(rs.getLong("id"), rs.getString("slug"), rs.getString("category"), rs.getString("title"),
                rs.getString("summary"), rs.getString("body"), rs.getString("keywords"), rs.getString("status"),
                rs.getInt("sort_order"), rs.getInt("view_count"), rs.getInt("helpful_yes"), rs.getInt("helpful_no"),
                rs.getInt("version_no"), instant(rs.getTimestamp("updated_at")), rs.getString("updated_by"));
    }

    private List<KnowledgeSearch.Doc> docs() {
        return jdbc.query("SELECT id, slug, category, title, summary, body, keywords FROM kb_articles WHERE status = 'PUBLISHED'",
                (rs, n) -> new KnowledgeSearch.Doc(rs.getLong("id"), rs.getString("slug"), rs.getString("category"),
                        rs.getString("title"), rs.getString("summary"), rs.getString("body"), rs.getString("keywords")));
    }

    private void saveVersion(Long id, Long userId) {
        jdbc.update("INSERT INTO kb_article_versions (article_id, version_no, category, title, summary, body, keywords, status, "
                + "changed_by_user_id) SELECT id, version_no, category, title, summary, body, keywords, status, ? "
                + "FROM kb_articles WHERE id = ?", userId, id);
    }

    private void logUnanswered(String question, String mode, Long userId) {
        jdbc.update("INSERT INTO kb_unanswered (question, mode, asked_by_user_id) VALUES (?, ?, ?)",
                question.length() > 300 ? question.substring(0, 300) : question, mode, userId);
    }

    private static SearchResult result(KnowledgeSearch.Hit h) {
        KnowledgeSearch.Doc d = h.doc();
        return new SearchResult(d.slug(), d.category(), d.title(), d.summary(), h.snippet());
    }

    private static String normalise(String body) {
        return body.replace("\r\n", "\n").trim();
    }

    private static Map<String, Object> facts(Article a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("slug", a.slug());
        m.put("title", a.title());
        m.put("category", a.category());
        m.put("status", a.status());
        m.put("version", a.versionNo());
        return m;
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    static List<String> categories(List<ArticleListItem> items) {
        List<String> out = new ArrayList<>();
        for (ArticleListItem i : items) {
            if (!out.contains(i.category())) {
                out.add(i.category());
            }
        }
        return out;
    }
}
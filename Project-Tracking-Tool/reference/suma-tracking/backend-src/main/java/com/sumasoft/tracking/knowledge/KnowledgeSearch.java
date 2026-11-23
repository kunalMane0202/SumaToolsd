package com.sumasoft.tracking.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finds help guides for a search or a question typed in plain words (Step 38).
 * No outside service is used. It understands:
 *  - word endings: "tickets", "importing", "escalated"
 *  - words that mean the same: "staff" = employee, "upload" = import, "late" = SLA
 *  - small spelling mistakes: "pasword", "escalte", "employe"
 *  - common Hindi / Hinglish words: "kaise", "banaye", "badle", "hataye", "dekhe"
 * The same class can later be used by the chatbot.
 */
public final class KnowledgeSearch {

    private KnowledgeSearch() {
    }

    public record Doc(long id, String slug, String category, String title, String summary, String body, String keywords) {
    }

    public record Hit(Doc doc, double score, String snippet, int matched, int asked) {
    }

    public record Section(String heading, String text) {
    }

    public record Answer(Hit hit, Section section, List<String> understood) {
    }

    // ------------------------------------------------------------------ language

    private static final Set<String> STOP = Set.copyOf(List.of(
            "a", "an", "the", "to", "in", "on", "of", "is", "are", "am", "be", "can", "could", "do", "does", "did",
            "i", "me", "my", "we", "our", "you", "your", "it", "its", "this", "that", "these", "those", "there",
            "how", "what", "where", "why", "when", "which", "who", "whom", "for", "and", "or", "with", "from", "by",
            "at", "as", "if", "so", "then", "please", "pls", "plz", "want", "need", "help", "tell", "about", "any",
            "some", "get", "got", "should", "would", "will", "shall", "may", "might", "must", "have", "has", "had",
            "into", "out", "up", "down", "just", "also", "only", "all", "more", "much", "very", "too", "here",
            "kaise", "kese", "kaisa", "kya", "kyaa", "hai", "hain", "he", "kare", "karein", "kare", "karna", "karu",
            "karo", "karne", "karte", "kar", "kahan", "kaha", "kab", "kyun", "kyu", "mein", "main", "mai", "ka", "ki",
            "ke", "ko", "se", "aur", "ya", "hota", "hoti", "ho", "hua", "hui", "tha", "thi", "batao", "bataye",
            "bataiye", "mujhe", "muje", "mera", "meri", "mere", "apna", "apni", "pe", "par", "raha", "rahi", "rahe",
            "nahi", "nhi", "aa", "aata", "chahiye", "sakte", "sakta", "sakti", "skte", "hum", "ham", "yeh", "ye",
            "wo", "woh", "isko", "usko", "kaun", "kon", "sir", "maam", "ji"));

    /** Words that mean the same thing. Every word of a group finds the others. */
    private static final List<List<String>> GROUPS = List.of(
            List.of("create", "add", "new", "make", "register", "insert", "naya", "nayi", "banaye", "banana", "banao", "bana", "onboard"),
            List.of("delete", "remove", "erase", "hatana", "hataye", "hatao", "hata", "deactivate", "disable"),
            List.of("edit", "change", "update", "modify", "correct", "rename", "badle", "badalna", "badlo", "badal"),
            List.of("find", "search", "look", "see", "view", "dekhe", "dekhna", "dekho", "dekh", "dhundhe", "dhundna", "dhoondo", "dhundo", "lookup"),
            List.of("employee", "staff", "agent", "member", "user", "person", "people", "colleague", "karmachari", "worker"),
            List.of("client", "customer", "company"),
            List.of("ticket", "case", "complaint", "query", "request"),
            List.of("password", "pwd", "passcode", "passwrd"),
            List.of("login", "signin", "sign", "logon", "credential"),
            List.of("import", "upload", "excel", "sheet", "xlsx", "spreadsheet", "csv"),
            List.of("sla", "deadline", "late", "overdue", "breach", "promise", "target"),
            List.of("escalate", "escalation", "tier", "l2", "senior"),
            List.of("assign", "reassign", "allocate", "give", "handover", "transfer", "dena", "dedo", "owner", "assignee"),
            List.of("report", "export", "download"),
            List.of("notification", "alert", "bell", "reminder"),
            List.of("error", "problem", "fail", "wrong", "bug", "galti", "gadbad", "issue", "broken"),
            List.of("dashboard", "chart", "graph", "kpi"),
            List.of("role", "permission", "access", "right"),
            List.of("holiday", "leave", "vacation", "chutti", "weekoff"),
            List.of("shift", "timing", "schedule", "roster"),
            List.of("status", "stage", "state"),
            List.of("start", "run", "launch", "chalu", "chalana", "begin", "open"),
            List.of("phone", "mobile", "contact"),
            List.of("email", "mail", "gmail"),
            List.of("history", "timeline", "purana", "audit"),
            List.of("stop", "close", "band", "finish"),
            List.of("server", "backend"),
            List.of("website", "frontend", "browser", "site"));

    private static final Map<String, Set<String>> SYNONYMS = new LinkedHashMap<>();

    static {
        for (List<String> group : GROUPS) {
            Set<String> stems = new LinkedHashSet<>();
            for (String w : group) {
                stems.add(stem(w));
            }
            for (String s : stems) {
                SYNONYMS.computeIfAbsent(s, k -> new LinkedHashSet<>()).addAll(stems);
            }
        }
    }

    static String stem(String w) {
        String s = w.toLowerCase(Locale.ROOT);
        if (s.length() > 5 && s.endsWith("ing")) {
            return s.substring(0, s.length() - 3);
        }
        if (s.length() > 4 && s.endsWith("ies")) {
            return s.substring(0, s.length() - 3) + "y";
        }
        if (s.length() > 4 && (s.endsWith("ed") || s.endsWith("es"))) {
            return s.substring(0, s.length() - 2);
        }
        if (s.length() > 3 && s.endsWith("s") && !s.endsWith("ss")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String w : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().split(" ")) {
            if (w.length() >= 2 && !STOP.contains(w)) {
                out.add(stem(w));
            }
        }
        return out;
    }

    /** One idea from the query: the word typed plus the words that mean the same. */
    private record Concept(String typed, Set<String> alternatives) {
    }

    private static List<Concept> concepts(String query) {
        List<Concept> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String w : words(query)) {
            if (!seen.add(w)) {
                continue;
            }
            Set<String> alts = new LinkedHashSet<>();
            alts.add(w);
            alts.addAll(SYNONYMS.getOrDefault(w, Set.of()));
            // a misspelt word can still reach its synonyms ("pasword" -> password)
            if (alts.size() == 1 && w.length() >= 4) {
                for (Map.Entry<String, Set<String>> e : SYNONYMS.entrySet()) {
                    if (close(w, e.getKey())) {
                        alts.addAll(e.getValue());
                        break;
                    }
                }
            }
            list.add(new Concept(w, alts));
        }
        return list;
    }

    // ------------------------------------------------------------------ matching

    private record Indexed(Doc doc, Set<String> title, Set<String> keywords, Set<String> summary,
                           Set<String> category, Set<String> body, String plainTitle, String plainBody) {
    }

    private static Indexed index(Doc d) {
        return new Indexed(d, new HashSet<>(words(d.title())), new HashSet<>(words(d.keywords())),
                new HashSet<>(words(d.summary())), new HashSet<>(words(d.category())), new HashSet<>(words(d.body())),
                plain(d.title()), plain(d.body()));
    }

    /** 1 = exact word, 0.7 = close spelling, 0 = not found. */
    private static double quality(Concept c, Set<String> field) {
        for (String a : c.alternatives()) {
            if (field.contains(a)) {
                return 1.0;
            }
        }
        if (c.typed().length() >= 4) {
            for (String f : field) {
                if (close(c.typed(), f)) {
                    return 0.7;
                }
            }
        }
        return 0;
    }

    static boolean close(String a, String b) {
        if (a.length() < 4 || b.length() < 4 || a.charAt(0) != b.charAt(0)) {
            return false;
        }
        int allowed = Math.max(a.length(), b.length()) <= 6 ? 1 : 2;
        if (Math.abs(a.length() - b.length()) > allowed) {
            return false;
        }
        return distance(a, b) <= allowed;
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /**
     * Ranked guides for a search or a question.
     * strict = true (search box): at least half of the ideas must be found.
     * strict = false (ask mode): one idea is enough; the best ones still come first.
     */
    public static List<Hit> search(List<Doc> docs, String query, int limit, boolean strict) {
        List<Concept> cs = concepts(query);
        if (cs.isEmpty()) {
            return List.of();
        }
        String phrase = plain(query);
        List<Hit> hits = new ArrayList<>();
        for (Doc d : docs) {
            Indexed x = index(d);
            double score = 0;
            int matched = 0;
            for (Concept c : cs) {
                double best = Math.max(Math.max(6 * quality(c, x.title()), 4 * quality(c, x.keywords())),
                        Math.max(Math.max(3 * quality(c, x.summary()), 2 * quality(c, x.category())),
                                1.5 * quality(c, x.body())));
                if (best > 0) {
                    matched++;
                    score += best;
                }
            }
            if (matched == 0) {
                continue;
            }
            // search box: two words must both be found; longer searches need half of the ideas.
            // ask mode: at least 60% of the ideas of a longer question must be found.
            int needed = strict
                    ? (cs.size() == 2 ? 2 : Math.max(1, (int) Math.ceil(cs.size() / 2.0)))
                    : (cs.size() >= 2 ? (int) Math.ceil(cs.size() * 0.6) : 1);
            if (matched < needed) {
                continue;
            }
            double coverage = (double) matched / cs.size();
            score += coverage * coverage * 6;
            // one-word search: a guide whose title is mostly about that word comes first
            int inTitle = 0;
            for (Concept c : cs) {
                if (quality(c, x.title()) > 0) {
                    inTitle++;
                }
            }
            if (cs.size() == 1 && !x.title().isEmpty()) {
                score += 3.0 * inTitle / x.title().size();
            }
            if (phrase.contains(" ") && phrase.length() >= 5) {
                if (x.plainTitle().contains(phrase)) {
                    score += 8;
                } else if (x.plainBody().contains(phrase)) {
                    score += 3;
                }
            }
            hits.add(new Hit(d, score, snippet(d, cs), matched, cs.size()));
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return hits.size() > limit ? new ArrayList<>(hits.subList(0, limit)) : hits;
    }

    /** Ask mode: the best guide, and inside it the best part, for a question in plain words. */
    public static Answer ask(List<Doc> docs, String question) {
        List<Hit> hits = search(docs, question, 1, false);
        if (hits.isEmpty()) {
            return null;
        }
        Hit top = hits.get(0);
        List<Concept> cs = concepts(question);
        List<Section> sections = sections(top.doc().body());
        Section best = sections.get(0);
        double bestScore = -1;
        for (Section s : sections) {
            Set<String> head = new HashSet<>(words(s.heading()));
            Set<String> text = new HashSet<>(words(s.text()));
            double sc = 0;
            for (Concept c : cs) {
                sc += 3 * quality(c, head) + quality(c, text);
            }
            if (sc > bestScore) {
                bestScore = sc;
                best = s;
            }
        }
        List<String> understood = new ArrayList<>();
        Indexed x = index(top.doc());
        for (Concept c : cs) {
            if (quality(c, x.title()) + quality(c, x.keywords()) + quality(c, x.summary()) + quality(c, x.body()) > 0) {
                understood.add(c.typed());
            }
        }
        return new Answer(top, best, understood);
    }

    /** The parts of a guide, split at "## " headings. */
    public static List<Section> sections(String body) {
        List<Section> out = new ArrayList<>();
        String heading = "";
        StringBuilder text = new StringBuilder();
        for (String line : body.split("\n")) {
            if (line.startsWith("## ")) {
                if (text.toString().trim().length() > 0) {
                    out.add(new Section(heading, text.toString().trim()));
                }
                heading = line.substring(3).trim();
                text = new StringBuilder();
            } else {
                text.append(line).append('\n');
            }
        }
        if (text.toString().trim().length() > 0) {
            out.add(new Section(heading, text.toString().trim()));
        }
        if (out.isEmpty()) {
            out.add(new Section("", body.trim()));
        }
        return out;
    }

    private static String snippet(Doc d, List<Concept> cs) {
        for (String line : d.body().split("\n")) {
            String clean = line.replaceFirst("^(##\\s+|\\d+\\.\\s+|-\\s+|Tip:\\s*|Note:\\s*|Warning:\\s*)", "").trim();
            if (clean.length() < 12) {
                continue;
            }
            Set<String> ws = new HashSet<>(words(clean));
            for (Concept c : cs) {
                for (String a : c.alternatives()) {
                    if (ws.contains(a)) {
                        return clean.length() > 220 ? clean.substring(0, 217) + "..." : clean;
                    }
                }
            }
        }
        return d.summary();
    }

    private static String plain(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
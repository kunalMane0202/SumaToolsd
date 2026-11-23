package com.sumasoft.tracking.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Requests and answers of the Knowledge Base (Step 38). */
public final class KnowledgeDtos {

    private KnowledgeDtos() {
    }

    public record ArticleListItem(Long id, String slug, String category, String title, String summary, String status,
                                  int sortOrder, int viewCount, Instant updatedAt) {
    }

    public record Article(Long id, String slug, String category, String title, String summary, String body,
                          String keywords, String status, int sortOrder, int viewCount, int helpfulYes, int helpfulNo,
                          int versionNo, Instant updatedAt, String updatedBy) {
    }

    public record SearchResult(String slug, String category, String title, String summary, String snippet) {
    }

    public record AnswerPart(String slug, String category, String title, String heading, String text) {
    }

    public record AskResult(boolean found, List<String> understood, AnswerPart answer,
                            List<SearchResult> related, List<SearchResult> suggestions) {
    }

    public record AskRequest(
            @NotBlank(message = "Type your question")
            @Size(max = 300, message = "Please keep the question under 300 characters")
            String question
    ) {
    }

    public record Feedback(@NotNull(message = "Say yes or no") Boolean helpful) {
    }

    public record ArticleForm(
            @NotBlank(message = "Short web name is required")
            @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                     message = "Short web name can have small letters, numbers and single hyphens, like add-employee")
            @Size(max = 120, message = "Short web name must be at most 120 characters")
            String slug,
            @NotBlank(message = "Category is required")
            @Size(max = 60, message = "Category must be at most 60 characters")
            String category,
            @NotBlank(message = "Title is required")
            @Size(min = 3, max = 200, message = "Title must be 3 to 200 characters")
            String title,
            @NotBlank(message = "Short summary is required")
            @Size(max = 500, message = "Summary must be at most 500 characters")
            String summary,
            @NotBlank(message = "The guide text is required")
            @Size(max = 50000, message = "The guide text is too long")
            String body,
            @Size(max = 1000, message = "Keywords must be at most 1000 characters")
            String keywords,
            @NotBlank(message = "Status is required")
            @Pattern(regexp = "^(PUBLISHED|HIDDEN)$", message = "Status must be PUBLISHED or HIDDEN")
            String status,
            Integer sortOrder
    ) {
    }

    public record Version(int versionNo, String title, String category, String status, Instant changedAt, String changedBy) {
    }

    public record Unanswered(Long id, String question, String mode, String askedBy, Instant askedAt) {
    }
}
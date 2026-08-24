package gg.duo.match.dto;

public record RecommendationEventRequest(
        String eventType   // IMPRESSION | CLICK | APPLY
) {
}

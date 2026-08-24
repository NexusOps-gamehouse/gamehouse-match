package gg.duo.match.client;

import com.fasterxml.jackson.databind.JsonNode;
import gg.duo.match.dto.PersonalityProfile;

/**
 * UserClient/PostClient가 공통으로 쓰는 JSON 파싱 헬퍼. common 모듈이 생기기 전까지
 * 임시로 여기 둔다 — 두 client 모두 duo-backend 모놀리스의 응답을 JsonNode로 받아서
 * 직접 파싱하는 같은 방식을 쓰기 때문에, personality 파싱 로직 중복만이라도 없앤다.
 */
final class JsonParsingUtils {

    private JsonParsingUtils() {
    }

    /** personality 필드가 응답에 아예 없으면(설문 개편 이전 user/post 서비스) null을 돌려준다. */
    static PersonalityProfile parsePersonality(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        return new PersonalityProfile(
                doubleOrNull(node, "winIntent"),
                doubleOrNull(node, "mistakeTolerance"),
                doubleOrNull(node, "communication"),
                doubleOrNull(node, "focus"),
                doubleOrNull(node, "leadership"),
                doubleOrNull(node, "leadershipPreference"),
                doubleOrNull(node, "sociability")
        );
    }

    static Double doubleOrNull(JsonNode parent, String field) {
        JsonNode n = parent.path(field);
        return (n.isMissingNode() || n.isNull()) ? null : n.asDouble();
    }

    static Integer intOrNull(JsonNode parent, String field) {
        JsonNode n = parent.path(field);
        return (n.isMissingNode() || n.isNull()) ? null : n.asInt();
    }
}

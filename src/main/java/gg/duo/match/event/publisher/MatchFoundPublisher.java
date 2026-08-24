package gg.duo.match.event.publisher;

import gg.duo.common.event.DomainEventPublisher;
import gg.duo.common.event.MatchFoundEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * design doc의 이벤트 흐름(12번): match ─ MatchFound ─→ chat(방 생성), crew(승격 후보 기록).
 *
 * 예전에는 duo-backend에 도메인 이벤트 버스가 없어 로그만 남겼다. 지금은
 * common/event/MatchFoundEvent 계약과 RabbitMQ(gamehouse.events exchange)가
 * 이미 붙어 있으므로(post/chat/user가 이미 쓰는 것과 동일 인프라) 그걸 그대로
 * 쓴다 — 호출하는 쪽(MatchSearchService)은 손대지 않아도 된다는 원래 설계 그대로,
 * 이 클래스 내부만 실제 발행 코드로 바뀌었다.
 *
 * 아직 이 이벤트를 구독하는 서비스(chat/crew)는 없다 — 계약만 먼저 나가 있는
 * 상태라 발행해도 부작용은 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchFoundPublisher {

    private final DomainEventPublisher domainEventPublisher;

    /**
     * memberIds는 지금은 신청자(requesterId) 하나뿐이다. 모집글 작성자/실제 파티원
     * id는 MatchResult에 저장돼 있지 않고(postId만 있음), post 서비스가 아직 파티원
     * 배열을 안 내려줘 PostSummaryDto.members도 비어 있을 때가 많다(README "파티원
     * 개별 데이터" 참고). post 쪽 파티 구성 정보가 갖춰지면 그때 memberIds를
     * [requesterId, ...실제 파티원]으로 넓힌다 — 호출부는 그대로 두고 이 메서드
     * 내부만 고치면 된다.
     */
    public void publish(Long matchResultId, Long postId, Long requesterId, String gameCode) {
        log.info("[MatchFound] resultId={} postId={} requesterId={} game={}",
                matchResultId, postId, requesterId, gameCode);

        List<Long> memberIds = new ArrayList<>();
        if (requesterId != null) {
            memberIds.add(requesterId);
        }
        domainEventPublisher.publish(new MatchFoundEvent(matchResultId, gameCode, memberIds));
    }
}

# match 서비스

`gamehouse` MSA의 신규 서비스. user/post 서비스의 `GET /api/users/{id}`, `GET /api/posts`를
REST로 호출해 "지금 나에게 맞는 모집글 Top N"을 추천한다. users/posts 테이블을 직접 조회하지
않고, match 자신의 테이블(`match_requests`, `match_results`, `recommendation_events`,
`user_preferences`, 스키마 `match_svc`)만 갖는다.

`backend/` 멀티모듈로 합류하면서 자체 `settings.gradle`/`gradlew`는 지웠고(루트 것을 쓴다),
JWT 검증(`JwtAuthFilter`/`JwtTokenProvider`)·전역 예외 처리(`GlobalExceptionHandler`)·보안
기본 설정(`SecurityBaseConfig`)·이벤트 버스(`DomainEventPublisher`/`MatchFoundEvent`/
`RabbitEventConfig`)는 `common` 모듈 것을 그대로 쓰도록 바꿨다 — user/post/chat/riot 과 같은
패턴이다. user/post 호출도 더 이상 단일 모놀리스(`app.monolith-base-url`)가 아니라 실제로
분리된 두 서비스(`services.user.base-url` / `services.post.base-url`)를 각각 가리킨다.

## v2: Team Fit 회의록 반영 (algoVersion `match-v2-teamfit`)

v1(`match-v1-rule-based`)과 가장 크게 다른 점 두 가지:

1. **포지션·티어가 점수가 아니라 Hard Filter다.** `GameMatchingStrategy`가 점수를 계산하지 않고
   `positionMatches`/`tierInRange` 통과 여부만 판단한다(`AbstractGameMatchingStrategy` + 게임별
   티어 서열표).
2. **Team Fit 점수는 12문항 설문(6영역 7축)만으로 계산한다.** 승리 지향성(25)·소통 적극성(20)·
   주도성(15)·실수 관용도(10)·플레이 집중도(10)·친목 성향(10)·나이(10) = 100점 + 파티 내 편차
   보정(±α).

### 동작함

- `POST /api/match/search` — game(필수) 외 gameMode/positions/micRequired/targetMembersOptions/limit
  전부 선택.
  - `HardFilterService`: 본인 글·마감·정원초과·게임/모집상태 제외(항상 엄격), 포지션·티어 범위·
    검색자가 고른 마이크 선호·희망 인원(항상 엄격), 마이크 "호환성"만 결과 3개 미만이면 완화
    ("완화 사다리")
  - `TeamFitCalculator`: 7축 Team Fit 점수 + 편차 보정. "파티"는 모집글 작성자 + 이미 참여 확정된
    파티원(`PostSummaryDto.members`)이다 — post 서비스가 아직 파티원 배열을 안 내려주면 작성자
    한 명으로 계산된다(아래 "알려진 한계" 참고).
  - `MatchRequest`/`MatchResult`로 검색·결과 스냅샷 저장. `MatchResult`에 축별 점수(`axesJson`)와
    1위 설명(`explanationJson`)도 같이 저장한다.
  - `ExplanationService`: 1위 결과에 대해 `{headline, reasons[], caution}` 구조의 AI 설명을 만든다.
    타임아웃 3초, `LLM_API_KEY` 미설정/실패 시 규칙 기반 문구로 즉시 대체.
- `POST /api/match/results/{resultId}/events` — 노출/클릭/지원 로그. `APPLY`면
  `MatchFoundPublisher`가 `common.event.MatchFoundEvent`를 RabbitMQ(`gamehouse.events`)로
  발행한다(아직 구독하는 서비스는 없다 — chat/crew가 붙을 계약만 먼저 나가 있는 상태).

### 자리만 잡아둠 / 아직 못 채운 것

- `domain/preference/UserPreference` — 개인화용, 아무 서비스도 아직 안 씀
- **파티원 개별 데이터.** `PostSummaryDto.members`/`PartyMemberDto`는 스키마만 만들어 뒀다 —
  post 서비스가 실제로 이 배열을 내려주기 시작하면 `TeamFitCalculator`는 코드 변경 없이 자동으로
  다인원 평균 계산으로 바뀐다. 그 전까지는 작성자 1인을 파티 전체로 취급한다. 같은 이유로
  `MatchFoundPublisher`가 발행하는 `MatchFoundEvent.memberIds`도 지금은 신청자 한 명뿐이다.
- **파티원별 나이.** 위와 같은 이유로 나이 축도 현재는 작성자 나이만 반영한다.
- Team Fit 세부 정규화 상수(`MAX_AXIS_DIFF`, 나이 감점 곡선, 편차 보정 계수)는 실사용 데이터가
  없는 상태에서 잡은 1차 값이다. STORY 03에서 실측 데이터로 보정이 필요하다.

## 왜 User/Post 엔티티를 그대로 안 쓰고 client로 호출하는가

match가 users/posts 테이블을 직접 읽으면 그 순간부터 스키마를 하나가 아니라 둘이 함께
관리해야 한다(분산 모놀리스). 그래서 `client/UserClient`, `client/PostClient`가 user/post
서비스의 기존 REST API(`GET /api/users/{id}`, `GET /api/posts`)를 그대로 호출하고, 응답을
match 쪽 DTO(`UserSummaryDto`, `PostSummaryDto`)로 옮겨 담는다. v2에서 새로 필요해진 필드
(age/personality/tierMin/tierMax/micLevel/members)도 이 두 엔드포인트의 응답 확장으로 받는다는
전제다 — 별도 신규 엔드포인트는 아직 안 만들었다.

두 client 모두 **필드가 없어도 죽지 않도록** 방어적으로 파싱한다(`JsonParsingUtils`). user/post
서비스가 아직 이 필드들을 안 내려줘도 null/빈 배열로 채워지고, `HardFilterService`는 "제한 없음"으로,
`TeamFitCalculator`는 중립값(60점)으로 처리한다.

인증은 "토큰 릴레이" 방식이다. match는 자체 로그인이 없고, 로그인한 사용자의 Authorization
헤더를 그대로 user/post 서비스에 다시 실어 보낸다. 서비스 간 전용 인증(mTLS, 내부 API 키)은
지금 범위에서는 과하다고 판단해 미뤘다.

## 로컬에서 띄우기

`backend/` 루트의 [`LOCAL-SETUP.md`](../LOCAL-SETUP.md)를 따라 5개 서비스를 함께 띄우는 것을
전제로 한다. match만 볼 때 핵심은:

```bash
cd backend
cp application-secret-example.yml application-secret.yml   # 아직 안 했다면
./gradlew :match:bootRun
```

기본 앱 포트는 8085(액추에이터는 8185)다. `services.user.base-url`/`services.post.base-url`
(기본값 http://localhost:8081, http://localhost:8082)이 맞아야 실제 추천 결과가 나온다 —
둘 다 안 띄워도 match 자체는 뜨지만, `/api/match/search`가 502("다른 서비스에서 정보를
가져오지 못했습니다")를 반환한다.

## user/post 서비스에 요청해야 하는 것

아래 응답 필드가 채워져야 v2 로직이 실제로 의미가 생긴다 — 그 전까지는 위 "왜 client로
호출하는가" 절에서 설명한 대로 안전하게 무력화된 채로 동작한다(구조적으로는 이미 연결되어
있다).

- `GET /api/users/{id}`(`common.dto.UserDto`) 응답에 `age`(number), `playDays`(string),
  `playDuration`(string), `personality`(object: winIntent/mistakeTolerance/communication/focus/
  leadership/leadershipPreference/sociability, 0~100 스케일) 추가
- `GET /api/posts`(`PostDto.Summary`) 응답의 각 item에 `tierMin`/`tierMax`(string),
  `micLevel`(string), `members`(author와 같은 모양의 배열) 추가, `item.author`에
  `age`/`personality` 추가

## 설계 문서와 다르게 잡은 부분과 이유

- `TeamCompositionService`(현재 팀 구성 조회)는 match에는 따로 만들지 않았다 — post 서비스에
  이미 `TeamCompositionService`(memberIds까지만 계산, filledRoles/avgTierIndex/styleDist는
  TODO)가 있지만 아직 HTTP로 노출돼 있지 않다. post가 파티원 배열을 내려주기 시작하면
  `PostClient`가 그대로 파싱해서 `PostSummaryDto.members`에 채우는 방식으로 흡수한다.
- `UserGameProfile`이 없어서 전략 두 개(Lol/Valorant)는 여전히 같은 범용 필드(position, tier)를
  읽는다. 티어 서열표만 게임별로 분리해 뒀다(`LolMatchingStrategy`/`ValorantMatchingStrategy`의
  `tierOrder()`) — 발로란트 역할 전용 필드가 생기면 이 두 클래스만 고치면 된다.

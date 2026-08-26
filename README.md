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

## v3: 스케일 정정 + 안 쓰이던 데이터 연결 (algoVersion `match-v3-teamfit`)

v2에서 **에러 없이 조용히 틀리고 있던** 것들을 고치고, 받아만 두고 안 쓰던 프로필 값을 점수에
연결한 버전이다. 각 항목의 근거는 해당 파일 주석에 남겨 뒀다.

**1) 점수 스케일이 실제 설문 값과 어긋나 있었다.**
`PlayStyleAxis.score()`는 `(평균-1)/4*100`으로 **0~100**(중앙 50)을 만드는데,
`TeamFitCalculator`는 "1~5점 × 20 = 20~100"(중앙 60)을 전제로 상수를 잡고 있었다.
`NEUTRAL 60 → 50`, `MAX_AXIS_DIFF 80 → 100`. 후자는 분모가 실제보다 작아 모든 축을 과하게
깎았고 차이 80 이상을 전부 0점으로 뭉갰다(85와 100이 동점이었다).

**2) 파티 평균 대신 구성원별 점수의 평균으로 계산한다.**
축마다 파티 평균을 먼저 구하면 승리 지향성 100인 방장과 0인 팀원이 서로를 상쇄해 50이 되고,
50인 검색자가 만점을 받았다. 나이도 같은 문제였다(20살+40살 파티의 평균 30 → 27살이 만점).

**3) 설문 미응답자를 평균에서 뺀다.**
v2는 미응답자를 중립값으로 채워 평균에 섞었다 — 결측이 감점처럼 작동했다. 지금은 아는 사람끼리만
비교하고, 대신 응답에 `surveyedCount`를 실어 화면이 "3명 중 2명 설문 완료"라고 말한다.

**4) 주도성을 양방향으로 본다.**
`PlayStyleAxis` 주석이 설계로 명시한 최적 조합(한쪽 INITIATIVE 높음 + 다른 쪽
INITIATIVE_PREFERENCE 높음)이 v2에서는 절반만 계산돼 점수에 잡히지 않았다.

**5) 배점 재구성 — 시간대·음성 축 신설.**
승리 지향성(20)·소통 적극성(16)·주도성(12)·실수 관용도(8)·플레이 집중도(8)·친목 성향(8)·
**플레이 시간대(15)**·**음성 채팅(5)**·나이(8) = 100점 + 파티 내 편차 보정(±α, 최대 5점).
편차 보정은 6개 성향 축을 모두 본다(v2는 4개만 봤다).

**6) 게임별 어휘를 post 쪽 `GameOptions`에 맞췄다.** — 아래 참고.

### 어휘의 기준점은 post 의 `GameOptions` 다

포지션/역할·티어 문자열은 결국 모집글에 **저장된 값**과 문자열로 비교된다. 저장을 검증하는 쪽이
`GameOptions`이므로 거기가 기준이다. v2에서는 세 군데가 어긋나 있었고, 전부 에러가 아니라
"결과 0건" 또는 "필터가 조용히 꺼짐"으로 나타났다.

| 항목 | 모집글(GameOptions) | v2의 match/프론트 | 증상 |
|---|---|---|---|
| 발로란트 역할 | 타격대/척후대/전략가/감시자 | 듀얼리스트/이니시에이터/… | 역할을 고르면 **항상 0건** |
| 발로란트 티어 | 초월 | 초월자 | 그 티어를 건 글이 티어 무관으로 통과 |
| LOL 게임 모드 | 신속/랭크/칼바람 | 일반/랭크/칼바람 | '일반' 선택 시 후보 목록 자체가 빈 배열 |

여기에 더해 `User.riotTier`는 라이엇 **영문 enum**(`"DIAMOND"`)인데 한글 서열표를 그대로
뒤지고 있었다. 못 찾으면 `tierInRange`가 "내 티어를 모른다"며 통과시키므로, **라이엇을 연동한
사용자일수록 티어 조건이 안 걸리는** 상태였다. `GameMatchingStrategy.normalizeTier()`가 세
경로(설문 한글값 / 라이엇 영문 enum / GameOptions 값)를 모두 흡수한다.

프로필의 `position`도 게임을 구분하지 않고 한 칸에 저장되므로, 검색 게임의 어휘에 속할 때만
폴백한다(`knowsPosition`). 예전에는 발로란트 검색에서 역할을 비워두면 LOL의 "정글"로 필터가
걸려 0건이 됐다 — 아무것도 안 고른 사용자가 가장 나쁜 결과를 받았다.

### 동작함

- `POST /api/match/search` — game(필수) 외 gameMode/positions/tier/micLevel/playStyle/
  targetMembersOptions/playTime/limit 전부 선택.
  - `HardFilterService`: 본인 글·마감·정원초과·게임/모집상태 제외(항상 엄격), 포지션·티어 범위·
    검색자가 고른 마이크 선호·희망 인원·플레이 스타일(항상 엄격), 마이크 "호환성"만 결과 3개
    미만이면 완화("완화 사다리")
  - `TeamFitCalculator`: 9축 Team Fit 점수 + 편차 보정. "파티"는 모집글 작성자 + 이미 참여
    확정(CONFIRMED)된 파티원(`PostSummaryDto.members`)이다 — 팀원이 둘이면 방장 포함 3명
    전원이 계산에 들어간다.
  - `MatchRequest`/`MatchResult`로 검색·결과 스냅샷 저장. `MatchResult`에 축별 점수(`axesJson`)와
    1위 설명(`explanationJson`)도 같이 저장한다.
  - `ExplanationService`: 1위 결과에 대해 `{headline, reasons[], caution}` 구조의 AI 설명을 만든다.
    타임아웃 3초, `LLM_API_KEY` 미설정/실패 시 규칙 기반 문구로 즉시 대체.
- `POST /api/match/results/{resultId}/events` — 노출/클릭/지원 로그. `APPLY`면
  `MatchFoundPublisher`가 `common.event.MatchFoundEvent`를 RabbitMQ(`gamehouse.events`)로
  발행한다(아직 구독하는 서비스는 없다 — chat/crew가 붙을 계약만 먼저 나가 있는 상태).

### 응답에 새로 실리는 값

- `results[].party` — 방장 + 확정 파티원(`PartyBrief`: userId/nickname/age/host/surveyed).
  파티원 조회는 원래도 하고 있었는데(성향을 가져오려면 id가 필요하다) 응답에 안 실어서 화면이
  "OO님의 파티"라고만 말하고 누가 있는지는 못 보여줬다. 성향 점수는 계산 전용이라 넣지 않는다.
- `results[].surveyedCount` — 그중 설문을 마친 인원. 미응답자를 감점 대신 이 숫자로 드러낸다.
- `results[].axes[].known` — 실제로 비교해서 나온 점수인가, 데이터가 없어 중립값으로 채운
  자리인가. 구분하지 않으면 아무도 안 채운 축이 중립값 50점으로 내려가면서 "60점 미만이면
  주의 문구" 규칙에 걸려, 재본 적도 없는 항목을 두고 "차이가 있어요"라고 말하게 된다.
  `ExplanationService`는 `known=false`인 축을 설명에서 빼고, 프론트는 흐리게 표시한다.
  점수 자체는 총점에 그대로 들어간다 — 축마다 배점이 고정이라 빼면 후보 간 총점을 비교할 수 없다.

### 자리만 잡아둠 / 아직 못 채운 것

- `domain/preference/UserPreference` — 개인화용, 아무 서비스도 아직 안 씀
- `RecommendationEvent` — 쓰기만 하고 읽는 코드가 없다. 재랭킹/개인화 루프 미연결.
- `MatchFoundPublisher`가 발행하는 `MatchFoundEvent.memberIds`는 아직 신청자 한 명뿐이다
  (파티원 목록은 이제 알 수 있으므로 넓힐 수 있는 상태다).
- Team Fit 세부 정규화 상수(나이 감점 곡선, 편차 보정 계수, 시간대 감점 폭)는 실사용 데이터가
  없는 상태에서 잡은 1차 값이다. STORY 03에서 실측 데이터로 보정이 필요하다.

## 왜 User/Post 엔티티를 그대로 안 쓰고 client로 호출하는가

match가 users/posts 테이블을 직접 읽으면 그 순간부터 스키마를 하나가 아니라 둘이 함께
관리해야 한다(분산 모놀리스). 그래서 `client/UserClient`, `client/PostClient`가 user/post
서비스의 기존 REST API(`GET /api/users/{id}`, `GET /api/posts`)를 그대로 호출하고, 응답을
match 쪽 DTO(`UserSummaryDto`, `PostSummaryDto`)로 옮겨 담는다. v2에서 새로 필요해진 필드
(age/personality/tierMin/tierMax/micLevel/members)도 이 두 엔드포인트의 응답 확장으로 받는다는
전제다 — 별도 신규 엔드포인트는 아직 안 만들었다.

성향 점수(`/internal/users/personality`)와 확정 파티원(`/internal/posts/party`)만은 공개 API가
아니라 **내부 전용 엔드포인트**로 받는다. 성향 점수는 "남의 프로필 조회"로 보여선 안 되는 값이고,
"이 글에 누가 확정으로 들어와 있는가"도 원래 방장만 보던 정보라 공개 응답에 얹지 않았다.

두 client 모두 **필드가 없어도 죽지 않도록** 방어적으로 파싱한다(`JsonParsingUtils`). user/post
서비스가 이 필드들을 안 내려줘도 null/빈 배열로 채워지고, `HardFilterService`는 "제한 없음"으로
넘긴다. `TeamFitCalculator`는 모르는 값을 **평균에서 빼고**, 그 축을 아무도 모를 때만 중립값
(50점 = 0~100 스케일의 중앙값)으로 떨어진다 — "모른다"를 감점으로 바꾸지 않기 위해서다.

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

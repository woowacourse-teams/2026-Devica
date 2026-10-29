# 위치 바인딩

산출물마다 저장 위치, 식별 방법, 유효 조건을 정한다. 산출물의 형식은 [artifacts/](artifacts/)가 정한다.

저장소는 GitHub 레포 `woowacourse-teams/2026-Devica` 와 PostHog 프로젝트 `638813` 이다.

## 저장 위치

| 산출물 | 저장 위치 | 식별 | 유효 조건 |
|---|---|---|---|
| [사용자 행동 지표](artifacts/user-behavior-metrics.schema.yml) | PostHog 프로젝트 `638813` 의 이벤트 | `event` 가 `recommendation_result_viewed` 또는 `recommended_spec_edited`, `$current_url` 이 운영 사이트 | — |
| [피드백](artifacts/feedback.md) | `product-recommendation-feedback` 라벨이 붙은 이슈. PR 은 제외 | 제목 머리말. `[제품 추천 의견]` 은 사용자 피드백, `[제품 추천 지표]` 는 지표 피드백 | 지표 피드백은 팀원 작성. 아니면 사용자 피드백으로 본다 |
| [분석 결과](artifacts/analysis-result.schema.yml) | 분석한 피드백 이슈의 댓글 | 첫 줄 `<!-- product-recommendation:analysis -->` | 팀원 작성. 여러 개면 가장 최근 것이 지금의 판단 |
| [개선 지시](artifacts/improvement-instruction.schema.yml) | 근거 피드백 중 번호가 가장 작은 이슈의 댓글 | 첫 줄 `<!-- product-recommendation:decision -->` | 팀원 작성 |
| [개선 결과](artifacts/improvement-result.schema.yml) | 개선 지시가 달린 이슈의 댓글 | 첫 줄 `<!-- product-recommendation:result -->` | 팀원 작성. 같은 이슈에서 이 댓글보다 앞선 개선 지시 중 가장 최근 것의 결과다 |
| [개선 PR](artifacts/improvement-pr.md) | `develop` 으로 가는 PR | 본문 첫 줄 `<!-- product-recommendation:improvement -->` | — |

팀원 작성은 `author_association` 이 `OWNER`, `MEMBER`, `COLLABORATOR` 인 작성자가 쓴 것이다. 구성 요소가 만드는 산출물은 팀원이 쓴 것만 유효하다.

## 댓글 모양

1. 첫 줄: 마커
2. 분석 결과만: 사람이 읽는 판단과 근거
3. ```` ```yaml ```` 블록 하나. [artifacts/](artifacts/)의 스키마를 따른다. 따르지 않으면 그 댓글은 산출물이 아니다

댓글 순서는 `created_at`, 같으면 `id` 다.

````markdown
<!-- product-recommendation:decision -->
**개선 지시** — MEMORY 규칙(SLOWDOWN: OFTEN)을 내림. 근거: #251, #257

```yaml
issues: [ 251, 257 ]
…
```
````

## 개선 지시의 상태

저장돼 있지 않고 개선 결과와 PR 에서 읽어 낸다.

| 상태 | 조건 |
|---|---|
| 처리 중 | 지시가 달린 이슈가 열려 있고, 개선 결과가 없거나 결과의 PR 이 열려 있다 |
| 실패 | 개선 결과가 `NOT_OPENED` |
| 반려 | 개선 결과의 PR 이 머지되지 않고 닫혔다. 반려 사유는 그 PR 의 팀원 리뷰·댓글이다 |
| 머지 | 개선 결과의 PR 이 머지됐다 |
| 중단 | 팀원이 이슈를 닫았고 개선 결과가 없다 |

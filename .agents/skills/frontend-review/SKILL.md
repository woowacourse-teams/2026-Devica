---
name: frontend-review
description: 프론트엔드(frontend/) 변경을 팀 리뷰 규칙으로 리뷰하고, 지적을 반영한 뒤 커밋에 붙일 리뷰 트레일러를 만든다. frontend/ 를 고친 뒤 push 하기 전에 사용한다.
---

# 프론트엔드 리뷰

`frontend/` 변경을 `docs/review-rules/*.md` 규칙으로 리뷰한다. 결과는 커밋 메시지의 `Frontend-Review` 트레일러로 남긴다. pre-push 훅과 CI는 이 트레일러의 diff 해시가 현재 코드와 같은지 검사한다.

아래 단계를 순서대로 모두 수행한다. 명령은 레포 루트에서 실행한다.

## 1. 리뷰 대상 확인

먼저 **기준 브랜치**를 정한다. 이 브랜치의 PR 이 머지될 브랜치다.

- PR 이 있으면 `gh pr view --json baseRefName -q .baseRefName` 로 읽고 앞에 `origin/` 을 붙인다.
- PR 이 없으면 보통 `origin/develop` 이다. 아직 머지되지 않은 다른 브랜치 위에서 작업했다면 그 브랜치(`origin/<부모 브랜치>`)다. 어느 쪽인지 확실하지 않으면 사용자에게 묻는다.

아래에서 `<기준 브랜치>`는 이 값이다. 다른 PR 위에 쌓은 브랜치를 `origin/develop` 기준으로 리뷰하면 부모 PR 의 변경까지 리뷰·린트 대상에 섞인다.

```bash
BASE=$(git merge-base <기준 브랜치> HEAD)
git diff "$BASE" -- frontend                                  # 커밋·수정된 변경
git ls-files --others --exclude-standard -- frontend           # 새로 추가된 파일
```

- 둘 다 비어 있으면 리뷰할 것이 없다고 알리고 끝낸다.
- 구현이 덜 끝났으면 먼저 마무리한다. 리뷰는 초안이 완성된 상태에서 한다.

## 2. 규칙별 병렬 리뷰

`docs/review-rules/` 안의 규칙 파일(`README.md` 제외)마다 리뷰어 서브 에이전트를 하나씩 띄워 **동시에** 실행한다. 리뷰어는 기본 서브 에이전트를 쓴다. 각 리뷰어에게 아래 지시를 그대로 전달한다. `<규칙 파일>`과 `<기준 브랜치>`는 실제 값으로 바꾼다.

> 너는 코드 리뷰어다. **파일을 수정하지 않는다.** 읽기와 조회 명령만 쓴다.
>
> 1. `<규칙 파일>`을 읽는다. 이 파일에 적힌 규칙만 본다. 다른 관점의 지적이나 린터가 잡는 것은 보고하지 않는다.
> 2. `git diff $(git merge-base <기준 브랜치> HEAD) -- frontend`와 새로 추가된 파일(`git ls-files --others --exclude-standard -- frontend`)을 본다. 필요하면 변경된 파일의 전체 내용과 호출하는 쪽 코드도 읽는다.
> 3. 규칙 위반마다 한 줄씩, 아래 형식으로만 답한다. 위반이 없으면 `지적 없음`이라고만 답한다.
>
>    `규칙 ID | 파일:줄 | 무엇이 왜 문제인지 한 문장 | 필수 또는 권장`
>
>    필수·권장 구분은 규칙 제목에 적힌 대로 따른다. 확신이 없는 지적은 보고하지 않는다.

서브 에이전트를 띄울 수 없는 환경이면, 규칙 파일을 하나씩 직접 위 지시대로 검토한다.

## 3. 지적 반영

지적마다 코드를 확인하고 상태를 하나로 정한다.

- **해결**: 코드를 고쳤다.
- **기각**: 지적이 틀렸다. 보고할 때 근거를 함께 적는다.
- **미해결**: 고치지 않았다. 권장 항목에만 쓸 수 있다. 보고할 때 이유를 함께 적는다.

필수 항목은 해결 또는 기각으로 끝내야 한다. 미해결 필수 항목이 있으면 push가 막힌다.

## 4. 린트·포맷·타입 검사

린트는 이번 변경 파일에만 돌린다. `frontend/` 전체에 돌리면 이번 작업과 상관없는 파일의 기존 에러까지 걸린다.

```bash
BASE=$(git merge-base <기준 브랜치> HEAD)
{ git diff --name-only --diff-filter=d "$BASE" -- frontend; git ls-files --others --exclude-standard -- frontend; } \
  | sed 's|^frontend/||' \
  | (cd frontend && xargs -r npx biome check --write --no-errors-on-unmatched --files-ignore-unknown=true)
(cd frontend && npx tsc --noEmit)
```

- 남은 에러는 맥락을 보고 직접 고친 뒤 다시 실행한다. 변경 파일에 있던 기존 에러도 여기에 포함된다. 그 파일을 커밋하면 pre-commit도 같은 에러로 막는다.
- 이 단계를 트레일러를 만들기 전에 해야 한다. 그래야 커밋할 때 pre-commit이 코드를 더 고치지 않아 해시가 유지된다.

## 5. 리뷰 트레일러

```bash
scripts/review-hash.sh -b <기준 브랜치>    # 해시 출력
```

아래 한 줄을 만든다. `unresolved`는 해결도 기각도 하지 않은 필수 항목 수다. 0이 아니면 push가 막힌다.

```
Frontend-Review: hash=<review-hash.sh 출력> base=<기준 브랜치> unresolved=<미해결 필수 항목 수>
```

- 트레일러를 만든 뒤에는 `frontend/` 코드를 고치지 않는다. 고쳤으면 1단계부터 다시 한다.
- 파일로 남기지 않는다. 리뷰 내용은 아래 보고로만 전달한다.

마지막으로 사용자에게 지적 목록과 상태(해결·기각·미해결, 기각·미해결은 이유), 그리고 트레일러를 보고한다. 커밋할 때 아래처럼 붙여야 한다는 것도 알린다.

```bash
git commit --trailer "Frontend-Review: hash=... base=... unresolved=0"
```

이미 커밋했다면 `git commit --amend --no-edit --trailer "..."`로 붙인다. 이미 push한 커밋이면 amend 대신 `git commit --allow-empty -m "<제목>" --trailer "..."`로 빈 커밋에 붙인다. 그러면 force push 없이 올릴 수 있다. 트레일러는 브랜치의 아무 커밋에 있어도 되고, push하는 코드의 해시와 맞기만 하면 된다.

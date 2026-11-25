---
name: create-pr
description: 현재 브랜치의 변경으로 Draft PR을 만들거나 기존 PR에 변경을 반영해 달라는 요청에 사용합니다. 예를 들어 "PR 올려줘", "PR 만들어줘"에 적용합니다. 새 PR을 만들기 전에 pr-review와 UI 변경 시 ui-review를 먼저 실행하고, 그다음 하네스 공통 create-pr 스킬 절차를 따릅니다. PR 조회나 본문 초안만 요청하면 원격 상태를 바꾸지 않습니다.
---

# Draft PR 생성 (리뷰 후 하네스 공통 스킬 연결)

이 저장소는 PR을 만들기 전에 리뷰를 거칩니다. 리뷰 단계만 여기서 정하고, PR 생성 절차는 하네스 공통 스킬 `../knk-harness/.agents/skills/create-pr/SKILL.md`를 읽고 그대로 따릅니다. 원본 안의 상대 경로(`../technical-writing/SKILL.md`, `../../../docs/templates/` 등)는 이 파일이 아니라 `../knk-harness/.agents/skills/create-pr/` 기준으로 해석합니다.

## 1. PR 전 리뷰

- 먼저 `gh pr list --state open --head <브랜치>`로 열린 PR이 있는지 봅니다. 새 PR을 만들 때만 리뷰합니다. 기존 PR에 커밋을 push만 하는 요청은 사용자가 요청했거나 변경이 클 때만 리뷰합니다.
- PR이 향할 base(기본 `dev`, 사용자 지정이나 쌓인 PR이면 그 브랜치)를 fetch한 뒤, `<base>...HEAD`와 커밋하지 않은 변경(`git status --short`, `git diff`)을 함께 리뷰 범위로 잡습니다. 리뷰어에게도 같은 base를 넘깁니다.
- 코드 리뷰는 pr-review 스킬로 합니다. Claude Code는 `pr-reviewer` 서브에이전트로 실행합니다.
- UI 변경 여부는 ui-review 스킬 1번의 대상 기준으로 판단합니다. 화면 코드나 여러 화면이 함께 쓰는 코드(`core/designsystem`, `app`의 셸과 테마, 기능 모듈의 `presentation`과 `res`)가 바뀌었으면 UI 변경이고, 데이터 계층이나 빌드 설정만 바뀌었으면 아닙니다.
- UI 변경이면 ui-review 스킬도 함께 실행합니다. Claude Code는 `ui-reviewer` 서브에이전트입니다. pr-reviewer는 에뮬레이터를 쓰지 않으므로 두 리뷰를 동시에 돌릴 수 있습니다.
- 리뷰어는 Jira와 슬랙을 읽지 못하므로, 대화에서 확인한 Jira 범위와 사용자 결정을 요청 맥락으로 함께 넘깁니다.
- 리뷰어의 지적은 사실인지 직접 확인한 뒤 사용자에게 전합니다. 결과에 따라 아래처럼 진행합니다.
  - 높음: PR을 만들지 않고 사용자에게 고칠지 묻습니다. 고치지 않고 진행하라고 하면 "리뷰 참고"에 그 지적과 진행 결정을 적습니다.
  - 보통: 사용자에게 지금 고칠지, PR 본문에 남기고 진행할지 묻습니다.
  - 낮음, 확인 필요: 본문에 남기고 진행합니다.
- 사용자가 리뷰를 생략하라고 하면 건너뛰고, 본문에 생략했다고 적습니다.

## 2. PR 생성

하네스 `create-pr/SKILL.md` 절차를 그대로 따릅니다. 이 저장소에서 더하는 것은 아래뿐입니다.

- 템플릿은 `mobile-pull-request-template.md`입니다.
- Assignee는 `alsdn1360`입니다(`--assignee alsdn1360`).
- 같은 Jira 키의 하네스 PR(`KIM-N-KANG/knk-harness`)이 있거나 이번에 함께 만들면 두 PR 본문에 서로의 링크를 넣습니다.
- 남은 리뷰 지적과 확인 필요 항목은 본문의 "리뷰 참고" 절에 적습니다. 템플릿에 없으면 검증 절 다음에 추가합니다.
- 기능 계획과 실행 결과(바꾼 결정, 검증, 복구 방법)는 본문에 남깁니다. 이 저장소에는 계획 문서를 따로 두지 않습니다.
- 화면 절에는 ui-review가 남긴 `build/ui-review/` 캡처를 넣습니다. 원본이 요구하는 기존 화면의 Before 캡처는 ui-review의 변경 전 비교로만 얻을 수 있으므로, 필요하면 리뷰가 끝난 뒤 사용자에게 비교 실행을 묻습니다. 하지 않았으면 After만 넣고 Before를 생략한 이유를 적습니다.
- 캡처는 ego-browser로 PR 댓글 폼의 첨부 기능에 올려 `user-attachments` URL을 받고 `<img width="300" ...>`로 넣습니다.
- 하네스 경로가 없으면 하네스 위치를 확인하고, 찾지 못하면 사용자에게 알립니다.

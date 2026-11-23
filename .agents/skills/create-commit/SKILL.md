---
name: create-commit
description: 로컬 변경사항을 팀 규칙에 맞춰 커밋해 달라는 요청에 사용합니다. 예를 들어 "커밋해줘", "이거 나눠서 커밋해줘"에 적용합니다. 하네스 공통 create-commit 스킬로 연결하고 이 저장소의 제목 형식과 필수 검증을 더합니다. 커밋 메시지 제안이나 검토만 요청하면 Git 상태를 바꾸지 않습니다.
---

# 커밋 생성 (하네스 공통 스킬 연결)

하네스 공통 스킬 `../knk-harness/.agents/skills/create-commit/SKILL.md`를 읽고 그 절차를 그대로 따릅니다. 원본 안의 상대 경로는 이 파일이 아니라 `../knk-harness/.agents/skills/create-commit/` 기준으로 해석합니다. 이 저장소에서 더하거나 원본보다 우선하는 것은 아래뿐입니다.

## 메시지

- 제목은 `[KNK-번호] 태그: 제목`이고 **명사형으로 끝냅니다**("~ 구현", "~ 추가", "~ 교체"). "~한다", "~함" 같은 서술형은 쓰지 않습니다. 이 규칙이 원본의 제목 설명보다 우선합니다.
- `Co-Authored-By: Claude`, "Generated with Claude Code" 같은 귀속 문구를 넣지 않습니다.
- 같은 작업의 반복 조정(크기, 색, 자산 교체)은 커밋을 쪼개지 않고 확정된 최종 상태로 하나만 남깁니다. 값을 바꿔 보는 중이면 커밋을 미룹니다.

## 필수 검증

원본 5번이 말하는 프로젝트 필수 검증은 CLAUDE.md "검증" 절입니다.

- 코드를 바꾼 모듈마다 `./gradlew :<core|feature>:<모듈>:ktlintCheck :<core|feature>:<모듈>:detekt :<core|feature>:<모듈>:testDebugUnitTest`를 실행합니다. 앱은 `:app`입니다.
- `build-logic`, 버전 카탈로그, 루트 Gradle 설정처럼 공통 빌드 로직을 바꿨으면 `./gradlew check`도 실행합니다. 그 밖의 전체 게이트는 PR CI가 맡으므로 커밋 때 반복하지 않습니다.
- 모듈을 추가, 삭제, 이름 변경했으면 `settings.gradle.kts`, `.idea/gradle.xml`, `build-logic`의 `ModuleArchitecture.kt`가 같은 커밋에 있는지 보고 `:build-logic:ktlintCheck`를 실행합니다.
- 문서와 스킬만 바꿨으면 Gradle을 돌리지 않고 경로와 링크를 확인합니다.

## stage

- `git add -A` 대신 `git add -- <경로>`로 검토한 파일만 stage합니다. `git rm`으로 지운 경로가 섞이면 `git add -A -- <경로>`를 쓰고, 커밋 전에 `git diff --cached --stat`으로 빠진 파일이 없는지 확인합니다.
- 사용자가 IDE에서 같은 파일을 고치고 있을 수 있으므로 stage 직전에 diff를 다시 읽습니다. `.idea/` 아래처럼 IDE가 바꾼 파일은 요청 범위가 아니면 stage하지 않습니다.
- push는 사용자가 요청했을 때만 합니다.

하네스 경로가 없으면 하네스 위치를 확인하고, 찾지 못하면 사용자에게 알립니다.

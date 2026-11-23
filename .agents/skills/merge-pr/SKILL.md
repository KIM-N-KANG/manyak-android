---
name: merge-pr
description: manyak-android의 기능 PR을 머지해 달라는 요청에 사용합니다. 예를 들어 "이 PR 머지해줘", "#160 머지하고 정리해줘", "쌓인 PR 순서대로 머지해줘"에 적용합니다. CI가 끝날 때까지 기다려 squash merge하고, 위에 쌓인 PR의 base를 옮기고, 머지 뒤 원격과 로컬 브랜치, 그 브랜치의 worktree, Jira 상태를 정리합니다. 머지할 수 있는지 확인만 요청하면 원격 상태를 바꾸지 않습니다. `release/*`와 base가 `main`인 PR은 이 스킬로 머지하지 않습니다.
---

# PR 머지와 정리 (manyak-android)

## 규칙

- 머지는 사용자가 요청한 PR에만 합니다. 번호가 없으면 현재 브랜치의 열린 PR이 대상이고, 대상을 정할 수 없을 때만 질문합니다. 쌓인 PR을 순서대로 머지해 달라는 요청은 `dev`에 가까운 아래 PR부터 처리합니다.
- head가 `release/*`이거나 base가 `main`(`hotfix/*` 포함)이면 이 스킬로 머지하지 않습니다. `main` ruleset은 머지 커밋만 허용하고, 릴리스는 `main`과 `dev`에 같은 브랜치로 PR 둘을 머지 커밋으로 넣습니다. 하네스 `docs/operations/DEPLOYMENT.md`의 7-2 Android 앱 릴리스, 7-3 운영 긴급 수정과 release-ops 스킬을 따릅니다.
- 방식은 squash merge 하나입니다. 저장소 설정은 세 방식을 모두 허용하고 `dev` ruleset은 squash와 머지 커밋을 허용하지만, 기능 PR에는 squash만 씁니다.
- 커밋 메시지는 GitHub 기본 메시지를 그대로 씁니다. 저장소 설정상 커밋이 하나면 그 커밋 제목, 여러 개면 PR 제목이 제목이 되고 본문에는 커밋 메시지 목록이 들어갑니다. `--subject`, `--body`, `--body-file`을 주지 않습니다.
- 머지 요청에는 CI 대기, Draft를 Ready로 바꾸기, 쌓인 PR의 base 변경, 머지 뒤 브랜치와 worktree 정리, Jira 완료 전환이 포함됩니다. 릴리스와 스토어 배포는 포함하지 않습니다.
- 저장소가 auto-merge를 허용하지 않으므로 `--auto`를 쓰지 않습니다. `--admin`으로 검사를 우회하지 않습니다. 요청 없이 force push하지 않습니다.
- 같은 Jira 키의 하네스 PR(`KIM-N-KANG/knk-harness`)은 사용자가 함께 머지해 달라고 했을 때만 머지합니다.

## 작업 흐름

1. 대상 PR을 조회합니다. 조회 실패를 PR 없음으로 취급하지 않습니다.

   ```bash
   gh pr view <번호> --json number,url,title,state,isDraft,baseRefName,headRefName,headRefOid,mergeable,mergeStateStatus,statusCheckRollup,body
   ```

   - `state`가 `MERGED`면 머지는 건너뛰고 6번부터 정리만 합니다. `CLOSED`면 머지하지 않고 상태를 보고합니다.
   - `git worktree list --porcelain`에서 `branch refs/heads/<head>`인 항목을 찾습니다. 첫 항목(기본 체크아웃)이 아니면 그 경로가 6번에서 정리할 worktree입니다.
   - 위에 쌓인 PR을 찾습니다: `gh pr list --state open --base <head> --json number,title,headRefName`.

2. 머지 전에 확인합니다. 하나라도 걸리면 머지하지 않고 이유를 보고합니다.
   - 로컬 head 브랜치에 커밋하지 않은 변경이나 push하지 않은 커밋이 있는지 봅니다(`git status --short --branch`, 로컬 끝 커밋과 `headRefOid` 비교). worktree에 체크아웃돼 있으면 `git -C <worktree> status --short --branch`로 봅니다. 사용자가 IDE에서 바꾼 `.idea/` 파일처럼 PR과 무관한 변경은 보고만 하고 넘어갑니다. PR에 빠진 변경일 수 있는 것만 확인받습니다.
   - base가 `dev`인지 확인합니다. 다른 기능 브랜치가 base면 아래 PR이 먼저 머지돼야 합니다. Android CI는 base가 `dev`나 `main`인 PR에서만 돌기 때문에, 쌓인 PR에는 아직 CI 결과가 없습니다.
   - CI를 `gh pr checks <번호>`로 직접 확인합니다. 검사는 Android CI의 Check and build(`./gradlew check`, `./gradlew assembleDebug`) 하나입니다. ruleset에 필수 검사가 없어 GitHub은 검사가 실패한 PR의 머지도 막지 않습니다. 진행 중이면 `gh pr checks <번호> --watch --fail-fast`로 끝날 때까지 기다리고, 기다리는 동안 사용자에게 멈춰서 묻지 않습니다. 사용자가 기다리지 말고 머지하라고 하면 그대로 하되 확인하지 못한 검사를 보고합니다.
   - 검사가 실패했으면 머지하지 않습니다. `gh run view <run-id> --log-failed`로 실패한 작업과 원인(ktlint, detekt, lint, 단위 테스트, 모듈 검사, 조립)을 요약해 보고합니다. 수정은 따로 요청받아 합니다.
   - `mergeable`이 `CONFLICTING`이면 머지하지 않습니다. `UNKNOWN`이면 잠시 뒤 다시 조회합니다. 충돌 해결은 따로 요청받아 합니다.
   - PR 본문의 검증과 체크리스트에서 체크되지 않은 항목을 봅니다. "해당 없음"이나 "PR CI로 확인"처럼 이유가 적힌 항목은 넘어가고, 이유 없이 비어 있거나 남은 일로 적힌 항목만 머지 전에 알립니다. 사용자가 이미 알고 머지를 요청했다면 다시 확인하지 않습니다.
3. 위에 쌓인 PR이 있으면 머지 전에 base를 `dev`로 옮깁니다: `gh pr edit <위 PR 번호> --base dev`. 아래 PR의 브랜치를 먼저 지우면 위 PR은 retarget되지 않고 닫히며, 그 뒤 head를 바꾸면 다시 열 수 없습니다.
4. Draft면 `gh pr ready <번호>`로 바꿉니다.
5. 확인한 head 커밋으로 머지합니다. 저장소의 머지 후 브랜치 자동 삭제가 꺼져 있으므로 `--delete-branch`를 붙입니다. 응답이 불확실하면 `gh pr view <번호> --json state,mergeCommit`으로 다시 조회한 뒤 판단합니다.

   ```bash
   gh pr merge <번호> --squash --delete-branch --match-head-commit <headRefOid>
   ```

   - head 브랜치가 worktree에 체크아웃돼 있으면 `--delete-branch`를 뺍니다. git이 그 로컬 브랜치를 지우지 못해 gh가 오류로 끝나고 원격 브랜치 삭제도 건너뛸 수 있습니다. 브랜치는 7번에서 지웁니다.

6. 1번에서 찾은 worktree가 있으면 정리합니다.
   - 이 세션이 그 worktree 안에 있으면 먼저 기본 체크아웃으로 나옵니다. 나올 수 없는 worktree(앱이 세션에 만들어 준 것)면 그 worktree에서 띄운 프로세스만 끄고 worktree는 남긴 채 세션을 보관하면 앱이 정리한다고 보고합니다.
   - 그 worktree를 작업 폴더로 쓰는 프로세스를 찾습니다. 경로는 `git worktree list`가 보여 주는 실제 경로를 씁니다. 출력의 COMMAND와 PID 열을 봅니다. 스킬을 인자와 함께 부르면 본문의 `$` 뒤 숫자가 인자로 바뀌므로, 이 스킬의 명령에는 그런 표기를 쓰지 않습니다.

     ```bash
     lsof -a -d cwd +D "<worktree>" 2>/dev/null
     ```

   - Claude Code 세션(`claude`)과 대화형 셸(`zsh`, `bash`)은 둡니다. Gradle 데몬과 Kotlin 데몬은 같은 버전을 쓰는 다른 체크아웃과 공유되므로 끄지 않고, `./gradlew --stop`도 실행하지 않습니다. 그 밖에 그 worktree에서 띄운 프로세스만 `kill <PID>`로 하나씩 끕니다.
   - 기본 체크아웃에서 `git worktree remove <worktree>`로 지웁니다. `--force`는 쓰지 않습니다. 커밋하지 않은 변경이나 추적하지 않는 파일이 있거나 잠겨 있어 거부되면 지우지 않고 그 내용을 보고해 확인받습니다. `build/`, `.gradle/`, `local.properties`처럼 gitignore된 파일은 제거를 막지 않습니다.

7. 브랜치를 정리합니다.
   - 기본 체크아웃이 head 브랜치에 있었으면 `dev`로 전환해 `git pull --ff-only`로 맞춥니다. 다른 작업 브랜치에 있으면 전환하지 않습니다. 어느 경우든 `git fetch origin --prune`으로 지워진 원격 브랜치 참조를 정리합니다.
   - 원격 head 브랜치가 남아 있으면(`git ls-remote --heads origin <head>`) `git push origin --delete <head>`로 지웁니다.
   - 로컬 head 브랜치가 남아 있으면 지웁니다. squash merge라서 `git branch -d`가 거부하므로, 로컬 끝 커밋이 `headRefOid`와 같을 때만 `git branch -D <head>`를 씁니다. 다르면 지우지 않고 보고합니다.
8. 3번에서 base를 옮긴 위 PR을 정리합니다. 위 브랜치에는 squash되기 전 아래 PR의 커밋이 그대로 있어 diff에 아래 변경이 함께 보이고, base 변경만으로는 CI가 다시 돌지 않습니다.
   - 위 PR도 머지 요청 범위에 있으면 위 브랜치에서 `git merge origin/dev`로 `dev`를 합칩니다. 충돌하면 위 브랜치 쪽을 채택하고 `git diff origin/dev...HEAD`가 위 PR의 변경만 담는지 확인한 뒤 일반 push합니다. push로 CI가 다시 돌면 2번부터 같은 절차로 머지합니다.
   - 범위 밖이면 base를 옮긴 사실과 위 방법을 보고만 합니다.
9. Jira를 정리합니다. 키는 PR 제목의 `[KNK-{번호}]`에서 읽습니다.
   - 같은 키로 열린 앱 PR(`gh pr list --state open --search "KNK-{번호} in:title"`)이나 하네스 PR(`gh pr list -R KIM-N-KANG/knk-harness --state open --search "KNK-{번호} in:title"`)이 남아 있거나, 이슈 설명의 범위 중 이번 PR이 다루지 않은 작업이 남아 있으면 완료로 바꾸지 않고 남은 것을 보고합니다.
   - 그렇지 않으면 이슈를 조회해 상태가 `완료`가 아닐 때만 전환 목록에서 **목표 상태가 `완료`인 전환 ID**를 찾아 실행하고 결과를 다시 조회합니다. 현재 KNK 워크플로에서는 `완료`(id 31)가 동작하고, `작업 완료`(id 4)는 권한 오류로 실패합니다.
   - Jira 전환이 실패해도 머지와 정리를 되돌리지 않고 남은 조치로 보고합니다.
10. 마지막으로 PR 상태와 squash 커밋, 원격과 로컬 브랜치 삭제, `git worktree list`, Jira 상태, `git status --short --branch`를 다시 확인합니다.

## 완료 보고

PR 번호와 제목, squash 커밋 해시와 메시지 제목, 기다린 검사와 결과, base를 옮긴 쌓인 PR, 지운 브랜치, 지운 worktree와 끈 프로세스, Jira 상태, 남은 로컬 변경을 간결하게 보고합니다. 확인하지 못한 검사나 건너뛰거나 실패한 정리 단계를 숨기지 않습니다.

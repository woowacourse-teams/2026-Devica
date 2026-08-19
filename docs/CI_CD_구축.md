# CI/CD와 배포 자동화

## 0. 이 문서에 대하여

코드 변경부터 빌드·테스트·배포까지를 자동화하면서 **무엇을 선택했고 그 대가로 무엇을 포기했는지**를 남긴다.

| 구분 | 상태 |
|---|---|
| CI | 구축 완료 (`.github/workflows/ci.yml`, PR #7 머지) |
| 배포 실행 구성 | 구축 완료 (`Dockerfile`, `docker-compose.prod.yml`, `.env.prod.example`) |
| CD | 구축 완료 (`buildspec.yml`, `appspec.yml`, `scripts/`) |

로컬 개발 환경은 [개발 환경 가이드](./개발_환경_가이드.md)에서 다룬다.

---

## 1. 왜 자동화하는가

### CI

빌드와 테스트를 각자 손으로 확인하는 방식에 의존하면 `main`, `develop`에 **컴파일조차 되지 않는 코드가 들어갈 수 있다.** 배포 서버는 이 브랜치를 기준으로 빌드하므로, 통합 시점의 검증이 빠지면 곧 배포 실패로 이어진다. 통합 과정의 검증을 사람의 성실함에 의존하지 않게 만드는 것이 목적이다.

### CD

CD가 없으면 배포는 다음 과정을 매번 손으로 반복하는 일이다.

1. SSM으로 EC2 접속
2. 코드 pull
3. 빌드
4. 컨테이너 재기동

스크립트로 3~4번을 줄일 수는 있지만 **"서버에 접속해야 한다"는 제약은 남는다.** 게다가 현재 EC2는 SSH가 열려 있지 않고 SSM 접근 권한도 인스턴스 생성 계정에 묶여 있어, **그 한 사람이 자리에 없으면 팀 전체의 배포가 멈춘다.** 자동화의 목적은 반복 작업 제거보다도 이 병목을 없애는 데 있다.

---

## 2. 전체 구성

CI는 GitHub Actions, CD는 AWS CodePipeline으로 나눠져 있다.

```
개발자 ── PR / Push ──┐
                      ▼
┌───────────────────────────────────────────────┐
│ GitHub Actions — CI                           │
│   트리거: main·develop의 PR, Push             │
│   체크아웃 → JDK 21 → ./gradlew build         │
│        └ MySQL 8.4 service 컨테이너 :3307     │
└───────────────────────────────────────────────┘

                  Push (웹훅)
                      ▼
┌───────────────────────────────────────────────┐
│ AWS CodePipeline — CD  (devica-dev-pipe)      │
│                                               │
│  Source   GitHub → 소스 zip을 S3에 저장       │
│     ▼                                         │
│  Build    CodeBuild (ARM, privileged)         │
│           buildspec.yml 실행                  │
│           테스트 → jar → 이미지 → image.tar   │
│           배포 번들을 S3에 저장               │
│     ▼                                         │
│  Deploy   CodeDeploy                          │
│           EC2의 에이전트가 번들을 내려받아    │
│           appspec.yml대로 훅 실행             │
└───────────────────────────────────────────────┘
                      ▼
┌───────────────────────────────────────────────┐
│ AWS                                           │
│   ALB  :443 (TLS 종료)                        │
│     │                                         │
│     ▼                                         │
│   EC2  :80   Ubuntu 26.04 / ARM64             │
│   └ docker compose -f docker-compose.prod.yml │
│         ├ devica-app  → 8080                  │
│         └ devica-db   MySQL 8.4 (+ volume)    │
└───────────────────────────────────────────────┘
```

EC2는 **S3하고만 통신한다.** GitHub 자격증명도, 소스도, 빌드 도구도 서버에 두지 않는다.

**핵심적으로 필요한 단계만 둔다.** 정적 분석, 커버리지 리포트, 슬랙 알림 등은 실제로 불편을 느낀 시점에 추가한다.

---

## 3. 기술 선택과 근거

### 3-1. CD 도구 — AWS CodePipeline

CI와 같은 GitHub Actions로 CD까지 하는 방안을 먼저 검토했으나, **GitHub Actions가 AWS를 조작하려면 자격증명이 필요하다**는 벽에 부딪혔다. 팀 계정은 액세스 키 발급이 막혀 있고, OIDC 방식은 IAM 자격증명 공급자와 역할을 새로 만들어야 하는데 그 권한도 없다.

CodePipeline은 AWS 안에서 도는 서비스라 이 문제가 없다. 각 서비스가 자기 서비스 역할로 움직인다.

| | AWS CodePipeline | GitHub Actions |
|---|---|---|
| AWS 접근 | 서비스 역할로 자동 처리 | 액세스 키 또는 OIDC 역할 필요 |
| 러너 아키텍처 | ARM 네이티브 지원 | x86. arm64는 QEMU 에뮬레이션 필요 |
| 설정 위치 | 콘솔 + 저장소의 spec 파일 | 저장소의 워크플로 파일 |
| 파이프라인 이력 | 단계별 실행 화면 제공 | 워크플로 실행 로그 |

대가는 **설정이 저장소와 콘솔에 나뉜다**는 점이다. `buildspec.yml`과 `appspec.yml`은 코드로 남지만, 빌드 이미지·권한·트리거 브랜치는 콘솔에만 있어 변경 이력이 남지 않는다.

CI는 GitHub Actions에 그대로 둔다. PR 단계 검증은 GitHub 이벤트와 붙어 있는 편이 자연스럽다.

### 3-2. 이미지 전달 — 레지스트리 대신 배포 번들

CodeBuild가 만든 이미지를 `docker save`로 `image.tar`에 담아 배포 번들에 싣고, EC2에서 `docker load`로 올린다. GHCR이나 ECR 같은 레지스트리를 쓰지 않는다.

**CodeDeploy가 어차피 S3를 거쳐 EC2로 번들을 나르기 때문이다.** 통로가 이미 있는데 이미지만 따로 창고를 두면 자격증명과 관리 대상이 하나 늘어난다.

대가는 **번들이 무겁다는 것**이다. `image.tar`가 126MB 수준이고, S3를 두 번 왕복한다(빌드가 올리고, 배포가 내려받는다). 배포 시간이 십수 초 늘고 S3에 번들이 쌓인다.

이미지가 커지거나 배포가 잦아지면 **ECR로 옮기는 것이 다음 선택지다.** 같은 계정 안이라 별도 비밀값이 없고, 같은 리전이라 전송료도 없다. 다만 EC2 인스턴스 역할과 CodeBuild 역할 양쪽에 ECR 권한을 붙여야 한다.

### 3-3. 이미지 태그 — 커밋 SHA

`latest`는 어떤 코드가 돌고 있는지 서버에서 확인할 방법이 없고, 롤백 대상을 지정할 수도 없다. 커밋 SHA 앞 7자리를 태그로 쓰면 **실행 중인 컨테이너와 커밋이 1:1로 대응한다.**

CodePipeline이 Build 액션에 `COMMIT_ID` 환경 변수(`#{SourceVariables.CommitId}`)를 넘겨주고, `buildspec.yml`이 이를 `image-tag.txt`에 적어 번들에 싣는다. **SHA를 알 수 없으면 빌드를 중단한다.** 태그 없이 배포하면 무엇이 떠 있는지 추적할 수 없기 때문이다.

### 3-4. 빌드 아키텍처 — CodeBuild ARM 이미지

EC2가 ARM64(Graviton)다. `docker build`는 빌드하는 머신의 아키텍처로 이미지를 고정하므로, **x86에서 만든 이미지는 EC2에서 `exec format error`로 실행되지 않는다.**

CodeBuild 프로젝트의 환경 이미지를 `aws/codebuild/amazonlinux-aarch64-standard`로 지정해 네이티브로 빌드한다. 기본값이 `x86_64`라서 명시적으로 바꿔야 한다.

이 실패는 **빌드가 성공한 뒤 배포 단계에서 드러나기 때문에** 원인을 찾기 어렵다.

`docker build`를 쓰므로 프로젝트의 **권한이 있음(privileged)** 옵션도 켜야 한다.

### 3-5. 배포 실행 경로 — CodeDeploy 에이전트

SSH가 열려 있지 않다는 제약에서 출발한다.

| 방식 | 판단 |
|---|---|
| **CodeDeploy 에이전트** | 채택. EC2의 에이전트가 S3에서 번들을 **직접 내려받는다.** 인바운드 포트를 열지 않고, 권한이 개인 계정에서 인스턴스 역할로 옮겨가 1절의 병목이 해소된다 |
| SSM Send-Command | 배포를 밖에서 밀어 넣는 방식. GitHub Actions에서 호출하려면 AWS 자격증명이 필요해 3-1의 문제가 그대로 남는다 |
| SSH + 액션 | 22번 포트를 열고 개인 키를 Secrets에 넣어야 한다. 보안 노출 면에서 후퇴 |

에이전트가 당기는 방식이라 **배포 이력과 롤백을 CodeDeploy가 관리한다.** 직접 구현할 필요가 없다.

### 3-6. 이미지 빌드 방식 — 빌드된 jar를 COPY

`Dockerfile`은 멀티스테이지로 Gradle을 돌리지 않고, 이미 빌드된 `build/libs/*.jar`를 COPY한다.

CodeBuild가 이미 `./gradlew build`로 테스트와 jar를 만든다. Dockerfile 안에서 Gradle을 또 돌리면 **같은 빌드를 두 번 하는 셈이다.** 대가로 `docker build` 단독으로는 이미지가 만들어지지 않는다 — `./gradlew build`가 선행되어야 한다.

`build.gradle`에서 plain jar 생성을 껐다. `build`가 jar를 두 개 만들면 `COPY build/libs/*.jar`가 대상을 특정하지 못해 실패한다.

---

## 4. 배포 대상 환경

### 4-1. EC2

| 항목 | 값 | 근거 |
|---|---|---|
| 이름 | `ec2-devica-prod-be` | |
| OS | Ubuntu Server 26.04 LTS | LTS 중 최신. 지원 기간을 길게 확보 |
| 아키텍처 | 64비트 ARM (Graviton) | 동급 x86 대비 시간당 요금이 낮고 성능/가격비가 좋다 |

ARM은 일부 Docker 이미지가 arm64 매니페스트를 제공하지 않는다는 제약이 있다. 현재 쓰는 이미지(temurin, mysql)는 모두 지원하므로 문제가 없지만, **의존성을 추가할 때마다 확인해야 하는 항목이 하나 늘었다.** 3-4의 빌드 환경 제약도 여기서 파생된다.

> Ubuntu 26.04는 CodeDeploy 에이전트의 공식 지원 목록에 없다. 설치는 되었으나 **다음 우분투 버전에서 다시 막힐 수 있는 항목**이다.

### 4-2. ALB (비교 대상: Nginx)

사용자 유입을 위해 TLS 기반 HTTPS는 필수라고 판단했고, 인증서를 발급·갱신할 주체가 필요했다. 무중단 배포 시에도 트래픽을 옮겨줄 지점이 필요하므로, 인프라를 구성하는 지금 함께 넣는 것이 적절하다고 보았다.

| | ALB | Nginx (컨테이너) |
|---|---|---|
| 인프라 비용 | **시간당 과금 발생** | 없음 (EC2 안에서 실행) |
| 인증서 | ACM이 발급·자동 갱신 | Certbot 설정·갱신을 직접 구성 |
| 무중단 배포 | 타깃 그룹 전환 | 설정 파일을 서버에서 직접 관리 |
| 커스터마이징 | 제한적 | 자유도 높음 |

Nginx는 비용이 들지 않고 커스터마이징이 자유롭지만, **인증서 갱신과 무중단 배포 설정 파일이 모두 EC2 내부의 수동 관리 대상이 된다.** 갱신 실패는 곧 서비스 접속 불가이므로, 관리 포인트를 늘리는 대신 비용을 지불하기로 했다. 세밀한 라우팅 제어가 필요해지면 그때 Nginx를 앞단에 두는 것을 다시 검토한다.

---

## 5. CI — 구축 완료

`.github/workflows/ci.yml`

### 실행 조건

| 트리거 | 대상 브랜치 |
|---|---|
| `pull_request` | `main`, `develop` |
| `push` | `main`, `develop` |

PR만으로 충분해 보이지만 Push도 포함했다. **머지 커밋 자체나 직접 푸시된 커밋은 PR 이벤트로 잡히지 않기 때문에**, 통합된 결과물이 검증되지 않은 채 남는 경우를 막는다.

### 구성

| 설정 | 내용 | 이유 |
|---|---|---|
| `concurrency` + `cancel-in-progress` | 같은 브랜치에 새 커밋이 오면 진행 중인 실행 취소 | 이미 낡은 검증에 러너를 쓰지 않는다 |
| `permissions: contents: read` | 최소 권한 | CI는 읽기만 필요하다 |
| MySQL 8.4 service 컨테이너 (3307) | 테스트 DB | H2 대신 **운영과 같은 DBMS**로 검증한다. 방언 차이로 인한 "로컬은 되는데 배포하면 안 되는" 문제를 줄인다 |
| `./gradlew build` | 컴파일 + 테스트 | 별도 태스크로 쪼개지 않고 한 번에 확인한다 |

> service 컨테이너의 포트 `3307`은 `src/test/resources/application.yml`, `buildspec.yml`의 `TEST_DB_PORT`와 맞춰져 있다. 한쪽만 바꾸면 없는 DB에 붙어 실패한다.

---

## 6. CD — 구축 완료

### AWS 리소스

| 리소스 | 이름 |
|---|---|
| 파이프라인 | `devica-dev-pipe` (V2) |
| 빌드 프로젝트 | `devica-dev-build` |
| 배포 애플리케이션 | `devica-dev` |
| 배포 그룹 | `devica-dev-dg` |
| 아티팩트 버킷 | `codepipeline-ap-northeast-2-878284509723` / `devica-dev-pipe/` |

### 저장소의 파일

| 파일 | 읽는 주체 | 역할 |
|---|---|---|
| `buildspec.yml` | CodeBuild | 무엇을 어떤 순서로 실행하고, 결과물 중 무엇을 다음 단계로 넘길지 |
| `appspec.yml` | EC2의 codedeploy-agent | 번들을 어디에 풀고, 배포 생명주기의 어느 시점에 무엇을 실행할지 |
| `scripts/deploy.sh` | 〃 (`AfterInstall` 훅) | 이미지 load, compose 파일 반영, 컨테이너 교체 |
| `scripts/validate.sh` | 〃 (`ValidateService` 훅) | 헬스 엔드포인트 폴링 |

`appspec.yml`은 **이름과 위치가 강제된다.** 번들 루트에 이 이름으로 있어야 한다. `buildspec.yml`은 기본값이라 콘솔에서 바꿀 수 있다.

### 단계

1. **Source** — GitHub 웹훅으로 push를 감지해 소스 zip을 S3에 저장
2. **Build** — CodeBuild가 `buildspec.yml` 실행
   - 테스트용 MySQL 컨테이너 기동 → `./gradlew build` → `docker build` → `docker save`
   - `appspec.yml`, `image.tar`, `image-tag.txt`, `docker-compose.prod.yml`, `scripts/*`를 번들로 묶어 S3에 저장
3. **Deploy** — EC2의 에이전트가 번들을 내려받아 `/opt/app/release`에 풀고 훅 실행
   - `deploy.sh` — `docker load` → `.env`의 `IMAGE_TAG` 갱신 → `docker compose up -d`
   - `validate.sh` — actuator 헬스 응답이 `UP`이 될 때까지 폴링

`docker compose up -d`는 이미지가 바뀐 컨테이너만 재생성한다. `depends_on`이 DB의 `service_healthy`를 기다리므로, **MySQL 초기화 전에 앱이 먼저 떠서 커넥션에 실패하는 문제는 발생하지 않는다.**

### 배포 그룹 설정

| 항목 | 값 | 이유 |
|---|---|---|
| 배포 유형 | 현재 위치(In-place) | 블루/그린은 인스턴스가 둘 이상이어야 의미가 있다 |
| 대상 지정 | EC2 태그 `Name` = `ec2-devica-prod-be` | |
| 배포 구성 | `CodeDeployDefault.AllAtOnce` | 인스턴스가 하나라 다른 구성과 결과가 같다 |
| 로드 밸런서 | 사용 안 함 | 켜면 배포 중 타깃 그룹에서 인스턴스를 뺐다 넣는데, 등록 해제 대기 시간(기본 300초)이 더해져 **중단 시간이 오히려 길어진다** |
| 실패 시 롤백 | 사용 | `validate.sh`가 스스로 롤백하지 않으므로 필수 |

### 훅 선택

`ApplicationStop` 훅은 두지 않는다. 컨테이너를 미리 내리면 **헬스체크 실패로 롤백하는 동안 서비스가 더 오래 죽어 있다.**

### 롤백

`validate.sh`는 실패하면 `exit 1`만 한다. **롤백은 CodeDeploy가 한다.**

배포 그룹의 "실패 시 롤백"이 켜져 있으면 이전 성공 리비전을 재배포한다. 그 번들에는 그때의 `image-tag.txt`와 `image.tar`가 그대로 들어 있어 **정확히 이전 이미지로 돌아간다.** CodeBuild를 다시 돌리지 않으므로 빠르고, 재빌드가 깨져서 되돌아가지 못하는 상황도 없다.

수동 롤백은 CodeDeploy에서 이전 리비전을 지정해 배포를 생성하면 된다.

DB 스키마 변경이 포함된 배포는 이 방식으로 되돌아가지 않는다. 애플리케이션만 이전 버전이 되고 **스키마는 새 상태로 남기 때문**이다.

---

## 7. 검증 결과

| 항목 | 결과 |
|---|---|
| 파이프라인 3단계 통과 | 성공 (실행 `b88dab1f`, 9분 39초) |
| 배포 대상 인스턴스 | 1/1 성공 |
| `ValidateService` 통과 | 53초 |
| 이미지 태그와 커밋 일치 | `/opt/app/.env`의 `IMAGE_TAG`로 확인 |

빌드 실패 원인은 CodeBuild 로그, 배포 실패 원인은 CodeDeploy 이벤트와 EC2의 `docker compose logs`로 확인한다. `validate.sh`는 실패 시 앱 컨테이너 로그 마지막 50줄을 함께 출력한다.

---

## 8. 한계와 개선 방향

| | 한계 | 개선 방향 |
|---|---|---|
| 1 | **CodeBuild 로그가 꺼져 있다.** 서비스 역할에 CloudWatch Logs 권한이 없어 켜면 빌드가 실패한다. 지금은 빌드가 깨져도 `exit status 1`만 보인다 | 계정 관리자에게 `logs:CreateLogGroup`, `logs:CreateLogStream`, `logs:PutLogEvents` 권한 요청 |
| 2 | 빌드 이미지·권한·트리거 브랜치가 **콘솔에만 있어 변경 이력이 남지 않는다** | 관리 대상이 늘면 IaC(CloudFormation, Terraform) 도입 검토 |
| 3 | **무중단 배포가 아니다.** `up -d`로 컨테이너를 교체하는 동안 요청이 끊긴다 | 인스턴스를 늘리고 ALB 타깃 그룹 전환 방식 도입 |
| 4 | 운영 DB가 앱과 같은 EC2의 컨테이너다. **인스턴스를 재생성하면 데이터가 사라지고, 백업 방안이 없다** | 사용자 데이터가 쌓이기 전에 RDS 이전 또는 볼륨 백업 |
| 5 | `image.tar`가 126MB라 배포마다 S3에 쌓인다. 버킷이 계정 공용이라 **팀이 직접 정리 규칙을 걸 수 없다** | 관리자에게 `devica-dev-pipe/` 접두사 한정 수명 주기 규칙 요청, 또는 ECR 이전 |
| 6 | 배포 결과를 콘솔에서만 알 수 있다 | 실패 알림을 팀 채널로 연동 |
| 7 | 스키마 변경이 포함된 배포는 롤백되지 않는다 | 마이그레이션 도구(Flyway) 도입 검토 |
| 8 | 테스트 DB 포트가 세 곳(CI, compose, 테스트 설정)에 중복돼 있다 | 한 곳에서 관리할 방법 검토 |

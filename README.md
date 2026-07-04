# Passfolio — Backend

![Passfolio](.github/assets/readme-cover.png)

<br>

# Project Introduction

## What

**"프로젝트 경험을, 증명 가능한 포트폴리오로."**<br>
**Passfolio** — Pass(합격) + Portfolio(포트폴리오), 이름 그대로 **'합격하는 포트폴리오'** 를 지향합니다.<br>
개발자 취업 준비생의 **GitHub 프로젝트를 분석**하고, 이를 근거로 기존 포트폴리오를 **개선**하거나 새 포트폴리오의 **방향을 설계**해주는 서비스입니다.   
본 레포지토리는 해당 서비스의 **Backend(API Server · Orchestration Hub)** 를 담당합니다.

## Why

- AI발 기술 변화와 경력직 선호 속에 '쉬었음' 청년은 6년간 **약 32% 증가**(36.0만 → 47.7만 명)했고, Z세대 **92%** 가 "취업 문턱이 높아졌다"고 답했습니다.
- 채용 평가의 무게중심은 스펙에서 **'증명된 실력' — 포트폴리오·실증**으로 이동하고 있습니다.
- 그런데 정작 구직 개발자의 **57.5%** 가 "내용·성과의 논리적 정리"를, **52.5%** 가 "기여도 명시"를 가장 어려워합니다. 어려움의 본질은 디자인이 아니라 **내용 구조화**입니다.
- 기존 서비스는 문장 '교정'(첨삭)이나 git 메타데이터 기반 '템플릿'에 머물러, **개발자 포트폴리오의 '내용'에 특화된 서비스는 공백**이었습니다.

## Service Purpose

- **프로젝트 분석** : GitHub 저장소를 clone하여 코드 기여도·기술 스택·핵심 성과를 추출한 분석 리포트 생성
- **포트폴리오 개선·설계** : 분석 결과를 근거로 기존 포트폴리오 개선 및 신규 포트폴리오 방향 설계
- **성장 로드맵** : 채용 시장 기준의 역할별 스킬 커버리지 진단과 학습 경로 제시
- **자기소개서 확장** : 포트폴리오와 자기소개서의 상호 변환·개선

## Expectation

- 동일 포트폴리오 기준, 단순 문장 교정은 평가 점수 **+1.15**에 그쳤지만 **코드 분석을 결합한 개선은 60.2 → 77.8(+17.5)** 을 달성 — '근거 있는 내용 개선'의 효과를 정량적으로 확인했습니다.
- 기여도·핵심 기능·성과가 코드에서 자동 도출되므로, 지원자는 **객관적 근거로 실력을 증명**할 수 있습니다.

## Main Target

- **연령대** : 20대 초반 ~ 30대 초반의 IT 취업 준비생·주니어 개발자
- **특징** : GitHub에 프로젝트 경험은 있으나 이를 포트폴리오 '내용'으로 구조화하는 데 어려움을 겪음
- **니즈** : 기여도·성과의 객관적 증명, 채용 시장 기준의 방향 제시, 문서 작성 부담 경감

## + More

서비스의 구체적인 내용은 아래에서 확인하실 수 있습니다.<br>

- **서비스** : [@Passfolio Site](https://www.passfolio.dev)
- **발표 자료 (Web PPT)** : [@Passfolio PPT](https://www.passfolio.dev/docs/passfolio-deck)

💡 구글에 Passfolio라고 검색해도 최상단에 나와요 😆

---

# Team Introduction

## Backend Members

<div align="left">

| **김태현** | **송성호** |
|:--------:|:---------:|
| [<img src="https://avatars.githubusercontent.com/u/92258189?v=4" height=150 width=150> <br/> @Youcu](https://github.com/Youcu) | [<img src="https://avatars.githubusercontent.com/u/173684716?v=4" height=150 width=150> <br/> @sungho1949](https://github.com/sungho1949) |
| 파트리더 · 풀스택 | 팀원 · 백엔드 |

</div>
<br>

## Other Parts

### Frontend — [@Passfolio/Frontend](https://github.com/Passfolio/Frontend)

<div align="left">

| **김태현** | **박준우** |
|:--------:|:---------:|
| [<img src="https://avatars.githubusercontent.com/u/92258189?v=4" height=100 width=100> <br/> @Youcu](https://github.com/Youcu) | [<img src="https://avatars.githubusercontent.com/u/184034424?v=4" height=100 width=100> <br/> @parkjunwoo0209](https://github.com/parkjunwoo0209) |
| 파트리더 · 풀스택 | 팀원 · 프론트엔드 |

</div>

### Portfolio-AI — [@Passfolio/Portfolio-AI](https://github.com/Passfolio/Portfolio-AI)

<div align="left">

| **이상빈** | **박준우** | **송성호** |
|:--------:|:---------:|:---------:|
| [<img src="https://avatars.githubusercontent.com/u/164621839?v=4" height=100 width=100> <br/> @dltkdqlsco](https://github.com/dltkdqlsco) | [<img src="https://avatars.githubusercontent.com/u/184034424?v=4" height=100 width=100> <br/> @parkjunwoo0209](https://github.com/parkjunwoo0209) | [<img src="https://avatars.githubusercontent.com/u/173684716?v=4" height=100 width=100> <br/> @sungho1949](https://github.com/sungho1949) |
| 팀 리더 | 파트리더 · 팀원 | 팀원 |

</div>

### Project-Analyzer-AI
<div align="left">

| **김태현** |
|:--------:|
| [<img src="https://avatars.githubusercontent.com/u/92258189?v=4" height=100 width=100> <br/> @Youcu](https://github.com/Youcu) | 
| 파트리더 · 프로젝트 분석 AI |
> 본 Part의 Repo는 Private으로 비공개 영역입니다.

</div>
<br>

---

# Development

## Key Features

Backend 파트가 담당하는 서비스 핵심 기능입니다. 모든 비동기 작업의 **디스패치/집계 허브(Orchestration Hub)** 역할을 합니다.

- **인증 · 보안** : GitHub OAuth2 + JWT 인증, GitHub 토큰은 AES(Redis) + AWS KMS **이중 봉투 암호화**로 보관
- **분석 오케스트레이션** : 분석 요청을 SQS로 디스패치(precheck · analysis)하고, Lambda 웹훅으로 결과를 수신해 배치 단위로 집계
- **NONSTOP 파이프라인** : 배치 전건 성공 시 FastAPI(Portfolio-AI)로 포트폴리오·로드맵 생성을 자동 핸드오프 (private 내부 호출)
- **실시간 알림** : SSE 기반 저장소별 분석 진행률·상태 스트리밍
- **동시성 · 안정성** : Redisson 분산 락, Bucket4j + Redis 분산 레이트 리밋(Fail-Open/Fail-Closed), Caffeine L1 캐시
- **파일 처리** : S3 멀티파트 업로드 + Presigned URL, Spring Batch 기반 미사용 파일 정리
- **알림 연동** : Solapi SMS · 메일 발송

<br>

## Environment

| Category | Stack |
| :--- | :--- |
| **Backend** | ![Java](https://img.shields.io/badge/Java%2021-007396?style=flat&logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot%203.5-6DB33F?style=flat&logo=springboot&logoColor=white) ![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-6DB33F?style=flat&logo=spring&logoColor=white) ![QueryDSL](https://img.shields.io/badge/QueryDSL-0769AD?style=flat) ![Spring Security](https://img.shields.io/badge/Spring%20Security-6DB33F?style=flat&logo=springsecurity&logoColor=white) ![OAuth2](https://img.shields.io/badge/OAuth2%20·%20GitHub-181717?style=flat&logo=github&logoColor=white) ![JWT](https://img.shields.io/badge/JWT-000000?style=flat&logo=jsonwebtokens&logoColor=white) |
| **DB · Cache** | ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=flat&logo=postgresql&logoColor=white) ![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=flat&logo=flyway&logoColor=white) ![Redis](https://img.shields.io/badge/Redis%20·%20Redisson-FF4438?style=flat&logo=redis&logoColor=white) ![Caffeine](https://img.shields.io/badge/Caffeine-6F4E37?style=flat) ![Bucket4j](https://img.shields.io/badge/Bucket4j-4B5563?style=flat) |
| **Observability** | ![Actuator](https://img.shields.io/badge/Actuator-6DB33F?style=flat&logo=spring&logoColor=white) ![Micrometer](https://img.shields.io/badge/Micrometer-117A71?style=flat) ![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=flat&logo=prometheus&logoColor=white) |
| **버전 관리** | ![GitHub](https://img.shields.io/badge/GitHub-181717?style=flat&logo=github&logoColor=white) |
| **협업 툴** | ![Notion](https://img.shields.io/badge/Notion-000000?style=flat&logo=notion&logoColor=white) ![Discord](https://img.shields.io/badge/Discord-5865F2?style=flat&logo=discord&logoColor=white) |
| **CI/CD** | ![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-2088FF?style=flat&logo=githubactions&logoColor=white) ![SSM Deploy](https://img.shields.io/badge/AWS%20SSM%20Deploy-FF9900?style=flat&logo=amazonwebservices&logoColor=white) ![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat&logo=docker&logoColor=white) |
| **Infra** | ![AWS EC2](https://img.shields.io/badge/AWS%20EC2-FF9900?style=flat&logo=amazonec2&logoColor=white) ![AWS S3](https://img.shields.io/badge/AWS%20S3-569A31?style=flat&logo=amazons3&logoColor=white) ![AWS SQS](https://img.shields.io/badge/AWS%20SQS-FF4F8B?style=flat&logo=amazonsqs&logoColor=white) ![AWS KMS](https://img.shields.io/badge/AWS%20KMS-FF9900?style=flat) ![Step Functions](https://img.shields.io/badge/Step%20Functions-E7157B?style=flat&logo=awsstepfunctions&logoColor=white) |

<br>

## Docs
### Team Documentation
- **서비스 Documentation** : [@passfolio.dev/docs](https://www.passfolio.dev/docs)
- **발표 자료 (Web PPT)** : [@Passfolio Deck](https://www.passfolio.dev/docs/passfolio-deck)

💡 Architecture는 PPT 참조

### Member's Personal Documentation
- **Harness Engineering** : [@Notion-Harness Engineering](https://hooby.notion.site/Harness-Engineering-351f6c063f3e80f79dcef01bdaced788?source=copy_link)  

<br>

## Timeline

- **기획 및 제안서** : 2026.03.03 ~ 2026.03.23
- **상세 설계** : 2026.03.24 ~ 2026.04.14
- **UI/UX 디자인** : 2026.04.04 ~ 2026.04.18
- **개발 착수** : 2026.04.19 ~ 2026.06.04
- **테스트 (Unit · E2E 병행)** : 2026.04.19 ~ 2026.06.04
- **시연** : 2026.06.04
- **최종 발표 PT** : 2026.06.07

```mermaid
gantt
    title Passfolio 개발 일정 (2026)
    dateFormat YYYY-MM-DD
    axisFormat %m/%d
    section 기획 · 설계
    기획 및 제안서            :a1, 2026-03-03, 2026-03-23
    상세 설계                :a2, 2026-03-24, 2026-04-14
    UI/UX 디자인             :a3, 2026-04-04, 2026-04-18
    section 개발 · 테스트
    개발                     :b1, 2026-04-19, 2026-06-04
    테스트 (Unit · E2E 병행)  :b2, 2026-04-19, 2026-06-04
    section 마무리
    시연                     :milestone, m1, 2026-06-04, 0d
    최종 발표 PT             :milestone, m2, 2026-06-07, 0d
```

<br>

## Management

- **관리 방식** : 파트별 분업 + 주간 단위 반복(스프린트 유사)으로 진행, 파트 간 인터페이스(API·웹훅)는 사전 합의 후 병렬 개발
- **이슈 관리** : GitHub Issues로 기능·버그 단위 추적
- **문서 관리** : Notion에 기획안·회의록·기술 문서 기록, 서비스 문서는 웹 Documentation 페이지로 공개
- **소스 코드 관리** : GitHub Pull Request 기반 코드 리뷰 및 히스토리 관리, Discord로 실시간 커뮤니케이션

---

💬 **About Passfolio Team**

> ◦ 명지대학교(자연) 캡스톤 디자인 프로젝트를 진행한 **Team 20세기's** 입니다.<br>
> ◦ 재학생 4인 구성으로, 4개 파트(FE · BE · Portfolio-AI · Project-Analyzer-AI)를 나누어 협업했습니다.<br>
> ◦ 현재 서버 운영은 일시 중단 상태이며, **2026년 12월 정식 출시**를 목표로 재정비 중입니다. 문의: hooby@passfolio.dev

# LandClaim (땅 구매 · 보호 · 지도 플러그인)

Paper 26.2 / Java 25 용 플러그인입니다. 모드 없이 서버에 .jar 하나만 넣으면 됩니다.

## 1. .jar 파일 만들기 (GitHub가 자동으로 빌드)
1. https://github.com 에서 무료 계정을 만들고 **New repository**로 새 저장소를 만듭니다 (이름 예: LandClaim).
2. 이 폴더의 파일을 **폴더 구조 그대로** 저장소에 올립니다.
   - `Add file → Upload files`에 `pom.xml`, `README.md`, `src` 폴더를 끌어다 놓고 Commit.
   - `.github/workflows/build.yml`은 숨김 폴더라 끌어다 놓기가 안 될 수 있습니다.
     그럴 땐 `Add file → Create new file`을 누르고 파일 이름 칸에 `.github/workflows/build.yml`을 입력한 뒤
     이 폴더의 build.yml 내용을 붙여넣고 Commit 하세요.
3. 저장소의 **Actions** 탭 → 가장 위 실행(초록 체크)을 열고 맨 아래 **Artifacts**의 `LandClaim`을 내려받습니다.
   (압축을 풀면 `LandClaim.jar`)
   - 빨간 X가 뜨면 실행 로그의 오류 문구를 그대로 복사해서 보내주세요.

## 2. 서버에 넣기 (아터노스)
1. 서버 소프트웨어를 **Paper**, 버전을 **26.2**로 설정합니다. (Java는 아터노스가 알아서 맞춥니다.)
2. 파일 메뉴에서 `plugins` 폴더에 `LandClaim.jar`를 업로드하고 서버를 시작합니다.
3. 처음 켜면 `plugins/LandClaim/config.yml`이 생깁니다. 월드 이름이 `world`가 아니면 `world:` 값을 바꾸세요.

## 3. 리소스팩 (땅 문서 두루마리 모양)
1. `LandClaim-ResourcePack.zip`을 GitHub 저장소의 **Releases**에 올립니다. (Create a new release → 파일 첨부 → Publish)
2. 올라간 zip 파일의 다운로드 링크를 복사해서 `config.yml`의 `resource-pack.url`에 넣습니다.
3. `resource-pack.sha1`에는 `40da5e18c0f2402310817159f708dc94540e2806`을 넣습니다. (zip을 다시 만들지 않았을 때)
4. 서버를 재시작하면 접속 시 리소스팩 수락 창이 뜹니다. 거절해도 기능은 똑같이 동작하지만 문서가 보라색 깨진 모양으로 보입니다.
   리소스팩을 쓰지 않을 거면 `use-custom-model: false`로 바꾸세요(기본 종이 모양).

## 4. 사용법
- `/land help` 로 전체 명령어 목록을 볼 수 있습니다 (클릭 가능, 관리자용은 관리자에게만 보임).
- `/land buy` 로 개인 땅 문서를 구매하고, 아직 팔리지 않은 땅에서 **Shift + 우클릭**으로 사용합니다.
- `/land map` 5×5 지도 (초록 구매 가능 / 빨강 팔린 땅 / 파랑 공용 땅, 내 위치는 ★ 표시와 반짝임)
- 관리자(OP)는 `/land admin ...` 사용 가능. 권한 `land.admin`, 건축 우회 권한 `land.bypass` (기본 OP).

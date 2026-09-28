# APK 만들기 — Android Studio 필요 없음

## 가장 쉬운 방법: GitHub Actions

1. GitHub에서 새 저장소(repository)를 하나 만듭니다.
2. 이 ZIP의 압축을 풀고 **모든 파일과 폴더**를 저장소에 업로드합니다.
3. `Actions` 탭을 엽니다.
4. `Build SubtitlePad APK` workflow를 선택합니다.
5. `Run workflow`를 누릅니다.
6. 빌드가 끝나면 해당 실행 화면 아래의 `Artifacts`에서 `SubtitlePad-debug-apk`를 다운로드합니다.
7. 압축을 풀면 `app-debug.apk`가 있습니다.
8. APK를 안드로이드 태블릿으로 보내서 설치합니다.

### 주의
- 이 workflow는 push할 때도 자동 실행되지만, 처음에는 `Actions`에서 수동으로 `Run workflow`를 누르는 것이 가장 쉽습니다.
- Android Studio는 필요 없습니다.
- 이 저장소에는 비밀키나 개인정보를 넣을 필요가 없습니다.

## 로컬에서 빌드하는 경우

GitHub Actions는 Gradle 8.13을 자동으로 준비하므로 별도의 Gradle 설치도 필요 없습니다. Android SDK는 GitHub의 Ubuntu 빌드 환경에 준비되어 있습니다.

# SubtitlePad

TV/Apple TV에서 영화를 재생하면서 Android 태블릿을 외부 자막 표시기로 사용하는 1차 버전입니다.

## 포함 기능
- SRT / SMI 파일 선택
- UTF-8 / UTF-16 / EUC-KR(CP949) 한글 자막 읽기
- 자막 전용 전체화면
- 재생 / 일시정지
- 10초 앞/뒤 이동
- 0.1초 단위 싱크 보정
- 자막 크기 조절
- 화면 항상 켜짐
- 가로 화면 고정
- 현재 자막/전체 자막 진행 표시
- SMI의 `<SYNC Start=...>` 및 SRT의 `00:00:00,000` 형식 지원

## 빌드
Android Studio에서 이 폴더를 열고 Gradle Sync 후:
Build > Build APK(s)

또는 Android SDK가 설치된 환경에서:
`gradlew assembleDebug`

필요 환경:
- Android Studio 최신 안정 버전
- Android SDK Platform 35
- JDK 17 이상

## 사용 방법
1. 앱 실행
2. SRT 또는 SMI 선택
3. 자막 화면에서 재생
4. Apple TV에서 영화를 시작한 뒤 태블릿 자막을 맞춤
5. ±0.1초 / ±10초 버튼으로 싱크를 조절

주의:
Apple TV의 실제 재생 위치를 자동으로 읽는 기능은 1차 버전에 포함하지 않았습니다.

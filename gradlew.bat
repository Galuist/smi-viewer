@echo off
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle is not installed locally. Use GitHub Actions (Actions -^> Build SubtitlePad APK -^> Run workflow).
exit /b 1

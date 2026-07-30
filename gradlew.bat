@echo off
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle is not installed. Use the included GitHub Actions workflow.
  exit /b 1
)
gradle %*

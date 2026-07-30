# GitHub 저장소 잔여 파일 영구 삭제

GitHub 웹에서 새 파일을 덮어 올려도 기존 파일은 자동으로 삭제되지 않습니다.
따라서 아래 파일이 이미 저장소에 커밋돼 있으면 깨끗한 ZIP을 올려도 남을 수 있습니다.

- `VolumeDucking.java`
- `YouTubePlayerActivity.java`
- `YoutubeUrlParser.java`
- `build/`

## 가장 쉬운 처리

1. 이 전체 수정본을 GitHub에 올립니다.
2. **Actions** 탭에서 `Permanently remove stale repository files`를 선택합니다.
3. `Run workflow`를 한 번 실행합니다.
4. 완료 후 `Build MARU MUSIC LIVE V3.1.2 APK`를 실행합니다.

첫 번째 워크플로는 남은 파일을 실제 Git 커밋에서 삭제합니다. 두 번째 빌드
워크플로도 시작 즉시 같은 파일을 한 번 더 제거하고 검사하므로 재발을 막습니다.

보호된 main 브랜치에서 Actions의 직접 push가 금지되어 있으면 Repository
Settings → Actions → General → Workflow permissions에서 `Read and write permissions`를
허용하거나, 아래 명령을 로컬 Git에서 실행해야 합니다.

```bash
git rm -f VolumeDucking.java YouTubePlayerActivity.java YoutubeUrlParser.java || true
git rm -rf build || true
git add .gitignore .github/workflows scripts
git commit -m "chore: remove stale root Java and build artifacts"
git push
```

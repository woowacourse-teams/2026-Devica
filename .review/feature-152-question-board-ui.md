# 리뷰 기록

base: origin/feature/149-annoymous-question-board
diff-hash: 806ddf9f77f96bd832d28507f9aeaea903176184

지적 없음

## 검증

- `async-request.md`, `state-model.md` 규칙별 리뷰 완료.
- 변경한 프론트엔드 파일 13개의 Biome 검사 통과.
- `npm run build`의 TypeScript 검사와 Vite 빌드 통과.
- 실제 백엔드와 별도 로컬 DB로 선택 → 작성 → 상세 → 목록, 상세 새로고침, 공통 FAQ, 잘못된 선택값·게시글 주소 확인.
- 테스트 API로 페이지 이동·브라우저 뒤로 가기, 목적 변경과 지연 응답, 작성 중 잠금·실패 후 입력 보존, 화면 이탈 후 응답 처리, 목록 실패 후 재시도 확인.
- 모바일 목록·글쓰기의 가로 넘침 없음과 공백·최대 길이 입력 제한 확인.

백엔드 #149 브랜치에서 분기했으므로 해당 부모 브랜치를 리뷰 기준으로 사용했다.

import { Link } from 'wouter';
import { fetchBoardPosts } from './board';
import { PATHS } from './routes';
import { useBoardRequest } from './useBoardRequest';

const RECENT_QUESTION_COUNT = 4;

export function HomeRecentQuestions() {
  const { state, retry } = useBoardRequest('home:LAPTOP:BACKEND_DEVELOPMENT', (signal) =>
    fetchBoardPosts('LAPTOP', 'BACKEND_DEVELOPMENT', 0, signal, RECENT_QUESTION_COUNT),
  );

  return (
    <section className="home-questions" aria-labelledby="home-questions-title">
      <div className="home-questions__header">
        <div>
          <h2 id="home-questions-title">최근 질문</h2>
          <p className="home-questions__scope">노트북 / 백엔드 개발</p>
        </div>
        <Link className="button button--neutral home-questions__board" href={PATHS.board}>
          질문 게시판 가기
        </Link>
      </div>
      {state.status === 'loading' && (
        <p className="home-questions__status" role="status">
          최근 질문을 불러오는 중입니다…
        </p>
      )}
      {state.status === 'failed' && (
        <div className="home-questions__status">
          <p role="alert">최근 질문을 불러오지 못했습니다.</p>
          <button className="button" type="button" onClick={retry}>
            다시 시도
          </button>
        </div>
      )}
      {state.status === 'success' &&
        (state.data.content.length === 0 ? (
          <div className="home-questions__status" role="status">
            <p>아직 등록된 질문이 없습니다.</p>
            <p>질문 게시판에서 첫 번째 질문을 남겨 보세요.</p>
          </div>
        ) : (
          <ul className="home-questions__list">
            {state.data.content.slice(0, RECENT_QUESTION_COUNT).map((post) => (
              <li key={post.id}>
                <Link className="home-questions__link" href={`/board/posts/${post.id}`}>
                  <span className="home-questions__title">{post.title}</span>
                  <span className="home-questions__meta">익명 · 댓글 {post.commentCount}</span>
                  <span className="home-questions__arrow" aria-hidden="true">
                    →
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        ))}
    </section>
  );
}

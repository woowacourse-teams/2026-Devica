import './SiteLayout.css';

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="site-footer__inner">
        <p className="site-footer__brand">
          <b>DEVICA</b> &nbsp; 내 사용 목적에 맞게, 구매 고민은 더 가볍게.
        </p>
        <div className="site-footer__contact">
          <p>
            문의 <a href="mailto:devica.team@gmail.com">devica.team@gmail.com</a>
          </p>
          <p>© 2026 Devica</p>
        </div>
      </div>
    </footer>
  );
}

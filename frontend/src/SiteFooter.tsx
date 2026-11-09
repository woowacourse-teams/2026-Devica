import './SiteLayout.css';

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="site-footer__inner">
        <span className="site-footer__brand">DEVICA</span>
        <p className="site-footer__contact">
          문의 <a href="mailto:devica.team@gmail.com">devica.team@gmail.com</a> · © 2026 Devica
        </p>
      </div>
    </footer>
  );
}

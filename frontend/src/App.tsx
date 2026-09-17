import { IntroView } from './IntroView';
import { SiteFooter } from './SiteFooter';
import { SiteHeader } from './SiteHeader';

export function App() {
  return (
    <>
      <SiteHeader/>
      <main className="probe" id="main-content">
        <IntroView/>
      </main>
      <SiteFooter/>
    </>
  );
}

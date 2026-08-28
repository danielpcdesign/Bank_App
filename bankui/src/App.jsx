import { Link, Route, Routes } from 'react-router'

import Navbar from './components/Navbar.jsx'
import AboutPage from './pages/AboutPage.jsx'
import ContactPage from './pages/ContactPage.jsx'
import CustomersPage from './pages/CustomersPage.jsx'
import EditCustomerPage from './pages/EditCustomerPage.jsx'
import HomePage from './pages/HomePage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'

/*
 * The root component. It owns the layout shell and the route table, and nothing else - no
 * fetching, no state. Same instinct as the controller in the API: this layer arranges, it
 * does not decide.
 *
 * THE SHELL. header / main / footer sit OUTSIDE <Routes>, so they render once and stay put
 * while only <main> swaps. That is the structural payoff of client-side routing: navigating
 * does not rebuild the page, it replaces one subtree. The nav's DOM nodes are never
 * destroyed, which is why moving between pages does not flicker.
 *
 * <Routes> renders exactly one of its children - whichever <Route> best matches the current
 * URL. It is a switch, not a list. The route table is the front end's equivalent of
 * @RequestMapping: a declaration of which paths exist and what answers them.
 */
function App() {
  return (
    <div className="app-shell">

      <header className="app-header">
        {/* Link, not <a href>. An <a> makes the browser fetch the page again from scratch,
            discarding the running app and every piece of state in it. Link changes the URL
            through the History API and lets React re-render - the whole reason a
            single-page app is single-page. */}
        <Link to="/" className="brand">Bank App</Link>
        <Navbar />
      </header>

      <main className="app-main">
        <Routes>
          <Route path="/" element={<HomePage />} />

          {/* MOVED. The customer list used to be what "/" rendered. Now "/" is a landing
              page and the list has its own address.

              A route should name one thing, and "/" was naming two: "the app" and "the
              customer list". So there was no URL that meant "show me the customers", and
              nowhere to put a home page without displacing them. Splitting it also makes
              the list linkable, which starts to matter the moment there is more than one
              screen worth linking to. */}
          <Route path="/customers" element={<CustomersPage />} />

          {/* :id is a URL parameter, not a literal. It matches any single segment and hands
              the value to the component via useParams. This is what makes a route a route
              rather than a page name: the URL carries state. */}
          <Route path="/customers/:id/edit" element={<EditCustomerPage />} />

          <Route path="/about" element={<AboutPage />} />
          <Route path="/contact" element={<ContactPage />} />
          <Route path="/login" element={<LoginPage />} />

          {/* The catch-all, and the client side's 404. It is load-bearing rather than
              cosmetic: nginx answers EVERY unmatched path with index.html so that deep links
              work, which means the server can no longer tell a typo from a real route. It
              has handed that judgement to this line. */}
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>

      <footer className="app-footer">
        <small>JUMP by Cognixia &middot; Banking App</small>
      </footer>

    </div>
  )
}

export default App

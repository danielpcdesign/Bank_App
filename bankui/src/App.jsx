import { Link, Route, Routes } from 'react-router'

import Navbar from './components/Navbar.jsx'
import RequireSignIn from './components/RequireSignIn.jsx'
import AboutPage from './pages/AboutPage.jsx'
import ContactPage from './pages/ContactPage.jsx'
import CustomersPage from './pages/CustomersPage.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import EditCustomerPage from './pages/EditCustomerPage.jsx'
import HomePage from './pages/HomePage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import MyDashboardPage from './pages/MyDashboardPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import RegisterPage from './pages/RegisterPage.jsx'

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

          {/* ============================================================================
              PUBLIC. Two routes, and they are the complete list of what an unauthenticated
              visitor may reach. Everything else lives under the guard below.

              They sit OUTSIDE RequireSignIn rather than being special-cased inside it,
              because "which pages are public" is a fact about the route table and belongs
              where the routes are - readable in one glance, and impossible to extend by
              accident from inside a component that cannot see this list.
              ============================================================================ */}

          <Route path="/login" element={<LoginPage />} />

          {/* the only other thing you may do without an account is get one. Registration
              takes a username, a password and a full name; the server assigns the id and
              forces the role. Deliberately NOT the same route or the same form as the
              admin-side create, which chooses an id on purpose. */}
          <Route path="/register" element={<RegisterPage />} />

          {/* ============================================================================
              EVERYTHING ELSE, behind the gate.

              A LAYOUT ROUTE: RequireSignIn renders <Outlet /> when somebody is signed in and
              <Navigate to="/login" /> when nobody is, so the children below are never
              mounted for a visitor who should not see them - not mounted and then hidden,
              never mounted, which is the difference between a gate and a leaked frame.

              Stated once, here, rather than as a wrapper repeated around each element. A
              wrapper per route is how one route eventually gets added without one.

              This is a PRODUCT decision and not a security control: the API answers every
              one of these endpoints for any caller with no credential, so this stops a
              person browsing and stops nobody else. RequireSignIn sets out what it does and
              does not buy, and NoAuthNotice says the same thing on the screen.
              ============================================================================ */}
          <Route element={<RequireSignIn />}>

            <Route path="/" element={<HomePage />} />

            {/* MOVED. The customer list used to be what "/" rendered. Now "/" is a landing
                page and the list has its own address.

                A route should name one thing, and "/" was naming two: "the app" and "the
                customer list". So there was no URL that meant "show me the customers", and
                nowhere to put a home page without displacing them. Splitting it also makes
                the list linkable, which starts to matter the moment there is more than one
                screen worth linking to. */}
            <Route path="/customers" element={<CustomersPage />} />

            {/* :id is a URL parameter, not a literal. It matches any single segment and
                hands the value to the component via useParams. This is what makes a route a
                route rather than a page name: the URL carries state. */}
            <Route path="/customers/:id/edit" element={<EditCustomerPage />} />

            {/* THE IDENTITY IS IN THE PATH. /dashboard/4 shows customer 4 because the
                address says 4, and a signed-in visitor can still edit the number and see
                somebody else's - the gate above asks whether you are signed in, never who
                you are. That is the honest shape of an app whose API serves every caller
                without asking; see DashboardPage for the alternatives and why each was
                worse.

                /dashboard with no id means "mine", and resolves to whoever signed in. It is
                a redirect rather than a page so that a dashboard always has an address
                naming whose it is - which is what makes it linkable, and what stops "my
                dashboard" and "customer 4's dashboard" from being two different screens. */}
            <Route path="/dashboard" element={<MyDashboardPage />} />
            <Route path="/dashboard/:id" element={<DashboardPage />} />

            <Route path="/about" element={<AboutPage />} />
            <Route path="/contact" element={<ContactPage />} />

            {/* The catch-all, and the client side's 404. It is load-bearing rather than
                cosmetic: nginx answers EVERY unmatched path with index.html so that deep
                links work, which means the server can no longer tell a typo from a real
                route. It has handed that judgement to this line.

                INSIDE the guard, so a signed-out visitor typing a nonsense address is sent
                to sign in rather than shown a 404 page - "you may go nowhere else" includes
                nowhere. A signed-in one gets the 404, which is the useful answer. */}
            <Route path="*" element={<NotFoundPage />} />

          </Route>

        </Routes>
      </main>

      <footer className="app-footer">
        <small>JUMP by Cognixia &middot; Banking App</small>
      </footer>

    </div>
  )
}

export default App

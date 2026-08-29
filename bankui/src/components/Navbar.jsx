import { NavLink, useLocation, useNavigate } from 'react-router'

import { getSignedInId, signOut } from '../services/viewer.js'

/*
 * The site navigation.
 *
 * NavLink rather than Link. They do the same thing - client-side navigation through the
 * History API, no page reload - but NavLink additionally knows whether its destination IS
 * the current URL, and hands that answer to className as a function.
 *
 * That is worth noticing: no component here tracks "which tab am I on". The router already
 * knows, because the URL is the state. Storing it in a useState alongside would create a
 * second source of truth that the back button could immediately falsify.
 *
 * `end` on the Home link is not decoration. NavLink counts a link active when the current
 * path STARTS WITH its `to`, so "/" would be active on every page in the app. `end` demands
 * an exact match. The other links do not need it because no route nests under them yet -
 * but /customers deliberately omits it, so that /customers/2/edit still highlights
 * Customers, and /dashboard omits it so /dashboard/4 highlights Dashboard. Being on a
 * customer's edit page is still being in the customers section.
 *
 * WHY useLocation IS CALLED AND ITS RESULT IGNORED. The sign-in state is not React state - it
 * lives in sessionStorage, which React cannot observe, so nothing here would re-render when
 * it changes and the bar would keep offering "Sign out" after signing out. Every change to
 * that value in this app is accompanied by a navigation (sign in navigates to a dashboard,
 * sign out navigates to the sign-in form), so subscribing to the location is enough to make
 * the bar correct, and it makes the dependency explicit rather than relying on a parent
 * happening to re-render. The alternative - lifting the value into context - would be more
 * machinery for a thing that is deleted in Phase 10.
 */
export default function Navbar() {
  const linkClass = ({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')

  const navigate = useNavigate()

  // read on every render, and the render is driven by the line below. See the comment above.
  useLocation()
  const signedIn = getSignedInId() !== null

  const handleSignOut = () => {
    signOut()
    // to the sign-in form rather than to the home page: signing out is almost always the
    // first half of signing in as somebody else, and this is a demonstration app where that
    // is the common case rather than an edge one.
    navigate('/login', { replace: true })
  }

  /*
   * SIGNED OUT, THE BAR IS TWO LINKS. Not the full navigation with dead entries that bounce
   * off the guard - a link that cannot be followed is worse than no link: it advertises a
   * destination, and the redirect that follows reads as a bug rather than a rule.
   *
   * This is presentation catching up with the route table, and nothing more. It gates
   * nothing. RequireSignIn does the redirecting, the API does no checking at all, and hiding
   * a <NavLink> hides a URL that is four characters long from somebody who could type it
   * anyway. If this menu and that guard ever disagree, the guard is the one that decides.
   */
  if (!signedIn) {
    return (
      <nav className="navbar">
        <NavLink to="/login" className={linkClass}>Sign in</NavLink>
        <NavLink to="/register" className={linkClass}>Create an account</NavLink>
      </nav>
    )
  }

  return (
    <nav className="navbar">
      <NavLink to="/" className={linkClass} end>Home</NavLink>
      <NavLink to="/customers" className={linkClass}>Customers</NavLink>
      <NavLink to="/dashboard" className={linkClass}>Dashboard</NavLink>
      <NavLink to="/about" className={linkClass}>About</NavLink>
      <NavLink to="/contact" className={linkClass}>Contact</NavLink>

      {/* Pushed to the far side by the stylesheet rather than by markup order, because it is
          a visual grouping, not a structural one.

          A BUTTON for signing out, where signing in is a LINK, and the difference is not
          cosmetic.
          A link is a destination: it can be middle-clicked into a new tab, copied, and read
          by a screen reader as somewhere to go. Signing out is none of those - it is an
          action with an effect, and a thing that CHANGES something should not be a URL that
          could be prefetched or opened in a background tab. */}
      <button type="button" id="nav-login" onClick={handleSignOut} className="link-button">
        Sign out
      </button>
    </nav>
  )
}

import { NavLink } from 'react-router'

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
 * Customers. Being on a customer's edit page is still being in the customers section.
 */
export default function Navbar() {
  const linkClass = ({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')

  return (
    <nav className="navbar">
      <NavLink to="/" className={linkClass} end>Home</NavLink>
      <NavLink to="/customers" className={linkClass}>Customers</NavLink>
      <NavLink to="/about" className={linkClass}>About</NavLink>
      <NavLink to="/contact" className={linkClass}>Contact</NavLink>

      {/* Pushed to the far side by the stylesheet rather than by markup order, because it is
          a visual grouping, not a structural one. */}
      <NavLink to="/login" className={linkClass} id="nav-login">Log in</NavLink>
    </nav>
  )
}

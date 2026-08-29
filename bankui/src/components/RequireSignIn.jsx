import { Navigate, Outlet } from 'react-router'

import { getSignedInId } from '../services/viewer.js'

/*
 * The gate. Everything except signing in and registering renders inside it.
 *
 * ==========================================================================================
 * WHAT THIS IS, EXACTLY
 * ==========================================================================================
 *
 * A PRODUCT DECISION, NOT A SECURITY CONTROL. The distinction is the entire reason this
 * comment is longer than the code, and it has to survive the next person who reads a file
 * named RequireSignIn and assumes the name is a promise.
 *
 * The API serves every endpoint - every GET, every DELETE - to any caller presenting no
 * credential at all. That has not changed and this component does not change it. What lives
 * behind this gate is one unauthenticated request away for anybody who wants it, and curl does
 * not render components. A gate in the client stops a person BROWSING. It stops nobody.
 *
 * WHAT IT GENUINELY BUYS, and it is worth naming so this does not read as pure theatre:
 * it removes ACCIDENTAL exposure. Before this, opening the site showed every customer in the
 * system to anyone who arrived - a colleague glancing at a screen, a link pasted into a chat,
 * a search engine that wandered in. None of those people were attacking anything; they were
 * shown the data because the app volunteered it. Not volunteering it is worth doing on its own
 * terms, and it is a completely different claim from "this data is protected". The first is
 * true. The second is not, and NoAuthNotice says so on the screens behind this gate.
 *
 * ==========================================================================================
 * NO LEAKED FRAME
 * ==========================================================================================
 *
 * A guard that renders its children and then redirects has already shown the data it was meant
 * to gate - for a frame, in a screenshot, in the DOM, and in whatever requests those children
 * fired on mount. Two properties prevent that here, and both are structural rather than
 * careful:
 *
 *   THE DECISION IS SYNCHRONOUS. getSignedInId is a sessionStorage read, which returns during
 *   render. There is no effect, no await, no loading state. By the time this function returns
 *   the answer is already known.
 *
 *   THE CHILDREN ARE NOT RENDERED IN THE FAILING CASE. <Navigate> is returned INSTEAD OF
 *   <Outlet>, not alongside it. A route element that is never returned is never mounted, so
 *   its effects never run and its fetches are never issued. Nothing is unmounted after the
 *   fact, because nothing was mounted.
 *
 * That is also why the check must never depend on anything fetched after mounting. "Is this id
 * a real customer" is a question only the server can answer, and asking it would mean rendering
 * something while waiting - which is the leak. So this gate checks PRESENCE, synchronously, and
 * nothing else. Validity is a separate problem discovered by the first page that resolves the
 * customer, and DashboardPage handles it by signing out and coming back here.
 *
 * ==========================================================================================
 *
 * A LAYOUT ROUTE rather than a wrapper repeated around eight elements. <Outlet> renders
 * whichever child route matched, so App's route table keeps its shape and the guard is stated
 * once. Repeating a wrapper is how one route eventually gets added without it.
 *
 * No redirect-back-to-where-you-were, deliberately. The obvious feature is to remember the
 * blocked destination and return to it after signing in, and it is wrong here: the destinations
 * are dashboards belonging to particular people, and following a deep link to somebody else's
 * dashboard after signing in as yourself is not what the person wanted. Sign-in sends you to
 * your own dashboard, because after signing in the app knows who you are.
 */
export default function RequireSignIn() {
  const signedInId = getSignedInId()

  // replace: true - the blocked address should not sit in the history behind the sign-in page,
  // where the back button would bounce off this guard again.
  return signedInId === null
    ? <Navigate to="/login" replace />
    : <Outlet />
}

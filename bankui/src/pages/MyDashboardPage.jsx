import { Navigate } from 'react-router'

import { getSignedInId } from '../services/viewer.js'

/*
 * What /dashboard means with no id after it: "mine". It resolves to the signed-in customer's
 * dashboard, or to the sign-in form when nobody is signed in.
 *
 * IT REPLACED ViewAsPage, which listed every customer and let you pick one to inspect the app
 * as. That page was the honest stand-in while there was no way to check a credential; now
 * there is one, and a chooser that lets anybody step into anybody's dashboard without a
 * password would be strictly worse than the sign-in beside it - not because it is insecure
 * (nothing here is secure) but because it makes the password pointless while the password is
 * the one part of this that genuinely works.
 *
 * ==========================================================================================
 * CORRECTED - THIS PARAGRAPH USED TO SAY THE OPPOSITE OF WHAT THE APP NOW DOES
 * ==========================================================================================
 *
 * It read: "WHAT IT DOES NOT DO IS BLOCK ANYTHING. /dashboard/4 typed into the address bar
 * still renders customer 4's dashboard whether or not anyone signed in, and DashboardPage does
 * not check. That is deliberate." Every clause of that is now false, and it is corrected here
 * rather than deleted, because a comment that would talk the next reader out of a guard that
 * EXISTS is worse than one that is merely out of date - the reader does not go looking for
 * something they have just been told is absent on purpose.
 *
 * What is actually true now:
 *
 *   SIGNED OUT, /dashboard/4 RENDERS NOTHING. RequireSignIn wraps the whole route table below
 *   /login and /register, and it returns <Navigate> INSTEAD OF <Outlet>, so the page is never
 *   mounted rather than mounted and hidden.
 *
 *   SIGNED IN AS SOMEBODY ELSE, DashboardPage DOES CHECK. It fetches the signed-in customer,
 *   decides from THAT record - never from the id in the URL - and returns a non-admin who asks
 *   for another person's dashboard to their own, before any of the subject's data is requested.
 *
 *   THE BANNER THAT "SAYS SO OUT LOUD" IS GONE. It was removed at the user's instruction; the
 *   maintainer-facing half of that argument now lives in services/api.js and is not to be
 *   reintroduced on screen.
 *
 * WHAT SURVIVES THE CORRECTION, because it was the sound part and none of the above touches it:
 * a client-side redirect is still not access control. The data behind these routes is one
 * unauthenticated GET away for anybody who wants it, and every gate named above is a PRODUCT
 * boundary - it decides what this interface offers, and curl never runs it. The old paragraph's
 * mistake was not its principle, it was concluding from "a redirect is not security" that the
 * app should not decide which screen a person gets. Those are different claims, and DashboardPage
 * records what the second one cost when it was believed. When the API starts refusing anonymous
 * callers, the refusal still comes from there, and this file is still not where it lands.
 *
 * THE /login BRANCH IS NOW UNREACHABLE, and it stays. This route sits inside RequireSignIn,
 * which has already established that an id is present - so by the time this renders, it cannot
 * be null. It is kept because it is one ternary arm and because it is the correct answer to
 * the question this component asks, independently of who else happens to have asked it first.
 * Deleting it would make this file quietly depend on a guard that is configured in a different
 * file, and a component whose correctness rests on a route table it cannot see is the kind
 * that breaks when somebody moves a route.
 *
 * A component rather than logic in App.jsx, because App owns the layout shell and the route
 * table and nothing else - no state, no reads, no decisions. This is a decision, so it lives
 * in a file a route points at.
 *
 * No effect, no state. It reads a value that is already there and returns a redirect, which
 * makes it a plain function of storage - and means there is no flash of an empty dashboard
 * before the redirect happens.
 */
export default function MyDashboardPage() {
  const id = getSignedInId()

  // replace: true on both. This page is a junction, not a destination - leaving it in the
  // history would make the back button bounce the user through a redirect they never saw.
  return id
    ? <Navigate to={`/dashboard/${id}`} replace />
    : <Navigate to="/login" replace />
}

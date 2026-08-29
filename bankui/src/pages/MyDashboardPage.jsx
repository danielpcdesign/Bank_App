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
 * WHAT IT DOES NOT DO IS BLOCK ANYTHING. /dashboard/4 typed into the address bar still renders
 * customer 4's dashboard whether or not anyone signed in, and DashboardPage does not check.
 * That is deliberate. A client-side redirect away from a URL is not access control - the data
 * behind it is one unauthenticated GET away for anyone who wants it - and writing one would
 * put a thing that looks like a guard in front of a door that has no lock. The banner on every
 * dashboard says so out loud instead. When the API starts refusing anonymous callers, the
 * refusal will come from there, and this file will not be the place it is implemented.
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

import { useEffect, useState } from 'react'
import { Link, Navigate, Outlet } from 'react-router'

import { getCustomer, isAdmin } from '../services/api.js'
import { getSignedInId, signOut } from '../services/viewer.js'

/*
 * The second gate. RequireSignIn asks whether SOMEBODY is signed in; this asks WHO.
 *
 * That distinction is the bug this file exists to fix. Authentication and authorisation are
 * different questions, and for a while this app only asked the first - so every signed-in
 * customer got the whole application, including the screen that lists, creates and deletes
 * other customers. A gate that checks presence and is treated as protection is the exact
 * failure these comments have spent their length warning about, and it happened here anyway.
 *
 * ==========================================================================================
 * IT IS STILL NOT SECURITY. THIS ONE MATTERS MOST OF ALL.
 * ==========================================================================================
 *
 * Every previous notice in this app said "the UI gates, the API does not". This change is the
 * one where that stops being obvious and starts being easy to forget, because for the first
 * time a customer genuinely CANNOT reach the admin surface through the interface. It looks
 * like access control. It is not.
 *
 * GET /api/v1/customers, POST /api/v1/customers and DELETE /api/v1/customers/{id} are served
 * to any caller presenting no credential at all. Unchanged. A signed-in customer who opens
 * DevTools, or anybody at all with curl and the address, can list every customer, create
 * customers, and delete them - administrators included - without this component ever running.
 * curl does not render components.
 *
 * So what this buys is what the sign-in gate bought, one level up: it stops a person
 * STUMBLING INTO a surface that was never meant for them. A customer following links can no
 * longer end up on an admin screen. That is a product boundary, it is worth having, and it is
 * not a security boundary.
 *
 * ONE REAL PROTECTION DOES EXIST, and it is worth naming because it shows the shape a real one
 * takes: the server refuses to delete the last remaining administrator. That is an invariant
 * about the STATE OF THE SYSTEM rather than a claim about who is asking, so it needs no
 * identity to enforce and it holds for curl exactly as it holds here. DashboardPage handles
 * its refusal. Note the difference in kind - it stops the system ending up with no
 * administrator at all; it does not protect anything from any particular caller.
 *
 * ==========================================================================================
 * THE HARD PART: NOT LEAKING BEFORE THE ANSWER ARRIVES
 * ==========================================================================================
 *
 * RequireSignIn had it easy. "Is an id present" is a sessionStorage read, which returns during
 * render, so the decision was already made by the time that component returned anything.
 *
 * This gate cannot be synchronous. The role is deliberately NOT in sessionStorage - caching it
 * would make the client the holder of its own claim about what it may see, which
 * services/viewer.js refuses at length and still refuses. So the role lives on the server,
 * asking for it costs a round trip, and there is necessarily at least one render before the
 * answer exists.
 *
 * The naive shape leaks, and leaks completely: render the children, fetch, then redirect if
 * the answer comes back wrong. By then the children have mounted, fired their own requests,
 * put every customer into the DOM and painted them. Hiding them afterwards hides nothing - the
 * data was displayed and the requests were made. A "flash of admin content" is not a cosmetic
 * bug, it is the entire leak.
 *
 * THREE OUTCOMES, AND THE CHILDREN APPEAR IN EXACTLY ONE OF THEM. Structural, not careful:
 *
 *   checking  - the answer is not known. Renders a neutral line and NOTHING ELSE. <Outlet /> is
 *               not in the returned tree, so no child mounts, no child effect runs, and no
 *               child request is issued.
 *   allowed   - confirmed admin. NOW <Outlet /> is returned, and the children mount for the
 *               first time.
 *   denied    - confirmed not an admin. A redirect. The children were never rendered at all.
 *
 * The initial state is `checking`, not `allowed`, and that single choice is what makes this
 * safe: the gate is CLOSED until something opens it, rather than open until something closes
 * it. Defaulting the other way would leak, and would pass every manual test run on a fast
 * connection by somebody who is already an administrator.
 *
 * WHY A COMPONENT AND NOT A CHECK INSIDE THE PAGE. For a route that is admin-only end to end -
 * editing a customer - the gate belongs outside the page, so the page cannot be reached at
 * all. DashboardPage deliberately does NOT use this: /dashboard/:id serves administrators and
 * customers both, so there is no admin route there to wrap. It answers the same question
 * inside itself with the same discipline - it renders nothing but "Loading..." until the
 * signed-in customer's role has arrived, and only then chooses which dashboard exists.
 */
export default function RequireAdmin() {
  const signedInId = getSignedInId()

  /*
   * 'checking' | 'allowed' | 'denied' | 'gone' | 'failed'
   *
   * One state variable rather than a pair of booleans. Two booleans admit four combinations
   * where only three are meaningful, which leaves a fourth that means nothing and will
   * eventually be reachable.
   */
  const [state, setState] = useState('checking')

  useEffect(() => {
    // guards against setting state after this component has gone - a user navigating away
    // mid-request. Not a leak, just a warning and a wasted render, but the cleanup is two
    // lines and the alternative is console noise that trains people to ignore console noise.
    let cancelled = false

    const check = async () => {
      try {
        // the SIGNED-IN customer, never the one named in the URL. Reading a role off the
        // record being VIEWED was precisely the earlier bug: it let anybody open an
        // administrator's dashboard and be handed that administrator's screen.
        const me = await getCustomer(signedInId)
        if (cancelled) return
        setState(isAdmin(me) ? 'allowed' : 'denied')
      } catch (err) {
        if (cancelled) return
        if (err.status === 404) {
          // signed in as somebody who no longer exists - deleted in another tab, or a wiped
          // database. The stored id is worthless, so it stops being believed. The same
          // judgement DashboardPage makes, for the same reason.
          signOut()
          setState('gone')
        } else {
          // could not ask. NOT treated as allowed, and not treated as denied either: the
          // honest answer is that the question went unanswered, and the screen says so rather
          // than guessing. Guessing allowed turns a flaky network into an open door; guessing
          // denied tells an administrator they are not one.
          setState('failed')
        }
      }
    }

    check()

    return () => { cancelled = true }
  }, [signedInId])

  /*
   * The order of these returns is the gate. `checking` comes first and the children are absent
   * from it, so the default path renders nothing of the page behind this.
   *
   * The wording is deliberately not "Checking permissions". There are no permissions here -
   * the API grants everything to everyone - and a reader seeing that word would reasonably
   * conclude something is being enforced. It says what every other pending screen here says.
   */
  if (state === 'checking') {
    return <p>Loading...</p>
  }

  if (state === 'allowed') {
    return <Outlet />
  }

  if (state === 'gone') {
    return (
      <Navigate
        to="/login"
        replace
        state={{ notice: 'That account no longer exists. Please sign in again.' }}
      />
    )
  }

  if (state === 'failed') {
    return (
      <section>
        <p className="error">
          Could not check which account you are signed in as. The server did not answer.
        </p>
        <p><Link to="/dashboard">Back to your dashboard</Link></p>
      </section>
    )
  }

  /*
   * Denied: back to your own dashboard, deliberately without a message.
   *
   * The only way to arrive here is by typing an address, since nothing in the interface links
   * a customer to an admin screen. Announcing "you may not view that" would also overstate
   * what happened by a wide margin - nothing was refused, because there is nothing here to
   * refuse against. This is not a screen anybody is forbidden from; it is a screen that is not
   * theirs, and being returned to the one that is says exactly that.
   */
  return <Navigate to={`/dashboard/${signedInId}`} replace />
}

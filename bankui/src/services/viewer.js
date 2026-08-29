/*
 * Who this browser tab is currently signed in as.
 *
 * One file, for the same reason services/api.js is one file: it is the only code in the app
 * that knows HOW that fact is remembered. Every component asks a function. When Phase 10
 * lands, this module is DELETED rather than adapted - see the bottom of this comment.
 *
 * ==========================================================================================
 * THE COMPROMISE, NAMED
 * ==========================================================================================
 *
 * Signing in returns a customer. A page refresh throws away every piece of React state, so
 * without something written down, a refresh drops you back to the sign-in form. Something has
 * to survive the reload, and every option for what that something is has a cost. This is the
 * one that was chosen, and what it costs:
 *
 * WHAT IS STORED: the customer's ID. A small integer. Nothing else.
 *
 *   NOT the password. That is absolute. The password is passed to one request and forgotten;
 *   it is never held in state longer than the submit, never stored, never logged. It is
 *   plaintext on the server for now, which makes it more sensitive rather than less.
 *
 *   NOT the customer object, and this is the interesting one. Caching the record would save a
 *   request on every load and it is what most apps do - but the record includes the ROLE, and
 *   then the client is holding its own claim about what it is allowed to see. Today that is
 *   inert, because nothing is enforced anywhere. It is still the wrong SHAPE: a client-held
 *   role claim is a genuine vulnerability class the moment there is anything to protect, and
 *   building the habit now means Phase 10 inherits it. Storing an id and re-reading the role
 *   from the server each load keeps the server the authority on who this person is, which is
 *   the arrangement that survives the arrival of real authentication.
 *
 * WHERE: sessionStorage, not localStorage. It is scoped to the tab and cleared when the tab
 * closes, so it cannot outlive the browsing context and be mistaken for a durable login.
 * localStorage would persist across restarts, which is precisely what a real session does and
 * precisely the impression this must not give.
 *
 * WHAT IT COSTS - the part that has to be said plainly:
 *
 *   A RESTORE DOES NOT RE-CHECK THE PASSWORD. On reload the app reads an id and shows that
 *   customer's dashboard. The credential was checked once, at sign-in, and never again. So
 *   anyone who can write to sessionStorage - which is the user, in DevTools, in about four
 *   seconds - can put any id there and get the admin dashboard.
 *
 *   That adds NO weakness, because there is none to add to: /dashboard/1 can simply be typed
 *   into the address bar and always could, and the API answers every request from every
 *   caller regardless. The UI is not a boundary and this does not make it less of one.
 *
 *   But it WOULD be a real vulnerability the moment anything is enforced - a stored identity
 *   the client can edit and the server trusts is the bug, not a step toward the fix. Hence
 *   the deletion note below rather than a migration note.
 *
 * REJECTED: storing nothing at all, so a refresh signs you out. The most honest option and it
 * was close. It loses because it buys nothing - it does not make the app any less open, since
 * the dashboard URL is typeable either way - while making the app's main screens unusable
 * after an accidental reload. Purity that costs the user and protects nobody is not restraint.
 *
 * ==========================================================================================
 * RE-EXAMINED, NOW THAT THIS GATES THE ENTIRE APPLICATION
 * ==========================================================================================
 *
 * The reasoning above was written when the stored id decided WHICH DASHBOARD to render. It now
 * decides whether any page in the application renders at all: RequireSignIn reads this value
 * and redirects everything else to the sign-in form. That is a much heavier load than the
 * original argument was written to carry, so it was re-examined rather than assumed to still
 * hold. Two of the three conclusions are unchanged. One is not, and it is the one about what
 * happens when this value goes bad.
 *
 * UNCHANGED, AND STILL THE HONEST POSITION: storing an id without re-checking the password on
 * restore gives away nothing, because there is nothing to give. The bypass is "write one
 * sessionStorage key in DevTools" where it used to be "type a different number into the
 * address bar" - which sounds like a downgrade and is not, because both describe the same
 * person doing the same thing to their own browser, and neither is the easy path. The easy
 * path is to ignore this application entirely and GET the API directly with no credential,
 * which works, has always worked, and is unaffected by anything in this file. A gate that
 * protects nothing cannot be weakened by the mechanism that opens it.
 *
 * UNCHANGED, AND NOW LOAD-BEARING: the id and only the id. The temptation to cache the whole
 * customer got stronger, not weaker - every page load behind the gate now re-fetches a record
 * the app just had - and it is still refused. Caching the role would make the client the
 * holder of its own claim about what it may see, and the client would then be deciding
 * something on the strength of a value the user can edit. That is inert while nothing is
 * enforced and it is the exact shape of a real vulnerability the moment something is. An id,
 * re-read from the server on every load, keeps the server the authority on who somebody is.
 *
 * AND THERE IS NO BETTER OPTION AVAILABLE, which is worth saying plainly rather than leaving
 * the compromise looking like a preference. Re-checking a credential on restore would mean
 * storing the credential - never - or presenting something the server issued and can verify,
 * which does not exist until Phase 10. There is no third design. This is not the best of
 * several shapes; it is the only shape, and what makes it acceptable is precisely that it
 * defends nothing, so there is nothing for it to defend badly.
 *
 * WHAT DID CHANGE: what a BAD value costs. It used to mean one dashboard showed an error. It
 * now means a visitor gets through the gate holding an id that means nothing, and every page
 * behind it fails - an application that looks signed in and is entirely unusable, with the
 * only fix being a "Sign out" control the user has no reason to suspect. So DashboardPage now
 * treats a 404 on the SIGNED-IN customer as a signal to sign out and return to the form with
 * an explanation, rather than as one more error to display. That is the part of this design
 * the heavier load genuinely broke, and it is fixed there rather than here - this module is
 * deliberately dumb, and "is this id still real" is a question only the server can answer.
 *
 * WHY THE VALIDITY CHECK IS NOT IN THE GATE: asking the server would mean rendering something
 * while waiting for the answer, and the something is the page being gated. RequireSignIn
 * checks presence, synchronously, and nothing else. Validity is discovered by the first page
 * that resolves the customer.
 *
 * ==========================================================================================
 * WHAT PHASE 10 DOES TO THIS FILE: deletes it. The server issues a token, the token says who
 * the caller is, and "who am I" stops being a question the client answers about itself. There
 * is nothing here to keep. A stand-in that pretends less is a stand-in that costs less to
 * remove.
 * ==========================================================================================
 */

// namespaced, so it is obvious in a DevTools storage pane what wrote this and why - and so it
// cannot collide with anything else served from the same origin.
const KEY = 'bankui.signedInCustomerId'

/*
 * Every access is wrapped, because sessionStorage throws rather than returning null in
 * situations that are not exotic: Safari's private mode has historically thrown on write, and
 * a browser configured to block site data throws on read. A storage failure must degrade to
 * "nobody is signed in" - an inconvenience - rather than taking down the page that asked.
 */
export function getSignedInId() {
  try {
    return sessionStorage.getItem(KEY)
  } catch {
    return null
  }
}

export function setSignedInId(id) {
  try {
    // String(), because sessionStorage stringifies anyway and it is better to do it where it
    // can be seen. The id goes into a URL next, where it is text regardless.
    sessionStorage.setItem(KEY, String(id))
  } catch {
    // deliberately silent. The sign-in itself SUCCEEDED - the credentials were checked and
    // the navigation is about to happen. All that is lost is surviving a refresh, and failing
    // the sign-in over that would be reporting the wrong thing as broken.
  }
}

export function signOut() {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    // nothing to do. If it cannot be removed it was almost certainly never written.
  }
}

import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'

import NoAuthNotice from '../components/NoAuthNotice.jsx'
import { signIn } from '../services/api.js'
import { setSignedInId } from '../services/viewer.js'

/*
 * Sign in. It now really does check a password - and it is still not authentication.
 *
 * This file used to be a placeholder that authenticated nobody and said so. The API has since
 * grown an endpoint that compares a username and password against the stored customer and
 * returns the matching record, role included, so the form is wired to it. What has NOT
 * changed is the thing the old comment existed to warn about, and it is worth being exact
 * about which part moved and which part did not.
 *
 * WHAT IS NOW REAL: the credential check. A wrong password is refused, by the server, against
 * the database. You cannot get somebody else's dashboard by typing their name in this form.
 *
 * WHAT IS STILL NOT REAL: everything that would make that check matter.
 *
 *   Nothing is issued. No token, no cookie, no session id - the response is a customer record
 *   and that is all.
 *   Nothing is remembered by the server. It does not know this exchange happened.
 *   THE NEXT REQUEST IS AS ANONYMOUS AS THE LAST. Every other endpoint still answers any
 *   caller with no credential whatsoever. The dashboard this form navigates to can be reached
 *   by typing its address, and the DELETE behind every button on it can be issued with curl
 *   by someone who has never seen this page.
 *
 * So signing in changes WHAT THE UI SHOWS YOU. It does not change WHAT THE SERVER WILL DO FOR
 * YOU. That sentence is the whole of it, and it is on the screen as well as in this comment,
 * because the person who most needs to read it is the one who never opens the source.
 *
 * The rule the old comment stated is unchanged and still load-bearing: authentication is a
 * SERVER decision, because a front end runs entirely on hardware the attacker controls. All a
 * client may legitimately do is collect a credential, hand it over, and then hide the controls
 * the server has already said no to - hiding as a courtesy to honest users, never as a
 * control. Every route behind a login must be refused by the API independently, on the
 * assumption that the UI was bypassed entirely, because it can be.
 *
 * WHAT PHASE 10 REPLACES THIS WITH: Spring Security on the API, a token the server issues and
 * verifies on every subsequent request, and endpoints that refuse callers who do not present
 * one. The form stays; what happens to its answer changes completely.
 *
 * PASSWORDS. Plaintext on the server for now, deliberately and temporarily - which makes the
 * value MORE sensitive here, not less, since there is no hash standing between a leak and the
 * real credential. So: it is held in state only as long as the field is on screen, sent to
 * exactly one request, cleared on both outcomes, and never stored, logged, or put in a URL.
 */
export default function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()

  const [form, setForm] = useState({ username: '', password: '' })

  /*
   * Seeded from the navigation state, so another page can hand this one something to say.
   * Two callers do: RegisterPage, when an account was created but the sign-in that followed
   * did not take, and DashboardPage, when a restored id turned out to belong to nobody.
   *
   * A lazy initialiser rather than an effect. It runs on the first render only, which is
   * exactly the semantics wanted - the message describes how the user ARRIVED, and the moment
   * they touch a field handleChange retires it like any other. An effect watching location
   * would put it back.
   *
   * Not a query parameter. This text is for the person on the screen and belongs nowhere near
   * the URL, which is copied, shared and logged.
   */
  const [message, setMessage] = useState(() => location.state?.notice ?? null)

  const [failed, setFailed] = useState(false)

  // disables the button while the request is out. Less about double submits than about the
  // form looking inert on a slow network, which is when people click again.
  const [busy, setBusy] = useState(false)

  const say = (text, isFailure = false) => {
    setMessage(text)
    setFailed(isFailure)
  }

  const handleChange = (event) => {
    const { name, value } = event.target
    setForm(prev => ({ ...prev, [name]: value }))
    // a message describes a past attempt; editing a field retires it. Same rule as every
    // other form in this app.
    setMessage(null)
  }

  const handleSubmit = async (event) => {
    event.preventDefault()

    setBusy(true)

    try {
      // Nothing is logged. A console.log of this object during development is how passwords
      // end up in a browser's console history and, later, in whatever collects front-end
      // logs. The two fields are passed by name rather than as the state object so nothing
      // else the form might carry can ride along.
      const customer = await signIn(form.username, form.password)

      // the password leaves state on BOTH outcomes, which is why this line is here rather
      // than in the success branch. A failed attempt leaves a real credential sitting in a
      // component that stays mounted; clearing it also means a retry starts from an empty
      // field, which is what a password manager expects.
      setForm(prev => ({ ...prev, password: '' }))

      if (customer === null) {
        /*
         * ONE MESSAGE FOR BOTH FAILURES, and it is the server's decision being honoured
         * rather than this component's preference.
         *
         * An unknown username and a wrong password come back as the same 401 with the same
         * empty body, deliberately, so that nobody can discover which usernames exist by
         * trying them one at a time and watching the answers differ. That property is worth
         * something and it is FRAGILE IN EXACTLY ONE DIRECTION: the server cannot leak it,
         * but the client can hand it away for free by being more specific than its source.
         * "We don't recognise that username" would do it, and would look like helpfulness.
         *
         * So the wording names both possibilities and chooses neither. It is also, as it
         * happens, the truthful sentence - this code genuinely does not know which was wrong.
         */
        say('Username or password not recognised.')
        return
      }

      // written down before navigating, so a refresh on the dashboard lands back on the
      // dashboard rather than here. services/viewer.js sets out exactly what is stored, what
      // is deliberately not, and what that trade costs.
      setSignedInId(customer.id)

      // replace: true, so the back button does not return to the sign-in form. Going "back"
      // to a form that has already been submitted is a dead end - the fields are empty and
      // submitting again is not what the user meant by back.
      navigate(`/dashboard/${customer.id}`, { replace: true })
    } catch (err) {
      // reached only for a genuine failure - signIn turns a rejected credential into null
      // rather than a throw, precisely so these two cannot be confused.
      if (err.status === 0) {
        say(err.message, true)
      } else if (err.status === 404) {
        // THE ENDPOINT is missing, not the customer. A rejected credential is a 401 and
        // signIn turns that into null above, so a 404 reaching here can only mean the route
        // is not there - a deployment or proxy problem, not a login problem.
        //
        // This branch was unreachable until the api module stopped absorbing 404 as "not
        // recognised". Worth keeping distinct: telling someone their password is wrong when
        // the sign-in endpoint is missing sends them to change a password that was fine.
        say('Sign-in is unavailable. The server did not answer at that address.', true)
      } else {
        say(`Unexpected error: ${err.status}`, true)
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <section>
      <h1>Sign in</h1>

      <NoAuthNotice />

      <form onSubmit={handleSubmit}>
        <label>
          Username:
          {/* autoComplete tells the browser's password manager what these fields are, so it
              can offer to fill and save them. Getting the names right is what makes managers
              work, and password managers are the single biggest practical improvement to
              real users' password hygiene - worth two attributes. */}
          <input
            type="text"
            name="username"
            autoComplete="username"
            value={form.username}
            onChange={handleChange}
            disabled={busy}
          />
        </label>
        <label>
          Password:
          {/* type="password" masks the field and keeps the value out of autofill history.
              It is a display and browser-behaviour choice - it protects against someone
              reading the screen, and nothing else. The value still travels as plain text in
              the request body; what protects it in transit is HTTPS. */}
          <input
            type="password"
            name="password"
            autoComplete="current-password"
            value={form.password}
            onChange={handleChange}
            disabled={busy}
          />
        </label>

        <button type="submit" disabled={busy}>Sign in</button>
      </form>

      {message && <p className={failed ? 'error' : 'message'}>{message}</p>}

      {/* the only other thing an unauthenticated visitor may do, so it is the only other
          thing this page offers. */}
      <p>No account yet? <Link to="/register">Create one</Link></p>
    </section>
  )
}

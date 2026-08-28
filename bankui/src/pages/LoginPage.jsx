import { useState } from 'react'

/*
 * Sign in. PLACEHOLDER - it authenticates nobody, and says so.
 *
 * The form is real, the inputs are controlled, and submitting does nothing but report that
 * the feature does not exist yet. That is the point: it is scaffolding to build against in
 * Phase 10, not a login.
 *
 * WHY IT MUST NOT FAKE IT. The tempting placeholder is to accept any credentials and set
 * something like isLoggedIn in state, so the app "works". That is not a weak login, it is
 * not a login at all - the API would still serve every endpoint to anyone who asks, and the
 * check would be a variable in the browser that a user can flip in DevTools in about four
 * seconds.
 *
 * The rule, worth stating once and keeping: authentication is a SERVER decision. A front end
 * cannot authenticate anything, because it runs entirely on hardware the attacker controls.
 * All it can legitimately do is collect a credential, hand it over, and then hide the
 * controls the server has already said no to - hiding as a courtesy to honest users, never
 * as a control. Every route behind a login must be refused by the API independently, on the
 * assumption that the UI was bypassed entirely, because it can be: curl does not run this
 * code.
 *
 * A fake login is also actively dangerous as a placeholder, because it looks finished. Real
 * systems have shipped exactly this.
 *
 * WHAT PHASE 10 REPLACES THIS WITH: Spring Security on the API, a credential POSTed to a
 * real endpoint, and a token the server issues and verifies on every subsequent request.
 * Where that token is stored is its own decision with real trade-offs - deferred to the
 * phase that can make it properly.
 */
export default function LoginPage() {
  const [form, setForm] = useState({ username: '', password: '' })
  const [message, setMessage] = useState(null)

  const handleChange = (event) => {
    const { name, value } = event.target
    setForm(prev => ({ ...prev, [name]: value }))
    setMessage(null)
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    // Nothing is sent anywhere, and nothing is logged. A console.log of this object during
    // development is how passwords end up in a browser's console history and, later, in
    // whatever collects front-end logs.
    setMessage('Sign-in is not available yet.')
  }

  return (
    <section>
      <h1>Sign in</h1>

      <p className="lead">
        Not implemented yet. Every part of the application is currently open, and the API
        does not check who is asking.
      </p>

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
          />
        </label>

        <button type="submit">Sign in</button>
      </form>

      {message && <p className="message">{message}</p>}
    </section>
  )
}

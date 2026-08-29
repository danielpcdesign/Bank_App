import { useState } from 'react'

import { createCustomer } from '../services/api.js'

/*
 * Creates a customer. Username, password, full name - the three fields the endpoint accepts.
 *
 * ==========================================================================================
 * ONE FORM, THREE SCREENS - and why this is not two components any more
 * ==========================================================================================
 *
 * This was AddCustomerForm, and there was a second, near-identical form living inside
 * RegisterPage. They existed separately because they called different endpoints:
 * POST /customers took an id and a role, POST /customers/register took neither. Two
 * operations, two requests, two forms - and the difference was real enough to be worth the
 * duplication.
 *
 * The API merged them. There is now one create, it takes three fields, and the server assigns
 * the id and the role. So the two forms were making the byte-identical request, and the case
 * for two components had to be re-made rather than assumed. It does not survive:
 *
 *   The FIELDS are the same three. The VALIDATION is the same (none client-side, on purpose -
 *   see below). The STATUS INTERPRETATION is the same 409/400/0 switch, and that switch is
 *   precisely the sort of thing that drifts when it is copied: one copy learns about a new
 *   status and the other does not, and nobody notices because both still compile.
 *
 *   What actually differs between the callers is a heading, a button label, a success
 *   sentence, and WHAT HAPPENS NEXT. The first three are props. The last is a callback - and
 *   "the child announces, the parent decides what that means" is the discipline this codebase
 *   has applied to every other form in it. Registration signing you in, and an admin staying
 *   put to refetch a table, are two different decisions about one event - which is exactly the
 *   shape onCreated exists for.
 *
 * So: one component, three call sites - the customers page, the admin dashboard, and the
 * public register page. If the operations diverge again, this splits again; today they have
 * not diverged, and two copies of one switch is not a design.
 *
 * ==========================================================================================
 * NO ID INPUT AND NO ROLE SELECTOR, and the reason has changed
 * ==========================================================================================
 *
 * There used to be both. The role selector offered admin, then stopped offering it, and the
 * comment here said - correctly, at the time - that removing the option PREVENTED NOTHING: the
 * endpoint accepted role: "ADMIN" from any caller with curl, and a hidden control is not a
 * restriction. That reasoning was sound, and it is now obsolete rather than wrong.
 *
 * The endpoint no longer reads a role from the body. From anybody. The capability is not
 * hidden from this form, it is gone from the create path, and it is gone on the server, which
 * is the only place a restriction can exist. Same for the id: there is no field to send, so
 * there is nothing for a form to offer.
 *
 * THE LIMIT, WHICH MUST NOT BE OVERSTATED. This closes the CREATE path only.
 * PUT /api/v1/customers/{id} still accepts a role from any caller with no credential - the
 * deliberate UI-only gating decision recorded on setCustomerRole, unchanged. Anybody may
 * register an ordinary account and then promote it with a single PUT.
 *
 *   "Registration cannot mint an administrator" is TRUE.
 *   "The API cannot mint an administrator" is FALSE.
 *
 * Nothing here, and no copy on screen, may imply the second.
 *
 * Controlled inputs, as everywhere else in this app: an <input> can hold its own value in the
 * DOM, and React would then have no idea what the user typed. A controlled input takes its
 * value FROM state and reports every keystroke back, so state is the single source of truth.
 */
export default function CreateCustomerForm({
  heading,
  submitLabel = 'Create',
  successMessage = 'Customer created successfully.',
  onCreated,
}) {

  // one object rather than three useState calls, so the shape matches the body being sent and
  // there is no assembly step. Empty strings, not undefined: React treats a controlled input
  // whose value is undefined as UNcontrolled, and warns when it later becomes controlled.
  const [form, setForm] = useState({ username: '', password: '', fullName: '' })

  const [message, setMessage] = useState(null)

  // which style the message wears. A taken username and a network failure are both "it did not
  // happen", and they must not look alike.
  const [failed, setFailed] = useState(false)

  const [busy, setBusy] = useState(false)

  const say = (text, isFailure = false) => {
    setMessage(text)
    setFailed(isFailure)
  }

  // one handler for all three inputs: each input's name= matches its key in the form object,
  // so event.target.name says which field to update. [name] is a computed key - the key is the
  // value of the variable, not the literal string "name".
  const handleChange = (event) => {
    const { name, value } = event.target
    // the updater form asks React for the current value rather than the one captured when this
    // handler was created. Spread, not mutate: assigning form.username = ... would change the
    // object React is already holding, so the next render compares it to itself and does
    // nothing.
    setForm(prev => ({ ...prev, [name]: value }))
    // a message describes a past attempt; the moment the user edits a field it is no longer
    // about what is on screen, so it is retired.
    setMessage(null)
  }

  const handleSubmit = async (event) => {
    // a form submit is a browser navigation. Without this the page reloads and the entire
    // React app is thrown away - the reason a broken React form flickers and does nothing.
    event.preventDefault()

    // captured before the fields are cleared. onCreated may need the credentials - the
    // register page signs in with them - and by the time it is called this state is empty.
    const submitted = { ...form }

    setBusy(true)

    let created
    try {
      // no `required` attributes and no client-side checks, on purpose: the point is to see
      // the server's 400. Client validation is a convenience for honest users, not a check -
      // anything at all can POST to the API directly.
      created = await createCustomer(submitted)
    } catch (err) {
      // the API distinguishes three outcomes and the message follows it. The status survives
      // the trip through the api module precisely so this switch is possible - that is what
      // ApiError is for.
      if (err.status === 409) {
        // state-dependent: the same request succeeds once the name is free. That is why the
        // API answers 409 rather than 400 here, the same split it makes for a declined
        // withdrawal.
        say('That username is already taken.')
      } else if (err.status === 400) {
        // says no more than that, deliberately - the server does not name the offending field,
        // so the client cannot either.
        say('Invalid request. Check that every field is filled in.')
      } else if (err.status === 0) {
        say(err.message, true)
      } else {
        say(`Unexpected error: ${err.status}`, true)
      }
      setBusy(false)
      return
    }

    // the password leaves state first, and unconditionally. It is plaintext at both ends of
    // the wire; the least this component can do is not keep a copy after it has been sent.
    setForm({ username: '', password: '', fullName: '' })
    say(successMessage)

    /*
     * AWAITED, and outside the try above.
     *
     * Awaited, because the caller's follow-up may be slow - the register page signs in and
     * navigates - and leaving the form enabled during it invites a second submit that would
     * create a second customer.
     *
     * Outside the try, because a failure inside the CALLER is not a failure of this request.
     * Catching it there would map somebody else's error onto this form's status switch and
     * report "that username is already taken" for a sign-in that timed out. The callback owns
     * its own failures; this only makes sure the button comes back either way.
     */
    try {
      await onCreated?.(submitted, created)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      {heading && <h2>{heading}</h2>}

      <label>
        Username:
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
        Full name:
        <input
          type="text"
          name="fullName"
          autoComplete="name"
          value={form.fullName}
          onChange={handleChange}
          disabled={busy}
        />
      </label>

      <label>
        Password:
        {/* "new-password" tells a password manager to OFFER to generate one rather than
            trying to fill this with a saved credential. Browser behaviour, not protection:
            the value travels as plaintext in the body and is stored as plaintext at the other
            end, which is a known and deliberately temporary state of this application. */}
        <input
          type="password"
          name="password"
          autoComplete="new-password"
          value={form.password}
          onChange={handleChange}
          disabled={busy}
        />
      </label>

      <button type="submit" disabled={busy}>{submitLabel}</button>

      {message && <p className={failed ? 'error' : 'message'}>{message}</p>}
    </form>
  )
}

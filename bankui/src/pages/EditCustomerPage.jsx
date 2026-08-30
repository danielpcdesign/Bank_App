import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'

import { getCustomer, roleOf, updateCustomer } from '../services/api.js'

/*
 * Edits one customer, reached at /customers/:id/edit.
 *
 * This lives in pages/ rather than components/ because it is a destination, not a piece.
 * A page is what a route renders and what a URL names; a component is something a page is
 * built out of.
 *
 * Two hooks the router provides:
 *
 *   useParams()   - reads the :id out of the current URL. Always a STRING, because a URL is
 *                   text; there is no type information in a path segment.
 *   useNavigate() - programmatic navigation, for when the move is a consequence rather than
 *                   a click. Saving succeeds -> go back to the list. A <Link> cannot express
 *                   that, because there is nothing for the user to click.
 */
export default function EditCustomerPage() {
  const { id } = useParams()
  const navigate = useNavigate()

  // null, not {}, so the render below can distinguish "still loading" from "loaded".
  // it also keeps the inputs from mounting before there is data: a controlled input whose
  // value is undefined is treated by React as uncontrolled, and switching it later warns.
  const [form, setForm] = useState(null)
  const [message, setMessage] = useState(null)

  // held OUTSIDE form, deliberately. `form` is the body of the PUT, and role is not part of
  // that body any more - the server owns it. Keeping it in a separate variable is what makes
  // "shown but not sent" true by construction rather than by a filter somebody has to
  // remember when building the request.
  const [role, setRole] = useState('')

  useEffect(() => {
    const loadCustomer = async () => {
      try {
        const customer = await getCustomer(id)
        /*
         * FOUR FIELDS, NAMED, rather than a spread of whatever the GET returned.
         *
         * This state is what the PUT will be built from, so it holds exactly the fields a PUT
         * replaces - the ones a client both reads and owns - and nothing else. The two it
         * leaves behind are the interesting ones:
         *
         *   password   is not here because it was never in the response. The field is
         *              WRITE_ONLY, so no client has seen one. Omitting it from the save is now
         *              CORRECT rather than merely unavoidable: the server preserves the stored
         *              password when the body leaves it out.
         *
         *   accountIds is not here because the server owns that list. It is maintained by the
         *              account endpoints as accounts are opened and closed, and PUT takes it
         *              from the stored document and ignores the body entirely.
         *
         *              This form used to carry it, and echoed it back untouched. That was the
         *              safer option while PUT still replaced the field - omitting it would
         *              have nulled the list outright, where echoing could only lose an update
         *              in a race between the GET and the PUT. The server no longer reads it,
         *              so the echo protects against nothing and its only remaining effect is
         *              to imply to the next reader that the value matters. It is dropped
         *              deliberately, and this paragraph is what makes the absence readable as
         *              a decision rather than an oversight.
         *
         *   role       joined them. It used to be loaded, held, and PUT back, because it was a
         *              field a client both read and owned. It is server-owned now: PUT replaces
         *              username and fullName and nothing else, and a role in the body is
         *              ignored. So it is not in this state at all - carrying a value the save
         *              cannot affect would only invite somebody to add a control for it.
         *
         *              It is still SHOWN further down, read from the loaded customer rather
         *              than from form state, because whose record this is and what kind of
         *              customer they are is worth seeing while editing their name.
         *
         * What is left is exactly what a PUT replaces, so the state and the request are the
         * same three fields by construction rather than by remembering to keep them in step.
         */
        setForm({
          id: customer.id,
          username: customer.username,
          fullName: customer.fullName,
        })
        setRole(roleOf(customer))
      } catch (err) {
        // 404 is a real, expected answer here - someone typed an id that does not exist, or
        // followed a stale link. it deserves its own message, not the generic status text.
        setMessage(err.status === 404 ? `No customer with id ${id}.` : err.message)
      }
    }
    loadCustomer()

    // [id], NOT []. This is the difference routing introduces. Navigating from
    // /customers/2/edit to /customers/3/edit matches the SAME route, so React reuses this
    // component instance instead of remounting it. With an empty array the effect would
    // never re-run and the page would keep showing customer 2 under a URL saying 3.
    // A dependency array is a claim about what the effect reads. This one reads id.
  }, [id])

  const handleChange = (event) => {
    const { name, value } = event.target
    // the updater form, setForm(prev => ...), asks React for the current value rather than
    // using the one captured when this handler was created. equivalent here, correct in
    // general - it is the version that survives two updates landing in the same tick.
    setForm(prev => ({ ...prev, [name]: value }))
    // a message describes a past attempt. the moment the user changes the input it is no
    // longer about what is on screen, so it is retired.
    setMessage(null)
  }

  const handleSubmit = async (event) => {
    event.preventDefault()

    /*
     * THERE IS NO ROLE GUARD HERE ANY MORE, and its removal is a fix rather than a relaxation.
     *
     * This used to refuse the save when a customer had no role, because role was part of the
     * PUT body and the server required one - so a record predating the field could not be
     * edited at all until somebody set it. The remedy it pointed at, a selector on the admin
     * dashboard, no longer exists either.
     *
     * Role is server-owned now and is not in the body, so a missing one cannot fail a save,
     * and blocking one would strand exactly the records nobody has looked at recently:
     * unable to edit a name because of a field this form does not send.
     */
    try {
      // form still carries the id it was loaded with, and it must: the controller rejects a
      // body whose id disagrees with the path. not offering an input for it is what keeps
      // those two in agreement.
      await updateCustomer(id, form)
      // navigating away IS the success message. The dashboard mounts fresh on arrival, so its
      // effect refetches and the customer list already shows the new values - no callback
      // needed here, unlike the create form. /dashboard rather than the old /customers, which
      // no longer exists: only an administrator can reach this page, so "back" is their
      // dashboard, which is where the list they came from lives.
      navigate('/dashboard')
    } catch (err) {
      if (err.status === 400) {
        setMessage('Invalid request.')
      } else if (err.status === 404) {
        // deleted by someone else while this page was open. PUT does not create, so there is
        // nothing to save into - saying so is more useful than a generic failure.
        setMessage('That customer no longer exists.')
      } else if (err.status === 0) {
        setMessage(err.message)
      } else {
        setMessage(`Unexpected error: ${err.status}`)
      }
    }
  }

  // loading and not-found are different states and the user should be able to tell them
  // apart. an unstyled "Loading..." that never resolves is a bug report; a clear "no such
  // customer" is an answer.
  if (message && !form) {
    return (
      <>
        <p className="error">{message}</p>
        <Link to="/dashboard">Back to the dashboard</Link>
      </>
    )
  }

  if (!form) {
    return <p>Loading...</p>
  }

  return (
    <>
      <h1>Edit customer {form.id}</h1>

      <form onSubmit={handleSubmit}>
        {/* no input for id, on purpose. the path is the identity, so an editable id could
            only ever produce the 400 the controller returns on a mismatch - the UI should
            not offer a move whose single possible outcome is an error. changing an id is
            not an edit anyway: it is a delete plus a create, which the API already exposes
            separately. */}
        <label>
          Username:
          <input type="text" name="username" value={form.username} onChange={handleChange} />
        </label>
        <label>
          Full name:
          <input type="text" name="fullName" value={form.fullName} onChange={handleChange} />
        </label>

        {/* SHOWN, NOT EDITABLE, and no control at all - not even a disabled one.

            This used to say that role changes belonged on the administrator dashboard, and
            that the separation was a decision about screen layout rather than enforcement,
            since the API accepted a role change from any caller. Both halves are now out of
            date: there is no control on the dashboard either, because no endpoint accepts a
            role. It is assigned at seed time and changeable through nothing.

            A disabled <select> would still be the wrong shape, and now for a stronger reason
            than before. Disabled implies "not available to you" - a claim about permission.
            The truth is "not available to anybody, through any interface", which is not a
            permission at all but a property of the system, and plain text says it without
            implying somebody else could. */}
        <p>
          Role: <strong className="capitalise">{role || 'not set'}</strong>{' '}
          <span className="muted">&mdash; set when the account was created; no endpoint changes it.</span>
        </p>

        {/* NO PASSWORD FIELD, deliberately. The server marks it write-only, so the customer
            loaded above has no password on it and there is nothing to render back - which is
            correct, and is also why this form cannot offer to change one. A password change
            is its own operation with its own rules: confirm the current one, enter the new
            one twice, decide what it invalidates. None of that has been decided, and a field
            that silently blanks a credential because it rendered empty is the failure this
            omission avoids.

            That open question is now closed: PUT preserves the stored password when the
            body omits one, so this form sending no password is the correct request rather
            than the only one it could make. services/api.js records the rule the server
            settled on - a PUT replaces the fields a client both reads and owns, and a
            password fails the read half. */}

        <button type="submit">Save</button>
      </form>

      {/* .error, not .message, and the bug this fixes was one class.

          A save that fails is the only thing this line can ever show: success navigates away
          instead of saying so, and handleChange retires a message the moment a field is
          touched. So "That customer no longer exists." was rendering in the neutral style this
          app uses for information - the style a DECLINED withdrawal wears, which is deliberately
          not a failure. A real failure dressed as an ordinary notice is how somebody leaves a
          page believing they saved.

          Note what is NOT here: the `failed` flag every other form in this app carries.
          Those forms genuinely have both outcomes on one line - CreateCustomerForm says
          "Customer created" and "That username is already taken" through the same <p>, and
          TransactionForm has three outcomes wearing two styles. This page has one, because
          navigating away is its success message. A flag that can only ever hold true is not
          consistency, it is a second thing to keep in step with the first. */}
      {message && <p className="error">{message}</p>}

      <p><Link to="/dashboard">Back to the dashboard</Link></p>
    </>
  )
}

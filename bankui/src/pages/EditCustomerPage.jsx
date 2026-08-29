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
         * ROLE is translated on the way in, and it is the one field that could not be loaded
         * raw. Two problems, one call. The wire spells it "CUSTOMER" while this page renders
         * the word, so an untranslated value would read as shouting. And customers written
         * before the field existed come back with role: null, which would make the value below
         * null - and a form value of null is how React decides an input is UNcontrolled, after
         * which it warns the moment a real value arrives. roleOf returns '' for an absent role,
         * which is a string, so the field is controlled from the first render, and '' stays
         * distinguishable from 'customer' - which is what stops a save from inventing a role
         * nobody chose.
         */
        setForm({
          id: customer.id,
          username: customer.username,
          fullName: customer.fullName,
          role: roleOf(customer),
        })
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
     * A customer written before roles existed has none, and the server now requires one. This
     * page cannot set one - role editing lives on the admin dashboard and nowhere else - so
     * it refuses the save and says where the fix is.
     *
     * Substituting the ordinary role here would be the tempting one line, and it would be
     * wrong twice over: it would assign a value the user never chose, on a field they cannot
     * even see, as a side effect of editing their full name - and it would do it silently to
     * precisely the records nobody has looked at recently. Sending it anyway and letting the
     * 400 come back would be honest but useless, because the server does not name the
     * offending field and the message would be "Invalid request." for a form where exactly
     * one field is at fault and the UI already knows which.
     */
    if (form.role === '') {
      setMessage('This customer has no role. An administrator can set one from their dashboard.')
      return
    }

    try {
      // form still carries the id it was loaded with, and it must: the controller rejects a
      // body whose id disagrees with the path. not offering an input for it is what keeps
      // those two in agreement.
      await updateCustomer(id, form)
      // navigating away IS the success message. CustomersPage mounts fresh on arrival, so
      // its effect refetches and the list already shows the new values - no callback needed
      // here, unlike the create form.
      navigate('/customers')
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
        <Link to="/customers">Back to list</Link>
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

        {/* SHOWN, NOT EDITABLE, and there is no control here at all - not a disabled one.
            Role changes belong on the administrator dashboard, which is the screen that is
            about administering other people's records; this page is about a customer's own
            details and is reachable from the ordinary customer list.

            To be exact about what that separation is: it is a decision about which screen an
            operation belongs on. It is NOT enforcement. The API accepts a role change from
            any caller with no credential, and moving the control does not alter that by one
            byte. The value still round-trips through this form's state and back out in the
            PUT, so saving a name does not silently clear a role.

            A disabled <select> was the alternative and reads worse: it implies the choice
            exists here and is currently unavailable to you, which is a claim about permission
            - the exact impression this whole app is trying not to give. */}
        <p>
          Role: <strong className="capitalise">{form.role || 'not set'}</strong>{' '}
          <span className="muted">&mdash; changed from the administrator dashboard.</span>
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

      {message && <p className="message">{message}</p>}

      <p><Link to="/customers">Back to list</Link></p>
    </>
  )
}

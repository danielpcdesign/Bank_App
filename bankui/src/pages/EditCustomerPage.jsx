import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'

import { getCustomer, updateCustomer } from '../services/api.js'

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
        setForm(await getCustomer(id))
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

        <button type="submit">Save</button>
      </form>

      {message && <p className="message">{message}</p>}

      <p><Link to="/customers">Back to list</Link></p>
    </>
  )
}

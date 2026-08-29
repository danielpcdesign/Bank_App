import { useState } from 'react'

import { createCustomer } from '../services/api.js'

/*
 * Creates a customer. Lives in components/ rather than pages/ because it is a piece of the
 * customers screen, not a destination of its own.
 *
 * (The course repo gives its equivalent a route - /employees/new. Reasonable when a form is
 * long enough to compete with the list for space. Three fields is not that, and keeping the
 * form above the table means the new row appears without a navigation.)
 *
 * Controlled inputs. An <input> can hold its own value in the DOM, and React would then have
 * no idea what the user typed. A controlled input instead takes its value FROM state and
 * reports every keystroke back through onChange, so state is the single source of truth and
 * the DOM is a mirror of it.
 *
 * Lifting state up. This component can create a customer but does not own the list, so it
 * cannot refresh it. Props only travel downward - a child never writes to its parent. The way
 * out is for the parent to pass a FUNCTION down, which the child calls when something
 * happened. The child announces; the parent decides what that means. Same discipline as the
 * service returning a boolean and letting the controller choose the status code.
 */
export default function AddCustomerForm({ onCreated }) {

  // one object rather than three useState calls, so the shape matches the JSON body the API
  // expects and the POST body is the state with no assembly step. empty strings, not
  // undefined: React treats a controlled input whose value is undefined as UNcontrolled.
  const [form, setForm] = useState({ id: '', username: '', fullName: '' })

  // one piece of state, not two. success and failure are mutually exclusive here, and two
  // booleans that must never both be true is a bug waiting to happen.
  const [message, setMessage] = useState(null)

  // one handler for all three inputs: each input's name= matches its key in the form object,
  // so event.target.name says which field to update. [name] is a computed key - the key is
  // the value of the variable, not the literal string "name".
  const handleChange = (event) => {
    const { name, value } = event.target
    // the updater form asks React for the current value rather than the one captured when
    // this handler was created. spread, not mutate: assigning form.id = ... would change the
    // object React is already holding, so the next render compares it to itself, sees
    // nothing, and does not re-render.
    setForm(prev => ({ ...prev, [name]: value }))
    // a message describes a past attempt. the moment the user changes an input it is no
    // longer about what is on screen, so it is retired. (EditCustomerPage already did this;
    // this file now matches it.)
    setMessage(null)
  }

  const handleSubmit = async (event) => {
    // a <form> submit is a browser navigation. without this the page reloads and the entire
    // React app is thrown away - the reason a broken React form flickers and does nothing.
    event.preventDefault()

    try {
      // note: <input type="number"> still yields a STRING. Jackson coerces "4" into the
      // Integer, so this works - by way of a server-side leniency setting rather than by
      // being correct.
      await createCustomer(form)
      setForm({ id: '', username: '', fullName: '' })
      setMessage('Customer created successfully.')
      if (onCreated) onCreated()
    } catch (err) {
      // the API distinguishes three outcomes, so the message does too. The status survives
      // the trip through the api module precisely so this switch is still possible - that is
      // what ApiError is for.
      if (err.status === 409) {
        setMessage('ID already taken.')
      } else if (err.status === 400) {
        // says no more than that, deliberately - the server does not name the offending
        // field, so the client cannot either
        setMessage('Invalid request.')
      } else if (err.status === 0) {
        setMessage(err.message)
      } else {
        setMessage(`Unexpected error: ${err.status}`)
      }
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <h2>Add customer</h2>

      {/* no `required` attribute on purpose: the point is to see the server's 400. client
          validation is a convenience for honest users, not a check - anything can POST to
          the API directly. */}
      <label>
        ID:
        <input type="number" name="id" value={form.id} onChange={handleChange} />
      </label>
      <label>
        Username:
        <input type="text" name="username" value={form.username} onChange={handleChange} />
      </label>
      <label>
        Full name:
        <input type="text" name="fullName" value={form.fullName} onChange={handleChange} />
      </label>

      <button type="submit">Create</button>

      {message && <p className="message">{message}</p>}
    </form>
  )
}

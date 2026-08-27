import { useState } from 'react'

/*
 * Creates a customer via POST /api/v1/customers.
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
    const { name, value } = event.target;
    // spread, not mutate. assigning form.id = ... would change the object React is already
    // holding, so the next render compares it to itself, sees nothing, and does not re-render.
    setForm({ ...form, [name]: value });
  };

  const handleSubmit = (event) => {
    // a <form> submit is a browser navigation. without this the page reloads and the entire
    // React app is thrown away - the reason a broken React form flickers and does nothing.
    event.preventDefault();
    fetch('/api/v1/customers', {
      method: 'POST',
      // without this header Spring answers 415
      headers: { 'Content-Type': 'application/json' },
      // note: <input type="number"> still yields a STRING. Jackson coerces "4" into the
      // Integer, so this works - by way of a server-side leniency setting rather than by
      // being correct.
      body: JSON.stringify(form)
    })
    .then(response => {
      // the API distinguishes three outcomes, so the message does too
      if (response.status === 201) {
        setForm({ id: '', username: '', fullName: '' });
        setMessage('Customer created successfully.');
        if (onCreated) onCreated();
      } else if (response.status === 409) {
        setMessage('ID already taken.');
      } else if (response.status === 400) {
        // says no more than that, deliberately - the server does not name the offending
        // field, so the client cannot either
        setMessage('Invalid request.');
      } else {
        setMessage(`Unexpected error: ${response.status}`);
      }
    })
    .catch(err => setMessage(`Network error: ${err.message}`));
  };

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
  );
}

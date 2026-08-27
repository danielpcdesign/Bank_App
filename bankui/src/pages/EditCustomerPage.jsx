import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'

/*
 * Edits one customer, reached at /customers/:id/edit.
 *
 * This lives in pages/ rather than components/ because it is a destination, not a piece.
 * A page is what a route renders and what a URL names; a component is something a page is
 * built out of. The distinction is a convention, not a rule React enforces, but it answers
 * "where does this file go" consistently once the tree grows.
 *
 * Two hooks the router provides:
 *
 *   useParams()  - reads the :id out of the current URL. Always a STRING, because a URL is
 *                  text; there is no type information in a path segment.
 *   useNavigate() - programmatic navigation, for when the move is a consequence rather than
 *                  a click. Saving succeeds -> go back to the list. A <Link> cannot express
 *                  that, because there is nothing for the user to click.
 */
export default function EditCustomerPage() {
  const { id } = useParams();
  const navigate = useNavigate();

  // null, not {}, so the render below can distinguish "still loading" from "loaded".
  // it also keeps the inputs from mounting before there is data: a controlled input whose
  // value is undefined is treated by React as uncontrolled, and switching it later warns.
  const [form, setForm] = useState(null);
  const [message, setMessage] = useState(null);

  useEffect(() =>
    {
    fetch(`/api/v1/customers/${id}`)
      .then(response =>
        {
        // 404 is a real, expected answer here - someone typed an id that does not exist, or
        // followed a stale link. it deserves its own message, not the generic status text.
        if (response.status === 404) {
          throw new Error(`No customer with id ${id}.`);
        }
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        return response.json();
        })
      .then(data => setForm(data))
      .catch(err => setMessage(err.message));

    // [id], NOT []. This is the difference routing introduces. Navigating from
    // /customers/2/edit to /customers/3/edit matches the SAME route, so React reuses this
    // component instance instead of remounting it. With an empty array the effect would
    // never re-run and the page would keep showing customer 2 under a URL saying 3.
    // A dependency array is a claim about what the effect reads. This one reads id.
    }, [id])

  // the same shape as AddCustomerForm's handler, deliberately duplicated rather than shared.
  // two callers is not yet a reason to extract one - the third is. pulling it out now would
  // couple two files that only happen to look alike today.
  const handleChange = (event) => {
    const { name, value } = event.target;
    // the updater form, setForm(prev => ...), asks React for the current value rather than
    // using the one captured when this handler was created. equivalent here, correct in
    // general - it is the version that survives two updates landing in the same tick.
    setForm(prev => ({ ...prev, [name]: value }));
    // a message describes a past attempt. the moment the user changes the input it is no
    // longer about what is on screen, so it is retired.
    setMessage(null);
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    fetch(`/api/v1/customers/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      // form still carries the id it was loaded with, and it must: the controller rejects a
      // body whose id disagrees with the path. not offering an input for it is what keeps
      // those two in agreement.
      body: JSON.stringify(form)
    })
    .then(response => {
      if (response.status === 200) {
        // navigating away IS the success message. CustomerList unmounts and remounts on the
        // way back, so its effect refetches and the list already shows the new values -
        // no callback needed here, unlike the create form.
        navigate('/');
      } else if (response.status === 400) {
        setMessage('Invalid request.');
      } else if (response.status === 404) {
        // deleted by someone else while this page was open. PUT does not create, so there is
        // nothing to save into - saying so is more useful than a generic failure.
        setMessage('That customer no longer exists.');
      } else {
        setMessage(`Unexpected error: ${response.status}`);
      }
    })
    .catch(err => setMessage(`Network error: ${err.message}`));
  };

  // loading and not-found are different states and the user should be able to tell them
  // apart. an unstyled "Loading..." that never resolves is a bug report; a clear "no such
  // customer" is an answer.
  if (message && !form) {
    return (
      <>
        <p className="error">{message}</p>
        <Link to="/">Back to list</Link>
      </>
    );
  }

  if (!form) {
    return <p>Loading...</p>;
  }

  return (
    <>
      <h2>Edit customer {form.id}</h2>

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

      <p><Link to="/">Back to list</Link></p>
    </>
  );
}

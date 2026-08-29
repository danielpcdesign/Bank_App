import { memo } from 'react'
import { Link } from 'react-router'

import { ROLES, roleOf } from '../services/api.js'

/*
 * One row of the customer table.
 *
 * It receives a customer and renders it. That is the whole contract - no state, no fetching,
 * no knowledge of where the data came from or how many siblings it has. A component that
 * only turns props into markup is called presentational, and it is the cheapest kind to
 * reason about: same props in, same markup out, every time.
 *
 * Props are read-only. React hands them down and the child does not write back; when a child
 * needs to cause a change it calls a function the parent passed it. That is the same
 * one-directional discipline as the API layers - the row reports, the list decides.
 */
function CustomerRow({ customer, onDelete, onRoleChange }) {

  // '' when the record predates the field. Kept distinct from 'customer' so the select below
  // can report "not set" rather than quietly showing a role nobody chose.
  const role = roleOf(customer)

  return (
    <tr>
      <td>{customer.id}</td>
      <td>{customer.username}</td>
      <td>{customer.fullName}</td>

      {/* THE ROLE COLUMN APPEARS ONLY WHERE onRoleChange IS PASSED, which is the admin
          dashboard and nowhere else. The customer dashboard does not render a customer table
          at all, and the ordinary /customers list passes no handler, so neither shows this.

          What that separation is: a decision about which screen the operation belongs on.
          What it is NOT: enforcement. The API accepts a role change from any caller with no
          credential of any kind, and it will keep doing so. Hiding this select hides it from
          people using the UI as intended and from nobody else - curl does not render
          components. The label beside it says as much on the screen, because a reader who
          never opens this file is exactly the reader who would otherwise assume the
          restriction is real. */}
      {onRoleChange && (
        <td>
          <select
            value={role}
            className="capitalise"
            onChange={(event) => onRoleChange(customer, event.target.value)}
          >
            {/* present only while the record genuinely has no role, and disabled so it cannot
                be chosen. It REPORTS a state the data is already in rather than offering it:
                once a role is set there is no way back to having none, and a dropdown
                implying otherwise would be lying about the model. */}
            {role === '' && <option value="" disabled>Not set</option>}
            {ROLES.map(name => (
              <option key={name} value={name} className="capitalise">{name}</option>
            ))}
          </select>
        </td>
      )}
      <td>
        {/* the destination is built from this row's own data, which is all the row needs to
            know. it does not navigate and does not know what lives at that path - it states
            where the customer's edit page is, and the router resolves it. */}
        <Link to={`/customers/${customer.id}/edit`}>Edit</Link>
        {' '}
        {/* Accounts live on their own page rather than in a column here: a balance would fit
            in a cell, but the deposit and withdraw controls beside it would not, and half of
            the feature in a cell is worse than all of it one click away. Added to the
            existing Actions column rather than as a new one - the column is already named
            for holding several controls, and a working table is cheapest left alone.

            It points at that customer's dashboard rather than at a separate accounts page,
            because with no authentication those are the same screen. "This customer's
            accounts" and "the app as this customer sees it" differ only once there is a
            principal to make one of them privileged, and there is not. Two routes rendering
            one view would have been the pretence. */}
        <Link to={`/dashboard/${customer.id}`}>Accounts</Link>
        {' '}
        {/* onClick calls onDelete with this row's id. the row does not delete anything and
            does not know what deleting means - it reports that a button was pressed. same
            contract as the form's onCreated. */}
        <button type="button" onClick={() => onDelete(customer.id)}>
          Delete
        </button>
      </td>
    </tr>
  );
}

/*
 * memo caches the last render and reuses it when the props are unchanged, so a parent
 * re-render does not automatically become a child re-render.
 *
 * "Unchanged" means SHALLOW equality - each prop compared with Object.is, one level deep.
 * That is the whole behaviour, and it is what decides when this pays and when it does not:
 *
 *   Skips the re-render when CustomerList re-renders for a reason unrelated to the data -
 *   setError firing, or any state added to that component later. The customers array and
 *   every object in it are still the same references, so every row is skipped.
 *
 *   Does NOT skip after a refetch. response.json() builds brand new objects, so
 *   customer !== customer even when every field is identical. Shallow equality fails, every
 *   row renders. memo cannot see that the data is the same; it only sees two references.
 *
 * Worth being straight about the size of the win here: three rows of three <td>s. This is a
 * demonstration of the mechanism, not a measured optimisation, and memo is not free - it
 * costs a comparison per prop per render plus the retained previous result. The honest rule
 * is to reach for it when a profiler shows a component re-rendering expensively, not by
 * default. It starts genuinely paying at hundreds of rows, or when a row grows real work.
 *
 * The function is named rather than inlined so React DevTools shows CustomerRow instead of
 * an anonymous memo wrapper.
 */
export default memo(CustomerRow);

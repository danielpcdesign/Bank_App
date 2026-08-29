import { memo } from 'react'
import { Link } from 'react-router'

import { roleOf } from '../services/api.js'

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
function CustomerRow({ customer, onDelete }) {

  // '' when the record predates the field. Kept distinct from 'customer' so the cell below
  // reports "not set" rather than quietly showing a role nobody chose.
  const role = roleOf(customer)

  return (
    <tr>
      <td>{customer.id}</td>
      <td>{customer.username}</td>
      <td>{customer.fullName}</td>

      {/* THE ROLE IS SHOWN AND NOT EDITABLE, and that is now a fact about the API rather than
          a choice this component makes.

          There used to be a <select> here, switched on by an onRoleChange prop the admin
          dashboard passed, and a comment explaining at length that its admin-only placement
          was a decision about which screen an operation belongs on and emphatically NOT
          enforcement - because the API accepted a role change from any caller with no
          credential at all.

          That comment is now obsolete rather than wrong. PUT /api/v1/customers/{id} replaces
          username and fullName and nothing else; role joined id, password and accountIds as
          server-owned. A selector here would submit, get a 200, change nothing, and redraw the
          old value - and a control that appears to work while silently doing nothing is worse
          than no control, because it teaches the operator that the system lies rather than
          that the operation is unavailable.

          So the column reports instead of offering, and reporting still earns its place: which
          of these people is an administrator is the first thing somebody reading this table
          wants to know, and it is the field that decides who can reach this screen at all. */}
      <td className="capitalise">{role || <span className="muted">not set</span>}</td>
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

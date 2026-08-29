import CustomerRow from './CustomerRow.jsx'

/*
 * A table of customers. Given an array, draws it.
 *
 * That is the entire contract. No state, no fetching, no idea where the array came from or
 * what happens when Delete is pressed - it calls the function it was handed and stops
 * caring. The same presentational discipline CustomerRow already followed, applied one level
 * up now that the fetching lives in the page that owns the data.
 *
 * Everything it needs arrives as props, which means it can be rendered anywhere - a search
 * results screen, a dashboard - without dragging an API call along with it.
 *
 * IT HAS ONE CALLER AGAIN. It briefly had two, and an optional onRoleChange prop that added a
 * whole column, because the customers page and the admin dashboard wanted different versions
 * of it. Both of those are gone: the standalone /customers route was removed - any signed-in
 * customer could reach it and delete anybody - and role editing was removed because the API
 * stopped accepting it. So the table is unconditional again, which is the simpler thing to
 * have, and the Role column below is now shown rather than edited.
 */
export default function CustomerList({ customers, onDelete }) {

  // an empty list is a legitimate result, not an error, and it deserves a sentence rather
  // than a table with a blank body. the caller has already distinguished this from "still
  // loading" and "the request failed" - by the time we get here, the answer is genuinely
  // "there are none".
  if (customers.length === 0) {
    return <p>No customers yet.</p>
  }

  return (
    <table>
      <thead>
        <tr>
          <th>Id</th>
          <th>Username</th>
          <th>Full name</th>
          {/* unconditional now. It was gated behind onRoleChange while the column held an
              editable selector and only one screen was meant to have it; the column is
              read-only, this table has one caller, and a condition that is always true is
              just a place for a bug to live. */}
          <th>Role</th>
          {/* one column for both Edit and Delete. "Actions" names what the column contains
              rather than what any single control does, which is why it survived adding a
              second one. */}
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        {/* key is how React matches elements between renders to decide what to reuse.
            Without a stable one it falls back to array position, and rows edited or deleted
            later update the wrong row. id is a genuine identity - the API guarantees it is
            unique. It also belongs HERE rather than inside CustomerRow: key is not a prop,
            React strips it for reconciliation, so it has to go on the element the .map()
            creates. */}
        {customers.map(customer => (
          <CustomerRow key={customer.id} customer={customer} onDelete={onDelete} />
        ))}
      </tbody>
    </table>
  )
}

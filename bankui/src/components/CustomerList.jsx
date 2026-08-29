import CustomerRow from './CustomerRow.jsx'

/*
 * A table of customers. Given an array, draws it.
 *
 * That is the entire contract. No state, no fetching, no idea where the array came from or
 * what happens when Delete is pressed - it calls the function it was handed and stops
 * caring. The same presentational discipline CustomerRow already followed, applied one level
 * up now that the fetching has moved out to CustomersPage.
 *
 * Everything it needs arrives as props, which means it can be rendered anywhere - a search
 * results screen, a dashboard - without dragging an API call along with it. It is now
 * rendered on two screens, and it took no change to serve the second: the customers page and
 * the admin dashboard hand it different arrays and different handlers, and it draws a table.
 *
 * onRoleChange is optional and adds a whole column, the same way AccountList's ownerOf does.
 * Passing it is what makes this the admin's version of the table. That is a decision about
 * where an operation belongs on screen and NOT a permission - the API takes a role change
 * from anyone - which is why the control carries a label saying so rather than relying on its
 * absence elsewhere to imply a rule that does not exist.
 */
export default function CustomerList({ customers, onDelete, onRoleChange }) {

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
          {/* driven by the same prop that fills the cell, so a column can never appear with
              nothing under it or vice versa - two conditions kept in step by being one. The
              admin dashboard passes onRoleChange; the ordinary customer list does not, and
              gets the table it already had. */}
          {onRoleChange && <th>Role</th>}
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
          <CustomerRow
            key={customer.id}
            customer={customer}
            onDelete={onDelete}
            onRoleChange={onRoleChange}
          />
        ))}
      </tbody>
    </table>
  )
}

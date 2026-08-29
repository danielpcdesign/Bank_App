import { Link } from 'react-router'

import AccountList from './AccountList.jsx'
import AddAccountForm from './AddAccountForm.jsx'
import CreateCustomerForm from './CreateCustomerForm.jsx'
import CustomerList from './CustomerList.jsx'

/*
 * What an administrator sees: every account in the bank, every customer, and the create and
 * delete operations for both. It mirrors Admin.dashboard() in the console app - view all
 * accounts, view all customers, create/delete customer, create/delete account - which is
 * where the scope comes from.
 *
 * Presentational, and almost entirely assembled from parts that already existed:
 *
 *   CustomerList + CreateCustomerForm  reused from the customers screen. The list already
 *                                    took an onDelete and already drew Edit and Accounts
 *                                    links; the form already announced upward through
 *                                    onCreated. Neither needed a line changed to serve a
 *                                    second screen, which is what the container /
 *                                    presentational split was for in the first place. The
 *                                    form is now shared with the public register page too.
 *   AccountList                      reused with different props - an owner column and a
 *                                    delete button instead of a transaction form.
 *   AddAccountForm                   the one genuinely new control, because opening an
 *                                    account is an operation no screen offered before.
 *
 *   CustomerList's Role column        the same component again, with one more prop. Changing
 *                                    a role is an administrative act, so the control lives on
 *                                    the administrative screen - a decision about placement,
 *                                    and emphatically not about permission.
 *
 * WHAT AN ADMIN IS NOT ALLOWED TO DO HERE, and why that phrasing is wrong. Nothing on this
 * page is a permission. Every control below calls an endpoint that the API serves to anyone
 * who asks, with no credential of any kind, and hiding these controls from a non-admin hides
 * them only from someone using the UI as intended. A caller with curl is entirely unaffected.
 * So the honest description of this file is: it is the screen that is USEFUL to an
 * administrator, not the screen that is PERMITTED to one. LoginPage.jsx makes the full
 * argument and is worth reading before anyone is tempted to treat this as a boundary.
 */
export default function AdminDashboard({
  accounts,
  customers,
  ownerOf,
  onDeleteAccount,
  onDeleteCustomer,
  onChanged,
}) {
  return (
    <>
      <h2>All accounts</h2>

      {/* ownerOf turns on the Owner column. An admin looking at accounts from every customer
          at once cannot read a row that does not say whose it is - which is exactly the
          column a customer's own dashboard has no use for. */}
      <AccountList
        accounts={accounts}
        ownerOf={ownerOf}
        onDelete={onDeleteAccount}
        emptyMessage="There are no accounts in the bank yet."
      />

      {/* no deposit or withdraw here, deliberately. The console app gives those to the
          customer and gives the admin CRUD, and the split is worth keeping: an administrator
          moving money in and out of somebody else's account through the same control the
          owner uses is an operation that should look different from the owner using it. The
          API's PUT /accounts/{id} can write a balance directly if that is ever wanted, and it
          is not wanted yet. */}

      <AddAccountForm customers={customers} onCreated={onChanged} />

      <h2>All customers</h2>

      {/* This is now the ONLY place the customer list appears. It used to also have its own
          route at /customers, which any signed-in customer could reach and use to delete
          anybody - the gap the user found. The route is gone; the list lives here. */}
      <CustomerList customers={customers} onDelete={onDeleteCustomer} />

      {/* ON SCREEN, not only in a comment, and the two halves of this are deliberately not
          the same claim. The first sentence describes something the server does; the second
          describes something only this interface does. A reader who cannot tell those apart
          will trust the wrong one. */}
      <p className="muted">
        Roles are fixed. No endpoint changes a customer&rsquo;s role, so the only administrators
        are the ones created at seed time &mdash; that part is enforced by the server and holds
        for any caller. Restricting this list to administrators is not: the API still serves
        every customer, and every delete, to anyone who asks.
      </p>

      {/* the same component the public register page renders, with a different heading and
          a different callback. Since the API merged its two create endpoints into one, an
          administrator adding a customer and a visitor registering are the identical request -
          so they are the identical form, and what differs is only what happens afterwards. */}
      <CreateCustomerForm heading="Add customer" onCreated={onChanged} />

      <p><Link to="/dashboard">View as someone else</Link></p>
    </>
  )
}

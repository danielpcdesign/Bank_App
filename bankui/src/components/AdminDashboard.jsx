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
 *   CustomerList + CreateCustomerForm  inherited from the standalone /customers screen, which
 *                                    has since been DELETED - any signed-in customer could
 *                                    reach it and delete anybody. The parts outlived the page:
 *                                    the list already took an onDelete and already drew Edit
 *                                    and Accounts links, the form already announced upward
 *                                    through onCreated, and neither needed a line changed to
 *                                    serve this screen instead. That is what the container /
 *                                    presentational split buys - a route can be removed
 *                                    without taking its components with it. The form is shared
 *                                    with the public register page too.
 *   AccountList                      reused with different props - an owner column and a
 *                                    delete button instead of a transaction form.
 *   AddAccountForm                   the one genuinely new control, because opening an
 *                                    account is an operation no screen offered before.
 *
 *   CustomerList's Role column       SHOWN, NOT EDITED - and this entry used to say the
 *                                    opposite: that changing a role was an administrative act
 *                                    whose control therefore belonged on the administrative
 *                                    screen. There is no such control anywhere now. PUT
 *                                    /customers/{id} replaces username and fullName, and role
 *                                    is server-owned. The old note was careful to call the
 *                                    placement a layout decision rather than a permission,
 *                                    which was correct - it has simply been overtaken by
 *                                    something stronger, since the capability is now gone from
 *                                    the API instead of positioned within this UI.
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
          owner uses is an operation that should look different from the owner using it.

          THE ESCAPE HATCH THIS COMMENT USED TO NAME IS GONE. It said "the API's
          PUT /accounts/{id} can write a balance directly if that is ever wanted". It cannot,
          any more: that route binds UpdateAccountRequest(type, overdraftLimit), so no endpoint
          anywhere writes a balance. Deposit and withdraw are now the only two operations in
          the system that move money, which makes the omission below a bigger decision than it
          was when it was written.

          =====================================================================
          COUPLED TO DashboardPage. DO NOT TAKE THE TWO DECISIONS SEPARATELY.
          =====================================================================

          Because accounts are created at zero and nothing writes a balance, an account is
          funded ONLY by an admin opening the owner's dashboard and using that customer's
          transaction form. Withholding the controls here is what makes that the single path;
          DashboardPage carries the full note beside the line that grants it. Adding a funding
          control to this screen is the change that would make that path optional - and it has
          to come first if it is ever wanted, because the alternative order leaves new accounts
          stranded at zero. */}

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

      {/* "View as someone else" USED TO SIT HERE, and it was a link to /dashboard - which
          resolves to your OWN dashboard, so on this page it returned you to the page you were
          already on. It was left behind when ViewAsPage was deleted, and ViewAsPage was deleted
          on purpose: a chooser that steps into anybody's dashboard without a password makes the
          password decorative, and the password is the one part of this that genuinely works.

          Deleted rather than relabelled. The label was not the defect - it advertised a
          capability that was removed deliberately, and renaming it would have kept a control
          for a feature nobody wants back. An admin who needs somebody else's dashboard follows
          their name in the accounts table above, which is a link to exactly that. */}
    </>
  )
}

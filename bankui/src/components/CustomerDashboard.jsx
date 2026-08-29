import { Link } from 'react-router'

import AccountList from './AccountList.jsx'

/*
 * What a customer sees: their own accounts, their balances, and the two operations that move
 * money. Nothing else - no other customer's records, no create, no delete.
 *
 * Presentational. It is handed a customer and an array of accounts and draws them; the
 * fetching, the failure handling and the refetch after a transaction all belong to
 * DashboardPage. Rendered with a made-up customer and three made-up accounts it would show
 * the right thing, which is the test.
 *
 * It is thin on purpose. AccountList already draws accounts, TransactionForm already moves
 * money, and Money already formats it - so a customer dashboard is those pieces with a
 * heading on top, and writing a second account table for it would have been the mistake. What
 * this file actually contributes is the FRAMING: whose money this is, said once at the top,
 * so no row has to repeat it.
 */
export default function CustomerDashboard({ customer, accounts, onCompleted }) {
  return (
    <>
      <h2>Your accounts</h2>

      <p className="muted">
        {customer.fullName} &middot; {customer.username} &middot; customer #{customer.id}
      </p>

      {/* no owner column, deliberately: every row here has the same owner, and a column
          repeating one name down the page is noise. The admin view passes ownerOf and gets
          the column; that is the whole difference between the two tables. */}
      <AccountList
        accounts={accounts}
        onCompleted={onCompleted}
        emptyMessage="You have no accounts yet. An administrator can open one for you."
      />

      {/* the customer cannot open or close an account - that is an admin operation in the
          console app this is modelled on, and it stays one here. Saying where it went is
          better than a control that is simply absent, which reads as an oversight. */}
      <p className="muted">
        Opening and closing accounts is done by an administrator.
      </p>

      <p><Link to="/dashboard">View as someone else</Link></p>
    </>
  )
}

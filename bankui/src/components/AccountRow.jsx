import { Link } from 'react-router'

import Money from './Money.jsx'
import TransactionForm from './TransactionForm.jsx'

/*
 * One row of the accounts table.
 *
 * Presentational, on the same contract as CustomerRow: it is handed an account and renders
 * it, with no state, no fetching and no idea where the object came from. The transaction form
 * nested in the last cell does own state - but it owns its OWN, exactly as
 * CreateCustomerForm does inside the customers screen, and this row neither reads it nor
 * knows it is there beyond passing an id and a callback through.
 *
 * THE OPTIONAL PROPS are what let one table serve both dashboards, and each is a whole cell
 * rather than a styling flag:
 *
 *   ownerOf     - a lookup from account id to the customer who owns it. Present only on the
 *                 admin view, where accounts from every customer are mixed together and a row
 *                 with no name on it is unreadable. Absent on a customer's own dashboard,
 *                 where every row has the same owner and a column repeating it is noise.
 *   onCompleted - deposit and withdraw. Present where somebody is looking at their own money.
 *   onDelete    - close the account. Present on the admin view.
 *
 * Three optional props is about where a component starts drifting into a bag of flags, so it
 * is worth saying why this is still one component rather than two. The COLUMNS are identical:
 * id, type, balance, overdraft limit, in that order, formatted the same way, with the same
 * treatment of a negative. That is the expensive part to keep consistent, and copying it into
 * an AdminAccountRow would give every future change to how money is displayed two places to
 * land - which is exactly how two tables end up disagreeing about what an overdraft is. What
 * differs between the dashboards is which controls sit beside the numbers, and that is a
 * props decision.
 *
 * NOT wrapped in memo, and the omission is deliberate rather than an oversight - CustomerRow
 * sitting next door is. memo skips a re-render when every prop is unchanged by shallow
 * comparison, and the only thing that re-renders this table is a refetch, which builds brand
 * new objects out of response.json(). Every prop would compare unequal, every row would
 * render anyway, and the comparison would be pure cost.
 */
export default function AccountRow({ account, ownerOf, onCompleted, onDelete }) {

  // undefined when this table has no owner column at all; null when it has one and this
  // particular account turns out to be in nobody's list. Different situations, and only the
  // second one has anything to say.
  const owner = ownerOf ? ownerOf(account.id) : undefined

  return (
    <tr>
      <td>{account.id}</td>

      {/* the type arrives from the server as an enum constant and reaches this component
          lowercased by the api module, so what is left is a word. Capitalising it is a
          presentational decision and belongs here: "Checking" is a label, "CHECKING" is a
          constant, and a table of somebody's accounts is not the place to shout. */}
      <td className="capitalise">{account.type}</td>

      {ownerOf && (
        <td>
          {owner
            // a link, because the obvious next thing an admin wants after seeing whose
            // account this is, is that person's own view of it.
            ? <Link to={`/dashboard/${owner.id}`}>{owner.fullName}</Link>
            /*
             * AN ACCOUNT OWNED BY NOBODY. KEPT, AND HERE IS THE DECISION.
             *
             * This existed because POST /api/v1/accounts created an account with a balance
             * and no customer listing it - real money in a real record that no dashboard but
             * this one could reach. That endpoint now requires a customer id, so no NEW
             * account can arrive in this state, and the branch is unreachable for anything
             * created from here on.
             *
             * It stays anyway, for data rather than for code. The collection predates the
             * fix, and an ownerless row written before it is exactly the row that most needs
             * naming: it is invisible on every other screen, and the alternative to naming it
             * is an empty cell that reads as a rendering bug and gets ignored. Deleting the
             * branch would not delete the rows.
             *
             * The condition for removing it is a fact about the database, not about the API:
             * once the accounts collection is known to have no unowned documents, this goes,
             * and the join in DashboardPage becomes total. Until somebody has checked, a
             * label costs one line and an unexplained blank costs somebody an afternoon.
             */
            : <span className="muted">Unassigned</span>}
        </td>
      )}

      {/* .num right-aligns and uses tabular figures, so the digits of every balance line up
          in their columns. Left-aligned money is readable one row at a time and unscannable
          down a list, which is the only way anyone actually reads a column of balances. */}
      <td className="num"><Money amount={account.balance} /></td>

      {/* the limit is the MAGNITUDE - how far below zero this account may go - normalised in
          the api module so it reads the same whichever sign convention the back end picked.
          Savings is 0, which is the honest rendering of "no overdraft" and the reason it is
          not blanked out: an empty cell would read as "unknown". */}
      <td className="num"><Money amount={account.overdraftLimit} /></td>

      {/* "Actions", like CustomerRow's, names what the column holds rather than what any one
          control does - which is why it survives holding a form on one screen and a button on
          another. Rendered at all only when there is something to put in it. */}
      {(onCompleted || onDelete) && (
        <td>
          {onCompleted && (
            <TransactionForm accountId={account.id} onCompleted={onCompleted} />
          )}
          {onDelete && (
            <button type="button" onClick={() => onDelete(account.id)}>
              Delete
            </button>
          )}
        </td>
      )}
    </tr>
  )
}

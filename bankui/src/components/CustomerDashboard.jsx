import AccountList from './AccountList.jsx'

/*
 * What a customer sees: their own accounts, their balances, and the two operations that move
 * money. Nothing else - no other customer's records, no create, no delete.
 *
 * "THEIR OWN" IS A DISPLAY CONVENTION, NOT A PROTECTION. The heading below reads "Your
 * accounts", which could easily be taken as a guarantee, and it is not one. The account
 * endpoints have no ownership check of any kind: any caller with no credential can read or
 * drain any account by id - verified live, not inferred. DashboardPage decides which accounts
 * to FETCH for this screen; nothing decides which accounts a person is ALLOWED to touch,
 * because that question needs an authenticated principal and there is none until phase 10.
 *
 * So this shows one customer's accounts because that is the useful screen, not because the
 * others are out of reach. services/api.js carries the full statement of what is and is not
 * enforced; the short version is that the customer-side gating elsewhere in this app does not
 * extend to accounts at all.
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
 *
 * ==========================================================================================
 * WHOSE ACCOUNTS - AND WHY A PRONOUN IS THE MOST LOAD-BEARING WORD ON THE SCREEN
 * ==========================================================================================
 *
 * This screen serves two viewers. A customer looking at their own accounts, and an
 * ADMINISTRATOR INSPECTING SOMEBODY ELSE'S - /dashboard/4 opened by an admin renders this
 * component with customer 4's record, because an admin looking at somebody's dashboard should
 * see THEIR screen rather than admin controls pointed at their data.
 *
 * Every string here used to be written for the first viewer only. "Your accounts", "You have no
 * accounts yet" - printed unchanged directly beneath DashboardPage's "Viewing the accounts of
 * Carol Johnson". The component had the subject's data and no idea of the subject's
 * RELATIONSHIP to the person reading, which is a different fact and the one the copy needed.
 *
 * WHY THAT IS MORE THAN WORDING: THE DEPOSIT AND WITHDRAW FORM RENDERS ON THIS VIEW. An admin
 * inspecting a customer gets TransactionForm on every row - the same controls the owner uses,
 * against money that is not theirs. The pronoun was the only thing on the page distinguishing
 * "I am moving my money" from "I am moving Carol's money", and it was wrong in exactly the case
 * where somebody is about to act on an account that is not their own. A misleading label beside
 * a button that moves money is not a copy defect; it is the wrong caption on the one control
 * here with consequences.
 *
 * So the component takes `isSelf` and addresses the owner BY NAME whenever the viewer is not
 * the owner. A flag rather than a name prop, because the name is already on `customer` and a
 * second copy of one field is a second thing that can disagree with the first. The flag is the
 * fact this component genuinely could not derive - it never sees the signed-in id, and it
 * should not: knowing who is asking is DashboardPage's job, and it already computes exactly
 * this comparison for its own "Viewing the accounts of" line.
 *
 * EVERY string branches, not only the heading. A page that says "Accounts held by Carol
 * Johnson" and then "You have no accounts yet" underneath is worse than one that is uniformly
 * wrong, because the inconsistency reads as a rendering bug and invites the reader to decide
 * which half to believe.
 */
export default function CustomerDashboard({ customer, accounts, isSelf, onCompleted }) {
  return (
    <>
      {/* named rather than possessive - "Accounts held by Carol Johnson" instead of "Carol
          Johnson's accounts" - which sidesteps an apostrophe that would have to be a typographic
          &rsquo; in JSX text and an escape inside a template string, and reads as a statement
          about ownership, which is the point being made. */}
      <h2>{isSelf ? 'Your accounts' : `Accounts held by ${customer.fullName}`}</h2>

      <p className="muted">
        {customer.fullName} &middot; {customer.username} &middot; customer #{customer.id}
      </p>

      {/* WHOSE MONEY THE BUTTONS BELOW MOVE, said once here rather than on every row. The
          deposit and withdraw controls are byte-identical in both cases - they are the same
          component with the same labels - so nothing on the control itself can carry the
          distinction, and the row is the wrong place to repeat it five times. Only rendered
          when the reader is not the owner: an owner does not need telling that their own money
          is theirs, and a line that is always present stops being read. */}
      {!isSelf && (
        <p className="muted">
          Deposits and withdrawals below move {customer.fullName}&rsquo;s money, not your own.
        </p>
      )}

      {/* no owner column, deliberately: every row here has the same owner, and a column
          repeating one name down the page is noise. The admin view passes ownerOf and gets
          the column; that is the whole difference between the two tables. */}
      <AccountList
        accounts={accounts}
        onCompleted={onCompleted}
        emptyMessage={isSelf
          ? 'You have no accounts yet. An administrator can open one for you.'
          : `${customer.fullName} has no accounts yet. You can open one from your own dashboard.`}
      />

      {/* the customer cannot open or close an account - that is an admin operation in the
          console app this is modelled on, and it stays one here. Saying where it went is
          better than a control that is simply absent, which reads as an oversight.

          This one branches too, and unbranched it was not merely impersonal but unhelpful:
          telling an ADMINISTRATOR that an administrator does this sends them looking for a
          control on a screen that deliberately does not have one. The form is on their own
          dashboard, so that is what it now says. */}
      <p className="muted">
        {isSelf
          ? 'Opening and closing accounts is done by an administrator.'
          : 'Opening and closing accounts is done from your own dashboard.'}
      </p>

      {/* "View as someone else" USED TO SIT HERE. It linked to /dashboard, which resolves to
          the signed-in customer's own dashboard - so for the person whose screen this is it
          led back to this page, and it could never have done anything else. It survived the
          deletion of ViewAsPage, the chooser it was written for, which went on purpose: picking
          anybody's dashboard out of a list with no password would make the sign-in beside it
          pointless. Deleted rather than relabelled, since the label was not the problem. */}
    </>
  )
}

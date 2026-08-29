import AccountRow from './AccountRow.jsx'

/*
 * A table of accounts. Given an array, draws it.
 *
 * The same contract CustomerList holds, and deliberately the same shape: no state, no
 * fetching, no knowledge of where the array came from or what happens when a transaction
 * completes. It could be rendered with three made-up objects and would show the right thing,
 * which is the test for whether a component belongs in components/ at all.
 *
 * ONE TABLE, TWO DASHBOARDS. A customer's own accounts and an admin's view of every account
 * in the bank are the same five facts in the same order; what differs is whether an owner is
 * worth naming and which controls sit at the end of the row. So the difference is carried in
 * props - ownerOf, onCompleted, onDelete, each optional and each adding a cell - rather than
 * in a second component that would have to be kept formatting money the same way as this one.
 *
 * `emptyMessage` is a prop for the same reason. "This customer has no accounts" and "There
 * are no accounts in the bank yet" are different sentences about different situations, and
 * the component drawing the table is not the one that knows which it is looking at.
 */
export default function AccountList({
  accounts,
  ownerOf,
  onCompleted,
  onDelete,
  emptyMessage = 'This customer has no accounts.',
}) {

  /*
   * EMPTY IS NOT AN ERROR. A customer who has not opened an account yet is a perfectly
   * ordinary customer, and the answer to "what are their accounts" is a sentence, not a
   * blank space and not a red message.
   *
   * The caller has already ruled out the other two possibilities before rendering this - it
   * knows the difference between "still loading", "the request failed" and "loaded, and the
   * answer is none". By the time control reaches here the answer is genuinely none. Exactly
   * CustomerList's treatment of the same situation, matched on purpose.
   */
  if (accounts.length === 0) {
    return <p>{emptyMessage}</p>
  }

  return (
    <table>
      <thead>
        <tr>
          <th>Id</th>
          <th>Type</th>
          {/* the header is driven by the same prop that fills the cell, so a column can never
              appear with nothing under it or vice versa. Two conditions kept in step by being
              one condition. */}
          {ownerOf && <th>Owner</th>}
          <th className="num">Balance</th>
          {/* named for what the number means rather than for the field it came from. "Limit"
              alone would leave the reader guessing whether it is a floor, a ceiling or a
              daily cap. */}
          <th className="num">Overdraft limit</th>
          {(onCompleted || onDelete) && <th>Actions</th>}
        </tr>
      </thead>
      <tbody>
        {/* key on the account id, and it matters more here than in the customers table:
            each row can contain a form holding typed-in state, and React uses the key to
            decide which rendered row is which between renders. Fall back to array position
            and a refetch that reorders the list would carry a half-typed amount from one
            account's row to another's - which on a bank means an amount typed against one
            account being submitted against a different one. */}
        {accounts.map(account => (
          <AccountRow
            key={account.id}
            account={account}
            ownerOf={ownerOf}
            onCompleted={onCompleted}
            onDelete={onDelete}
          />
        ))}
      </tbody>
    </table>
  )
}

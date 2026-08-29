import { useState } from 'react'

import { deposit, withdraw } from '../services/api.js'

/*
 * Deposit into or withdraw from one account.
 *
 * In components/ rather than pages/ for the same reason CreateCustomerForm is: it is a piece
 * of a screen, not a destination. And it follows that file's shape closely on purpose - it owns
 * its own input state and its own message, calls the API directly, and announces upward when
 * something happened rather than reaching for the list it does not own. The parent decides
 * what that means.
 *
 * THE THREE OUTCOMES, which is the whole reason this file is more than ten lines.
 *
 * The API's deposit/withdraw resolve to a boolean meaning "did the rules allow it", not "did
 * it work" - the same boolean AccountOperations declares in the Java, and AGENTS.md is
 * explicit that a refusal is an in-contract outcome. So a submit here ends in one of three
 * places, and collapsing any two of them would be wrong:
 *
 *   ACCEPTED  - the balance changed. Clear the input, say so, tell the parent to refetch.
 *   REFUSED   - the server understood perfectly and said no. A savings account will not go
 *               below zero; a checking account will not go past its overdraft limit. This is
 *               INFORMATION. It uses the neutral .message style, not .error, and the typed
 *               amount is deliberately KEPT so the user can edit it down rather than retype
 *               it from nothing.
 *   FAILED    - no usable answer. Network down, account gone, server broke. That is .error.
 *
 * The refusal wording says what the rule did without restating the rule. This component
 * could be handed the balance and the limit and phrase it precisely - "that is $40 past your
 * overdraft limit" - but then the client would be duplicating a rule the server owns, and
 * the two would drift the first time the server's changed. The server is the authority on
 * whether; the client only reports that the answer was no.
 */
export default function TransactionForm({ accountId, onCompleted }) {

  // a string, not a number, because that is what an <input> holds. Number() happens once, at
  // the point of validation, so "" and "abc" stay distinguishable until then - converted
  // eagerly they would both be indistinguishable rubbish.
  const [amount, setAmount] = useState('')

  const [message, setMessage] = useState(null)

  // which style the message wears. Refused and failed are both "the money did not move", and
  // they must not look alike - so the outcome picks the class rather than the wording alone
  // carrying a difference the eye never sees.
  const [failed, setFailed] = useState(false)

  // in flight. Not cosmetic: without it a second click before the first response lands sends
  // a second, real deposit. Disabling is the cheapest guard, and on a bank the cost of a
  // double submit is not a duplicate row, it is duplicate money.
  const [busy, setBusy] = useState(false)

  const say = (text, isFailure = false) => {
    setMessage(text)
    setFailed(isFailure)
  }

  const handleChange = (event) => {
    setAmount(event.target.value)
    // a message describes a past attempt; editing the amount retires it. Same rule as
    // CreateCustomerForm and EditCustomerPage.
    setMessage(null)
  }

  /*
   * WHOLE NUMBERS ONLY, and what happens to the ones that are not.
   *
   * Every amount in this system is an integer. The input carries step="1", which is a
   * courtesy - it makes the spinner step by ones and lets the browser flag a fractional
   * value - but it is not a control: anything at all can POST to the API directly, so the
   * server enforces the real rule. CreateCustomerForm already makes this point about its
   * own deliberately missing `required` attributes.
   *
   * A pasted "10.50" is REFUSED, not truncated. Truncating silently would move $10 when the
   * user typed $10.50 - the one failure mode here that costs somebody money without telling
   * them. Refusing costs a keystroke.
   *
   * Returns the amount, or null having already said why.
   */
  const parseAmount = () => {
    const text = amount.trim()

    // empty is its own case, and not the same as zero: "" becomes 0 under Number(), and
    // "enter an amount" is a different instruction from "that must be more than zero".
    if (text === '') {
      say('Enter an amount.')
      return null
    }

    const value = Number(text)

    // type="number" hands back "" for text it cannot parse, so this mostly catches paste and
    // autofill. Cheap, and the alternative is NaN reaching the API.
    if (!Number.isFinite(value)) {
      say('That is not a number.')
      return null
    }

    if (!Number.isInteger(value)) {
      say('Amounts are whole numbers - no cents.')
      return null
    }

    // zero and negative together, because the server treats them identically: Account.deposit
    // and both withdraw() overrides refuse amt <= 0. A negative withdrawal is a deposit with
    // the sign flipped, and an operation that means its own opposite is worth refusing at
    // both ends.
    if (value <= 0) {
      say('Amount must be greater than zero.')
      return null
    }

    return value
  }

  /*
   * One form, two actions.
   *
   * Both buttons are submit buttons and event.nativeEvent.submitter says which was pressed.
   * That is the browser's own mechanism for this, and it is worth using rather than hanging
   * onClick on two plain buttons: it keeps the Enter key working, which on a form this small
   * is how most people will use it.
   *
   * The missing-submitter branch is not paranoia so much as a refusal to guess. A form can be
   * submitted programmatically with no submitter, and the tempting fallback - "default to
   * deposit" - would move money in a direction nobody chose. There is no safe default between
   * these two, so there is no default.
   */
  const handleSubmit = async (event) => {
    event.preventDefault()

    const operation = event.nativeEvent.submitter?.value
    if (operation !== 'deposit' && operation !== 'withdraw') {
      say('Choose Deposit or Withdraw.')
      return
    }

    const value = parseAmount()
    if (value === null) {
      return
    }

    setBusy(true)

    try {
      const allowed = operation === 'deposit'
        ? await deposit(accountId, value)
        : await withdraw(accountId, value)

      if (allowed) {
        setAmount('')
        say(operation === 'deposit' ? 'Deposit accepted.' : 'Withdrawal accepted.')
        // the parent owns the balance on screen, so the parent refreshes it. The child says
        // what happened and stops - it has no idea a table exists.
        onCompleted()
      } else {
        // THE MIDDLE OUTCOME. Note what is absent: no setAmount(''), no onCompleted().
        // Nothing moved, so there is nothing to refetch, and the amount stays put to be
        // corrected rather than retyped.
        say(operation === 'deposit'
          ? 'Declined. The account would not accept that amount.'
          : 'Declined - that would take the balance past what this account allows.')
      }
    } catch (err) {
      // the status survives the trip through the api module precisely so this switch is
      // possible. Every branch here is a genuine failure, so every branch is .error.
      if (err.status === 404) {
        say('That account no longer exists.', true)
      } else if (err.status === 400) {
        say('Invalid request.', true)
      } else if (err.status === 0) {
        say(err.message, true)
      } else {
        say(`Unexpected error: ${err.status}`, true)
      }
    } finally {
      // finally, not a line per branch: the request is over however it ended, and buttons
      // left disabled by a forgotten path are a dead form.
      setBusy(false)
    }
  }

  return (
    <form className="txn-form" onSubmit={handleSubmit}>
      {/* the label is present but not shown: the column header already says what this is to
          a sighted reader, while a screen reader gets nothing useful from a header several
          rows up. Not placeholder text, which disappears the moment it is used. */}
      <label>
        <span className="visually-hidden">Amount</span>
        <input
          type="number"
          name="amount"
          value={amount}
          onChange={handleChange}
          step="1"
          min="1"
          inputMode="numeric"
          className="txn-amount"
          disabled={busy}
        />
      </label>

      {' '}
      <button type="submit" name="op" value="deposit" disabled={busy}>Deposit</button>
      {' '}
      <button type="submit" name="op" value="withdraw" disabled={busy}>Withdraw</button>

      {message && <p className={failed ? 'error' : 'message'}>{message}</p>}
    </form>
  )
}

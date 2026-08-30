import { useState } from 'react'

import { openAccountForCustomer } from '../services/api.js'

/*
 * Opens an account for a customer. An admin control, built on the same pattern as
 * CreateCustomerForm: it lives in components/ because it is a piece of a screen rather than a
 * destination, it owns its own inputs, it calls the API directly, and it announces upward
 * through onCreated rather than reaching for a list it does not own.
 *
 * It still asks for an ID, where the customer form no longer does, and that is a difference in
 * the APIs rather than an inconsistency here: POST /accounts takes a client-assigned account
 * id, while POST /customers had its id taken away when the create endpoints were merged and
 * the server started assigning them. The forms follow their endpoints.
 *
 * WHY IT ASKS WHO OWNS IT. The API offers two creates - one that makes an account belonging
 * to nobody, and one nested under a customer that also adds the id to their record. This form
 * only ever calls the second, so the owner is a required field rather than an option. An
 * ownerless account is reachable from no dashboard but the admin's, which makes it a way to
 * lose track of money rather than a feature.
 *
 * The customer <select> is fed by the list the dashboard has already loaded, so choosing an
 * owner costs no request. It is also the reason this takes `customers` as a prop rather than
 * fetching them: the parent already has them, and a component that fetched its own copy would
 * be able to disagree with the table above it.
 *
 * ==========================================================================================
 * THERE IS NO OPENING BALANCE FIELD ANY MORE, AND IT WAS REMOVED RATHER THAN REWIRED
 * ==========================================================================================
 *
 * There was one, and for a while it worked. Then the create endpoint narrowed to
 * CreateAccountRequest(id, type, overdraftLimit) and the field stopped being read - without
 * failing. Jackson ignores an unknown property, so the POST still returned 201, the account
 * still opened, and the number the admin typed went nowhere. An account created with "500" in
 * that box opened at zero and said "Account opened."
 *
 * A CONTROL THAT ACCEPTS A NUMBER AND DISCARDS IT IS WORSE THAN NO CONTROL. It is the same
 * failure this codebase has now met three times - the role selector that submitted and changed
 * nothing, the accountIds echo the server had stopped reading, and this - and the rule it keeps
 * producing is the same one: a control that appears to work while doing nothing teaches the
 * operator that the system lies, rather than that the operation is unavailable.
 *
 * THE ALTERNATIVE, AND WHY IT WAS REFUSED. The field could have been kept by opening the
 * account and then issuing a deposit for the stated amount - two requests behind one button.
 * It was rejected on two grounds, and the second is the one that decided it:
 *
 *   IT WOULD REBUILD THE CAPABILITY THE SERVER JUST REMOVED. Narrowing the request record was
 *   a decision that money enters an account through deposit and through nothing else. A client
 *   that reassembles an opening balance out of two calls is not honouring that contract, it is
 *   working around it - and the workaround lives in the one layer that cannot enforce anything.
 *
 *   IT HAS NO TRANSACTION AROUND IT. If the create succeeds and the deposit fails - a refused
 *   amount, a dropped connection, a closed tab between the two - the result is a real account,
 *   owned, at zero, that the admin believes holds 500. There is nothing here that can roll the
 *   first request back; DELETE is a second request that can fail in its own right. The honest
 *   version of that feature has to report "the account exists but the money did not arrive",
 *   which is a worse sentence than the one the user reads now, and it is a state somebody then
 *   has to reconcile by hand. A compound operation belongs on the server, where it can be one.
 *
 * WHAT IT COSTS THE ADMIN, stated plainly because it is a real cost and not nothing: opening a
 * funded account is now two steps in two places. Open it here, then follow the owner's name in
 * the accounts table to their dashboard and deposit. That path already exists and already works
 * - it is the same deposit control the customer uses - so what was lost is a shortcut, not a
 * capability.
 *
 * AND IT IS THE ONLY PATH, which is why that sentence is load-bearing rather than reassuring.
 * Nothing writes a balance directly - not this create, and not PUT /accounts/{id}, which binds
 * UpdateAccountRequest(type, overdraftLimit). The admin dashboard deliberately carries no
 * deposit control of its own. So an account opened here is funded by exactly one route, and if
 * that route is ever closed off, every account this form creates is stranded at zero with
 * nothing here reporting a problem - this form would go on saying "Account opened", perfectly
 * correctly. DashboardPage holds the full note, beside the line that keeps the route open.
 */
export default function AddAccountForm({ customers, onCreated }) {

  // one object rather than four useState calls, so the shape is close to the body being sent
  // and there is no assembly step. Empty strings throughout, not undefined: React treats a
  // controlled input whose value is undefined as UNcontrolled and warns when it changes.
  //
  // `type` starts at savings rather than empty, because a select with no valid choice is a
  // trap - it looks answered and is not. Every other field is genuinely blank.
  const [form, setForm] = useState({
    id: '',
    customerId: '',
    type: 'savings',
    overdraftLimit: '',
  })

  const [message, setMessage] = useState(null)
  const [failed, setFailed] = useState(false)
  const [busy, setBusy] = useState(false)

  const say = (text, isFailure = false) => {
    setMessage(text)
    setFailed(isFailure)
  }

  // one handler for every field: each input's name= matches its key in the form object, so
  // event.target.name says which to update. [name] is a computed key - the key is the value
  // of the variable, not the literal string "name".
  const handleChange = (event) => {
    const { name, value } = event.target
    setForm(prev => ({ ...prev, [name]: value }))
    setMessage(null)
  }

  // savings has no overdraft, and the model enforces that regardless of what is sent - the
  // constructor forces the limit to zero for savings, because "savings has no overdraft" is
  // a fact about savings rather than a per-account choice. Disabling the input rather than
  // hiding it says so: a field that vanishes looks like a bug, one that greys out looks like
  // a rule.
  const savings = form.type === 'savings'

  const handleSubmit = async (event) => {
    event.preventDefault()

    if (form.customerId === '') {
      say('Choose which customer this account belongs to.')
      return
    }

    setBusy(true)

    try {
      // the limit is sent as typed and negated in the api module. Savings sends 0 because the
      // input is disabled and empty - Number('') is 0 - which is also what the model would
      // force it to, so the two agree without this form having to know the rule.
      await openAccountForCustomer(form.customerId, {
        id: form.id,
        type: form.type,
        overdraftLimit: savings || form.overdraftLimit === '' ? 0 : form.overdraftLimit,
      })

      setForm(prev => ({ ...prev, id: '', overdraftLimit: '' }))
      // the customer is deliberately NOT reset. Opening two accounts for the same person is
      // the common case, and clearing the field they would only have to set again is the kind
      // of tidiness that costs the user work.
      //
      // the message says the balance is zero rather than leaving the admin to read it off the
      // table, because "opened" alone is what the old silently-discarded field left them
      // believing. It names where money goes in, since that is now somewhere else entirely.
      say('Account opened with a zero balance. Deposit from the owner\'s dashboard.')
      if (onCreated) onCreated()
    } catch (err) {
      // the API distinguishes four outcomes here and the message follows it exactly. The
      // status survives the trip through the api module precisely so this switch is possible.
      if (err.status === 409) {
        say('That account id is already taken.')
      } else if (err.status === 404) {
        // established before the conflict check on the server: a create against a missing
        // customer is not a conflict, it is a create against nothing.
        say('That customer no longer exists.')
      } else if (err.status === 400) {
        // says no more than that, deliberately - the server does not name the offending
        // field, so the client cannot either.
        say('Invalid request. An account id must be a positive whole number.')
      } else if (err.status === 0) {
        say(err.message, true)
      } else {
        say(`Unexpected error: ${err.status}`, true)
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <h3>Open an account</h3>

      <label>
        Account id:
        <input type="number" name="id" value={form.id} onChange={handleChange} disabled={busy} />
      </label>

      <label>
        Owner:
        {/* the empty option is not padding. A select with a pre-selected first customer would
            open an account for whoever happens to sort first every time the admin forgets to
            choose, and that failure is silent. An unselectable placeholder makes the omission
            visible and the check above catches it. */}
        <select name="customerId" value={form.customerId} onChange={handleChange} disabled={busy}>
          <option value="">Choose a customer</option>
          {customers.map(customer => (
            <option key={customer.id} value={customer.id}>
              {customer.fullName} (#{customer.id})
            </option>
          ))}
        </select>
      </label>

      <label>
        Type:
        <select name="type" value={form.type} onChange={handleChange} disabled={busy}>
          <option value="savings">Savings</option>
          <option value="checking">Checking</option>
        </select>
      </label>

      {/* no opening balance input. It is gone rather than disabled, and the difference is the
          same one EditCustomerPage draws about the role: a disabled control says "not available
          to you", which is a claim about permission, where the truth is that no endpoint accepts
          an opening balance from anybody. A sentence says that; a greyed-out box implies
          somebody else could type in it. */}
      <p className="muted">
        Accounts open at a zero balance. Money goes in through a deposit on the owner&rsquo;s
        dashboard.
      </p>

      <label>
        Overdraft limit:
        {/* collected as a MAGNITUDE - "how far below zero may this account go" is a question
            a person can answer, where "what is the floor" invites a minus sign that half of
            them will forget. The api module negates it on the way out and takes the absolute
            value on the way in, so this input and the stored field never have to agree about
            a sign. */}
        <input
          type="number"
          name="overdraftLimit"
          value={savings ? '' : form.overdraftLimit}
          onChange={handleChange}
          step="1"
          min="0"
          disabled={busy || savings}
        />
        {savings && <span className="muted"> Savings accounts have no overdraft.</span>}
      </label>

      <button type="submit" disabled={busy}>Open account</button>

      {message && <p className={failed ? 'error' : 'message'}>{message}</p>}
    </form>
  )
}

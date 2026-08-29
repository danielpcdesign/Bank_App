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
 */
export default function AddAccountForm({ customers, onCreated }) {

  // one object rather than five useState calls, so the shape is close to the body being sent
  // and there is no assembly step. Empty strings throughout, not undefined: React treats a
  // controlled input whose value is undefined as UNcontrolled and warns when it changes.
  //
  // `type` starts at savings rather than empty, because a select with no valid choice is a
  // trap - it looks answered and is not. Every other field is genuinely blank.
  const [form, setForm] = useState({
    id: '',
    customerId: '',
    type: 'savings',
    balance: '',
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
        balance: form.balance === '' ? 0 : form.balance,
        overdraftLimit: savings || form.overdraftLimit === '' ? 0 : form.overdraftLimit,
      })

      setForm(prev => ({ ...prev, id: '', balance: '', overdraftLimit: '' }))
      // the customer is deliberately NOT reset. Opening two accounts for the same person is
      // the common case, and clearing the field they would only have to set again is the kind
      // of tidiness that costs the user work.
      say('Account opened.')
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

      <label>
        Opening balance:
        <input
          type="number"
          name="balance"
          value={form.balance}
          onChange={handleChange}
          step="1"
          disabled={busy}
        />
      </label>

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

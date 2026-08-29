import { useEffect, useState } from 'react'

import CreateCustomerForm from '../components/CreateCustomerForm.jsx'
import CustomerList from '../components/CustomerList.jsx'
import { deleteCustomer, getCustomers } from '../services/api.js'

/*
 * The customers screen. This is the old CustomerList with its two jobs separated.
 *
 * It used to both own the data AND draw the table. Those are different responsibilities and
 * they were only together because the file started small. Now:
 *
 *   CustomersPage (here)  - owns state, calls the API, decides what a failure means
 *   CustomerList          - given an array, draws a table. No state, no fetching.
 *
 * The names for these are CONTAINER and PRESENTATIONAL. The test for which one you are
 * holding: could you render it in isolation with made-up props and see the right thing?
 * CustomerList, yes. This page, no - it would go and fetch.
 *
 * The payoff is not abstraction for its own sake. It is that the table is now reusable on
 * any screen that has customers to show, and that everything with a decision in it lives in
 * one file, so there is exactly one place to look when the list is wrong.
 *
 * It is in pages/ rather than components/ because a route renders it. That is the whole
 * rule: pages are destinations, components are what destinations are built from.
 */
export default function CustomersPage() {

  // empty array, not null - the first render happens before any data arrives, and the table
  // would crash mapping over null
  const [customers, setCustomers] = useState([])

  // failure is stored explicitly. a UI showing nothing when the API is down looks identical
  // to one showing an empty database
  const [error, setError] = useState(null)

  // and so does a UI that has not finished asking yet. three states - loading, failed,
  // loaded - need three answers, not two
  const [loading, setLoading] = useState(true)

  const loadCustomers = async () => {
    try {
      setCustomers(await getCustomers())
      // a successful load clears any previous failure. without this a transient error stays
      // on screen forever, contradicting the fresh data underneath it
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      // finally, not a line in each branch: the request is over either way, and the one
      // thing worse than an error message is a spinner that never stops
      setLoading(false)
    }
  }

  useEffect(() => {
    // the effect callback itself is NOT async, deliberately. An async function returns a
    // promise, and React reads an effect's return value as a CLEANUP FUNCTION - it would
    // try to call the promise. Declaring the async work separately and calling it here is
    // the standard way around that.
    //
    // The suppression below is a judgement, not a shrug. set-state-in-effect exists to catch
    // a genuine bug: setting state synchronously inside an effect renders, which runs the
    // effect, which sets state - a loop, or at best a wasted second render for a value that
    // could have been computed during the first. That is not what happens here. An async
    // function runs synchronously only up to its first await, and loadCustomers awaits
    // before it touches state, so every setState lands in a later tick. The rule cannot see
    // past the call boundary into the suspension point.
    //
    // Suppressing rather than leaving it: a warning that is always present is a warning
    // nobody reads, and it would hide the next one - which might be real.
    // eslint-disable-next-line react/set-state-in-effect
    loadCustomers()

    // [] means "depends on nothing" - runs once after mount. loadCustomers is deliberately
    // not listed: a function declared in a component body is a new object every render, so
    // React would see the dependency change every time and re-run the effect forever.
    // Two requests still appear in the dev network tab: <StrictMode> remounts every
    // component to surface effects that are not safe to run twice. Not present in a build.
  }, [])

  const handleDelete = async (id) => {
    try {
      await deleteCustomer(id)
    } catch (err) {
      // 404 means someone else already deleted it, or this tab's list is stale. The outcome
      // the user asked for is already true, so it is not worth an error message - but the
      // list on screen is provably wrong, which is exactly why the refetch below still runs.
      if (err.status !== 404) {
        setError(err.message)
        return
      }
    }
    // refetch rather than removing the row locally. Costs a round trip; leaves the screen
    // agreeing with the database. The optimistic alternative is faster and assumes the
    // delete succeeded - a fine trade on a slow network, a bad one on a bank.
    loadCustomers()
  }

  return (
    <section>
      <h1>Customers</h1>

      {/* && returns its left side when falsy, and React renders null/false as nothing */}
      {error && <p className="error">{error}</p>}

      {/* the page owns the data, so the page owns the refresh. the form is handed the
          ability to trigger one without being told what it does, and without being able to
          reach `customers` itself. the child announces; the parent decides. */}
      <CreateCustomerForm heading="Add customer" onCreated={loadCustomers} />

      {loading
        ? <p>Loading...</p>
        : <CustomerList customers={customers} onDelete={handleDelete} />}
    </section>
  )
}

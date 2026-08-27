import { useEffect, useState } from 'react'

import AddCustomerForm from './AddCustomerForm.jsx'
import CustomerRow from './CustomerRow.jsx'

/*
 * Fetches the customer list from the API and renders it.
 *
 * A component is a function React calls again on every render, so a plain const inside it
 * cannot hold a fetch result - the next call rebuilds it. State is the storage React owns
 * and hands back, which is why useState exists rather than an assignment.
 *
 * Rendering must stay pure: React may call this function speculatively, or twice. A network
 * request is a side effect, so it lives in useEffect, which runs after the render commits.
 */
export default function CustomerList() {

  // empty array, not null - the first render happens before any data arrives, and the map
  // below would crash on null
  const [customers, setCustomers] = useState([]);

  // failure is stored explicitly. a UI showing nothing when the API is down looks identical
  // to one showing an empty database
  const [error, setError] = useState(null);

  // declared in the component body rather than inside the effect, so it has two callers: the
  // mount effect below, and the form's onCreated. the function does not know or care which
  // one invoked it - it loads, and that is all.
  const loadCustomers = () =>
    {
    // relative path on purpose - it resolves against whatever origin served the page, which
    // is Vite in dev and the real host in production. localhost:8080 would work today and
    // break on deploy.
    fetch('/api/v1/customers')
      .then(response =>
        {
        // fetch does NOT reject on 404 or 500 - it only rejects when the request never
        // completed. throwing here turns a fulfilled-but-failed response into a rejection,
        // so the single catch below handles both HTTP errors and network failures.
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        return response.json();
        })
      .then(data =>
        {
        setCustomers(data);
        // a successful load clears any previous failure. without this a transient error
        // stays on screen forever, contradicting the fresh data underneath it.
        setError(null);
        })
      .catch(err => setError(err.message));
    };

    const handleDelete = (id) => {
      fetch(`/api/v1/customers/${id}`, { method: 'DELETE' })
        .then(response => {
          if (response.status === 204) {
            loadCustomers();
          } else if (response.status === 404) {
            // already gone, refetch to sync
            loadCustomers();
          } else {
            throw new Error(`HTTP error! status: ${response.status}`);
          }
        })
        .catch(err => setError(err.message));
    };
  // TODO A - handleDelete(id).
  //   Lives here, not in the row, for the same reason loadCustomers does: this component owns
  //   the data, so it owns every change to it.
  //
  //   DELETE /api/v1/customers/{id}, then decide what each status means:
  //     204 -> gone. call loadCustomers() to refetch.
  //     404 -> already gone. someone else deleted it, or this tab's list is stale. worth
  //            thinking about before you write it: is that an error to show the user, or is
  //            the outcome they wanted already true? your answer decides the code.
  //
  //   Two design calls that are yours to make, not defaults to accept:
  //
  //   1. Confirm first? This is the app's first destructive action and there is no undo.
  //      window.confirm() is ugly and blocking, and it is also honest about the cost.
  //
  //   2. Refetch, or remove the row locally? Refetching costs a round trip but leaves the
  //      screen agreeing with the database. Removing it locally is instant and assumes the
  //      delete succeeded - "optimistic" updating. Both are legitimate; pick one on purpose.

  useEffect(() =>
    {
    loadCustomers();
    // [] means "depends on nothing" - runs once after mount. loadCustomers deliberately does
    // NOT go in this array: a function declared in a component body is a new object on every
    // render, so React would see the dependency change every time and re-run the effect
    // forever. useCallback is the tool that would make it safe to list; it buys nothing while
    // the array stays empty.
    // two requests appear in the dev network tab regardless: <StrictMode> remounts every
    // component to surface effects that are not safe to run twice. not present in a build.
  }, [])

  return (
    <>
      {/* && returns its left side when falsy, and React renders null/false as nothing */}
      {error && <p className="error">{error}</p>}

      {/* the list owns the data, so the list owns the refresh. the form is handed the ability
          to trigger one without being told what it does, and without being able to reach
          `customers` itself. the child announces; the parent decides. */}
      <AddCustomerForm onCreated={loadCustomers} />

      <table>
        <thead>
          <tr>
            <th>Id</th>
            <th>Username</th>
            <th>Full name</th>
            <th>Actions</th>
            {/* TODO B - a fourth <th> for the delete column. An empty <th> is fine; the
                column has no name because its contents are self-describing. */}
          </tr>
        </thead>
        <tbody>
          {/* key is how React matches elements between renders to decide what to reuse.
              without a stable one it falls back to array position, and rows edited or
              deleted later update the wrong row. id is a genuine identity - the API
              guarantees it is unique. */}
          {/* TODO C - pass handleDelete down as onDelete.
              Write it the obvious way first: onDelete={handleDelete}, or an inline arrow if
              you find you need one. Then open React DevTools' Profiler, record a keystroke
              in the form, and look at whether the rows re-rendered. The answer differs
              depending on which of those two you wrote, and the difference is the point. */}
          {customers.map(customer => (
            <CustomerRow key={customer.id} customer={customer} onDelete={handleDelete} />
          ))}
        </tbody>
      </table>
    </>
  )
}

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
          </tr>
        </thead>
        <tbody>
          {/* key is how React matches elements between renders to decide what to reuse.
              without a stable one it falls back to array position, and rows edited or
              deleted later update the wrong row. id is a genuine identity - the API
              guarantees it is unique. */}
          {customers.map(customer => (
            <CustomerRow key={customer.id} customer={customer} />
          ))}
        </tbody>
      </table>
    </>
  )
}

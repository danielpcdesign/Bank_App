import { memo } from 'react'

/*
 * One row of the customer table.
 *
 * It receives a customer and renders it. That is the whole contract - no state, no fetching,
 * no knowledge of where the data came from or how many siblings it has. A component that
 * only turns props into markup is called presentational, and it is the cheapest kind to
 * reason about: same props in, same markup out, every time.
 *
 * Props are read-only. React hands them down and the child does not write back; when a child
 * needs to cause a change it calls a function the parent passed it. That is the same
 * one-directional discipline as the API layers - the row reports, the list decides.
 */
function CustomerRow({ customer }) {
  return (
    <tr>
      <td>{customer.id}</td>
      <td>{customer.username}</td>
      <td>{customer.fullName}</td>
    </tr>
  );
}

/*
 * memo caches the last render and reuses it when the props are unchanged, so a parent
 * re-render does not automatically become a child re-render.
 *
 * "Unchanged" means SHALLOW equality - each prop compared with Object.is, one level deep.
 * That is the whole behaviour, and it is what decides when this pays and when it does not:
 *
 *   Skips the re-render when CustomerList re-renders for a reason unrelated to the data -
 *   setError firing, or any state added to that component later. The customers array and
 *   every object in it are still the same references, so every row is skipped.
 *
 *   Does NOT skip after a refetch. response.json() builds brand new objects, so
 *   customer !== customer even when every field is identical. Shallow equality fails, every
 *   row renders. memo cannot see that the data is the same; it only sees two references.
 *
 * Worth being straight about the size of the win here: three rows of three <td>s. This is a
 * demonstration of the mechanism, not a measured optimisation, and memo is not free - it
 * costs a comparison per prop per render plus the retained previous result. The honest rule
 * is to reach for it when a profiler shows a component re-rendering expensively, not by
 * default. It starts genuinely paying at hundreds of rows, or when a row grows real work.
 *
 * The function is named rather than inlined so React DevTools shows CustomerRow instead of
 * an anonymous memo wrapper.
 */
export default memo(CustomerRow);

/*
 * The API client. Every HTTP call the front end makes lives here and nowhere else.
 *
 * WHY THIS FILE EXISTS
 *
 * Until now fetch() calls were spread across three components, each repeating the base path,
 * the Content-Type header, and the !response.ok check. That is the same duplication the
 * server side already solved by layering: the controller does not talk to Mongo, it asks the
 * service, which asks the repository. This module is the front end's repository layer - the
 * only code in the app that knows the API is reached over HTTP at /api/v1.
 *
 * What it buys, concretely:
 *   - The base path is written once. When the API moves to /api/v2, one line changes.
 *   - Components stop containing transport code. CustomersPage says getCustomers(); it does
 *     not know whether that is fetch, axios, or a WebSocket.
 *   - The rules that are easy to forget - fetch not rejecting on 500, the Content-Type
 *     header Spring needs to avoid a 415 - are enforced in one place rather than remembered
 *     at each call site.
 *
 * DIVERGENCE FROM THE COURSE REPO, on purpose: chapter 07 uses axios here. We stay on fetch.
 * axios would add a dependency to buy two things that are four lines below - automatic JSON
 * parsing and rejection on 4xx/5xx. The structure is what we are borrowing, not the library.
 * And swapping in axios later would change this file and no other, which is itself the
 * argument for the file existing.
 */

// Relative, for the same reason every fetch in this app was already relative: it resolves
// against whatever origin served the page - Vite in dev, nginx in compose, CloudFront in
// production. A hardcoded http://localhost:8080 works today and breaks on deploy.
const BASE_URL = '/api/v1/customers'

/*
 * An error that remembers its status code.
 *
 * The temptation is to have this module translate failures into friendly messages. It must
 * not. 409 and 400 mean different things to a user - "that ID is taken" versus "that data is
 * invalid" - and only the CALLER knows which of those matter in its context. A DELETE that
 * 404s is a genuine failure on one screen and a shrug on another.
 *
 * So the split is: this module normalises TRANSPORT, the caller interprets MEANING. Same
 * division as the API's service returning a boolean and letting the controller pick the
 * status code.
 */
export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

/*
 * One request. Everything below is a thin name over this.
 *
 * async/await rather than .then() chains. It is the same promise machinery - await just
 * unwraps a promise and resumes the function when it settles - but it lets the two failure
 * modes be told apart, which a single .catch() at the end of a chain cannot do:
 *
 *   the try/catch around fetch  -> the request never completed. DNS, offline, CORS refusal.
 *   the !response.ok check      -> the request completed fine and the server said no.
 *
 * That distinction was already in the code as a comment; here it is in the control flow.
 */
async function request(path, options) {
  let response

  try {
    response = await fetch(BASE_URL + path, options)
  } catch (cause) {
    // status 0 is not an HTTP code - there was no response to take a code from. It is a
    // deliberate sentinel meaning "never reached the server", so callers switching on
    // status have a value to match rather than having to check for a missing field.
    throw new ApiError(0, `Network error: ${cause.message}`)
  }

  if (!response.ok) {
    throw new ApiError(response.status, `HTTP error! status: ${response.status}`)
  }

  // 204 No Content has an empty body, and response.json() on an empty body throws a parse
  // error - a confusing way to report a request that in fact succeeded. DELETE returns 204.
  if (response.status === 204) {
    return null
  }

  return response.json()
}

// The header and the serialisation, in one place. Forgetting Content-Type is a 415 from
// Spring, and it is exactly the kind of thing that gets remembered on the first call site
// and forgotten on the fourth.
const jsonRequest = (method, data) => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(data),
})

/*
 * The public surface. One function per API operation, named for what it does rather than for
 * the verb it uses - readers of CustomersPage care that a customer is being created, not
 * that a POST is involved.
 *
 * This list is also documentation: it is the complete set of things this front end can ask
 * the back end to do. When Phase 13 adds filter/search, the gap shows up here first.
 */
export const getCustomers = () => request('')
export const getCustomer = (id) => request(`/${id}`)
export const createCustomer = (customer) => request('', jsonRequest('POST', customer))
export const updateCustomer = (id, customer) => request(`/${id}`, jsonRequest('PUT', customer))
export const deleteCustomer = (id) => request(`/${id}`, { method: 'DELETE' })

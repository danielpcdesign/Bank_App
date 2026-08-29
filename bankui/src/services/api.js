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
//
// It is the VERSION root now, not the customers collection - it was /api/v1/customers while
// customers were the only resource, and accounts made that name a lie. Each function below
// spells out its own collection, which is a line longer and stops the base from having to be
// re-decided every time a second resource appears.
const BASE_URL = '/api/v1'

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
/* ==========================================================================================
 * ROLES
 *
 * A customer is an ADMIN or a CUSTOMER, and the two see different dashboards. The enum is
 * Role { ADMIN, CUSTOMER } and the field on Customer is `role`, spelled here to match the
 * server byte for byte - read from the model rather than guessed, because a case mismatch on
 * an enum surfaces as an unexplained 400 rather than as anything that names the field.
 *
 * The field is @NotNull, so it is not optional on the way out: a POST or PUT of a customer
 * without one is rejected. That is why the two customer functions below map their argument
 * rather than passing it through, which they used to do.
 *
 * WHAT THIS IS NOT. It is not a permission, and nothing built on it is a security boundary.
 * There is no authentication in this application: no login, no session, no token, no
 * principal. The API serves every endpoint, including every DELETE, to anyone who asks. Role
 * is a fact ABOUT a customer record, exactly like fullName - it says what kind of customer
 * this is, and the UI uses it to decide which screen is useful. It decides nothing about what
 * is allowed, because the front end is not in a position to decide that and cannot be: it
 * runs on hardware the user controls, and curl does not run this code. Role.java on the
 * server makes the same argument, unprompted and at greater length; LoginPage.jsx makes it
 * for the client. Both are worth reading before extending any of this.
 *
 * TWO VOCABULARIES, and the translation between them lives here and nowhere else. The wire
 * speaks in enum constants - "ADMIN" - and the UI speaks in words - "admin" - for the same
 * reason AccountType does: a constant is correct in a payload and shouty in a sentence.
 * Everything above the api module uses the lowercase form.
 */

// the roles this UI knows about, in the order a form should offer them. Least surprising
// first, which is also the one a new customer should get by default.
export const ROLES = ['customer', 'admin']

/*
 * The role, lowercased, or the EMPTY STRING when the record does not have one.
 *
 * The empty string is the important case and it is not hypothetical: customers written before
 * the field existed come back with role: null, and there are some in the live database right
 * now. Three things had to be true of how that is represented, and '' is what satisfies all
 * three:
 *
 *   It is not 'customer'. Defaulting an absent role to the ordinary one would make a record
 *   with no role indistinguishable from one deliberately marked CUSTOMER - and an edit form
 *   built on that would save a role the user never chose, quietly, on the first save.
 *
 *   It is falsy, so isAdmin() is false and a missing or renamed field degrades to the view
 *   with FEWER controls on it rather than more. Not a security property - there is nothing to
 *   secure - just the habit of being wrong in the harmless direction.
 *
 *   It is a string, so a <select> bound to it stays a CONTROLLED input. Bound to null, React
 *   treats the select as uncontrolled and then warns when a real value arrives and it becomes
 *   controlled - the hazard EditCustomerPage already documents for its text fields.
 */
export const roleOf = (customer) =>
  (customer?.role == null ? '' : String(customer.role).toLowerCase())

export const isAdmin = (customer) => roleOf(customer) === 'admin'

// the ids of the accounts this customer owns. A function for the same reason roleOf is one:
// `accountIds` is the model's name for it, the empty case arrives as either [] or a missing
// field depending on when the document was written, and neither is a component's problem.
//
// READ ONLY. The admin dashboard uses this to work out whose account is whose. Nothing may
// feed it back into a write: the server owns that list and takes it from the stored document,
// so a PUT carrying one would be either ignored or, if the rule ever changed, wrong.
export const accountIdsOf = (customer) => customer?.accountIds ?? []

/*
 * A customer on its way OUT: the fields a PUT replaces, and nothing else.
 *
 * The role is uppercased into the enum constant here - the mirror of roleOf, and the reason a
 * form can hold 'admin' in its state and still produce a body Jackson will bind. An empty role
 * is passed through as an empty string rather than quietly substituted: the server rejecting
 * it is the correct outcome, and the forms catch it first so the user sees which field rather
 * than a bare 400.
 *
 * ==========================================================================================
 * WHY THIS NAMES ITS FIELDS INSTEAD OF SPREADING THE CUSTOMER
 * ==========================================================================================
 *
 * It used to be { ...customer, role }, which sent back whatever happened to be on the object.
 * It now lists four fields, because the server has a rule about which ones a PUT replaces and
 * the rule is worth encoding rather than approximating:
 *
 *   PUT FULLY REPLACES THE FIELDS A CLIENT BOTH READS AND OWNS.
 *
 * That is a rule about PUT, not about writing in general - toNewCustomer below is the POST
 * body, and it is a different shape entirely: three fields, no id and no role, because a
 * create is the server establishing the record rather than a client replacing one.
 *
 * Run the two halves over every field on a customer and the whole contract falls out:
 *
 *   id, username, fullName - read and owned. Replaced. They are here.
 *   role                   - read and owned. Replaced, which is why changing a role is a PUT
 *                            like any other edit and no PATCH endpoint was added for it.
 *   password               - FAILS THE READ HALF. It is WRITE_ONLY, so no client has ever
 *                            seen one and none can send one back. The server preserves the
 *                            stored value when the body omits it, so omitting it is now
 *                            CORRECT rather than merely unavoidable.
 *   accountIds             - FAILS THE OWN HALF. Accounts are linked and unlinked by the
 *                            account endpoints as they are opened and closed; a
 *                            customer-shaped PUT has no idea what it would be overwriting.
 *                            The server takes this list from the stored document and ignores
 *                            the body.
 *
 * THE ECHO IS GONE. This function used to forward accountIds untouched, which was the safer
 * of the two options while PUT still replaced the field - omitting it would have nulled the
 * list, while echoing it could only lose an update in a race. The server now ignores the body
 * for that field, so the race cannot unlink anything and the echo buys nothing at all.
 *
 * It is removed rather than left as a harmless extra, and that is the point worth keeping: a
 * field in a request body is a claim that the field matters. The next reader would have to
 * work out that it does not, and somebody would eventually defend it. Absence, with this
 * comment, says the server owns the list - which is the fact, and it is not something a spread
 * could have said.
 *
 * The cost of naming fields is that a NEW client-owned field is silently omitted until it is
 * added here. That is the right way round for a full replacement: an omitted field leaves the
 * stored value alone, while an unexpected extra one overwrites something.
 */
const toWireCustomer = (customer) => ({
  id: customer.id,
  username: customer.username,
  fullName: customer.fullName,
  role: String(customer.role ?? '').toUpperCase(),
})

/*
 * A customer on its way out as a CREATE: three fields, and only three.
 *
 * POST /api/v1/customers takes username, password and fullName. The server assigns the id and
 * forces the role to CUSTOMER. There is no id field and no role field to send.
 *
 * THIS USED TO BE FOUR FIELDS PLUS A PASSWORD, and there used to be a second create endpoint
 * beside it - /customers/register - for self-service sign-up. They have been merged: one
 * endpoint, one body, one function. Registration and administrative creation were already
 * making the identical request in everything but name.
 *
 * WHAT THE MERGE RETIRED, and it is worth recording because this module argued the opposite
 * case at some length and was right to at the time. The admin-side create form used to offer
 * a role selector; the admin option was removed from it, and the comment there said plainly
 * that the removal PREVENTED NOTHING - the endpoint behind it accepted role: "ADMIN" from any
 * caller with curl, and a control that is merely hidden is not a restriction.
 *
 * That argument is now obsolete rather than wrong. The endpoint no longer reads a role from
 * the body, from anybody. The capability is not hidden, it is gone from the create path, and
 * it is gone where such things have to be: on the server.
 *
 * BUT ONLY FROM THE CREATE PATH, and this limit must not be lost. PUT /api/v1/customers/{id}
 * still accepts a role from any caller with no credential - that is the deliberate UI-only
 * gating decision recorded on setCustomerRole, unchanged. So anybody may register an ordinary
 * account and then promote it to ADMIN with a single PUT.
 *
 *   "Registration cannot mint an administrator" is TRUE.
 *   "The API cannot mint an administrator" is FALSE.
 *
 * Nothing in this module or in any UI copy above it may imply the second.
 *
 * NAMED, NOT SPREAD, for the reason toWireCustomer learned the hard way. A spread sends
 * whatever the caller's object happens to be carrying, and here that would be actively wrong:
 * an `id` or a `role` in this body is at best ignored and at worst something the server has to
 * decide how to refuse. Sending precisely what the endpoint accepts means the request cannot
 * make a claim the API never invited - and it means the same function serves the register page
 * and the admin form without either being able to smuggle a field past the other.
 *
 * accountIds is absent for a simpler reason than on PUT: a new customer has no accounts. The
 * server initialises the list.
 */
const toNewCustomer = (details) => ({
  username: details.username,
  password: details.password,
  fullName: details.fullName,
})

export const getCustomers = () => request('/customers')
export const getCustomer = (id) => request(`/customers/${id}`)
// the only create. Self-service registration and an administrator adding a customer are the
// same request, so they are the same function - two exported names for one identical POST
// would be the same duplication problem one level down.
export const createCustomer = (details) => request('/customers', jsonRequest('POST', toNewCustomer(details)))
export const updateCustomer = (id, customer) => request(`/customers/${id}`, jsonRequest('PUT', toWireCustomer(customer)))
export const deleteCustomer = (id) => request(`/customers/${id}`, { method: 'DELETE' })

/* ==========================================================================================
 * ACCOUNTS
 *
 * Written while the accounts API was still being built, then RECONCILED against the finished
 * AccountController and Account model rather than left as guesswork. Everything the front end
 * assumes about that API is in this section and nowhere else, which is the whole point: the
 * one place it had to change when the real contract turned out to differ was here, and no
 * component moved.
 *
 * The contract, as the controller actually implements it:
 *
 *   GET  /api/v1/accounts                       -> [Account]
 *   GET  /api/v1/accounts/{id}                  -> Account, or 404
 *   GET  /api/v1/customers/{id}/accounts        -> [Account], or 404 if no such CUSTOMER.
 *                                                  A customer with no accounts is 200 and [].
 *   POST /api/v1/accounts/{id}/deposit?amount=N -> 200 with the updated Account
 *   POST /api/v1/accounts/{id}/withdraw?amount=N
 *
 * WHERE THE FIRST DRAFT WAS WRONG, worth recording because it is the argument for this file:
 * both operations were written to send { "amount": 50 } as a JSON body. The controller binds
 * @RequestParam, so the amount goes in the QUERY STRING and a JSON body is ignored - every
 * transaction would have failed validation. That was a four-line correction in one function.
 * Had the fetch lived in TransactionForm, it would have been the same correction in the same
 * number of places, but found later and in a file about buttons.
 *
 * The CRUD half of the accounts API - create, replace, delete, and the customer-scoped
 * open/close - is deliberately absent. No screen offers those yet, and a function nothing
 * calls is dead code that still has to be kept true. It goes in when a screen needs it.
 * ========================================================================================== */

/*
 * The shape the front end works in.
 *
 * A DIVERGENCE from the customer functions above, which hand back response.json() untouched.
 * Worth being explicit about why the two differ: field names that arrive raw are field names
 * every component then depends on, and a rename becomes a hunt. Mapping once, here, is what
 * keeps that a one-line change. The fields happen to line up today - id, type, balance,
 * overdraftLimit are exactly what the model serialises - so this looks like a copy; it is a
 * seam, and it is cheap.
 *
 * TWO REAL TRANSFORMS, though, both of which would otherwise have to be repeated wherever an
 * account is rendered:
 *
 * OVERDRAFT LIMIT. The model stores it as a FLOOR - "the balance may not cross this" - and it
 * is therefore zero or NEGATIVE: checking sits at something like -100, savings is forced to 0
 * because "savings has no overdraft" is a fact about savings rather than a per-account
 * choice. But "overdraft limit" reads to a user as a MAGNITUDE: how far below zero they may
 * go, 100. Math.abs converts the model's word into the reader's, once. It also means this
 * still renders correctly if the server ever flips the sign convention.
 *
 * TYPE. An enum over the wire, so it arrives as "SAVINGS" - correct as a constant and shouty
 * as a label. Lowercased here so the presentational layer can capitalise it the way it wants
 * a word rather than special-casing an enum.
 */
const toAccount = (raw) => ({
  id: raw.id,
  type: String(raw.type ?? '').toLowerCase(),
  // Number(), because "did this arrive as a JSON number or a string" is a transport question
  // and this is the transport layer. A component should never have to ask.
  balance: Number(raw.balance),
  overdraftLimit: Math.abs(Number(raw.overdraftLimit ?? 0)),
})

// defensive: an endpoint meant to return a list that returns null would otherwise crash
// .map() inside a component. Cheap to absorb here, impossible to absorb there.
const toAccounts = (raw) => (Array.isArray(raw) ? raw.map(toAccount) : [])

export const getAccounts = () => request('/accounts').then(toAccounts)
export const getAccount = (id) => request(`/accounts/${id}`).then(toAccount)
export const getCustomerAccounts = (id) => request(`/customers/${id}/accounts`).then(toAccounts)

/*
 * DEPOSIT AND WITHDRAW, and the third outcome.
 *
 * These resolve to a BOOLEAN, and it is the same boolean Account.withdraw() returns in the
 * Java: "did the account's rules allow it", not "did it work". A savings account refusing to
 * go below zero is not a failure - the request was valid, the server understood it perfectly,
 * and the answer is no. AGENTS.md is explicit that a refusal is an in-contract outcome, and
 * the UI has to be able to say so without it looking like a crash.
 *
 * Three outcomes, then, carried three different ways:
 *
 *   resolves true   - 200. The rules allowed it and the balance changed.
 *   resolves false  - 409. The rules refused. Information, not an error.
 *   throws ApiError - 400 (an amount no state would ever accept), 404 (no such account),
 *                     5xx, or status 0 for a request that never arrived.
 *
 * The 409 is the interesting one, and the controller picks it for a reason worth restating:
 * a refusal is STATE-DEPENDENT - the identical request succeeds once the balance allows it -
 * whereas a fractional or negative amount is 400 because no state would ever accept it. That
 * split is exactly the one the UI needs, so it is the split this function preserves.
 *
 * Note what this does NOT do: it does not decide what a refusal MEANS or how to phrase it. It
 * turns one status into one unambiguous value and stops. Same division ApiError was built
 * for - this module normalises transport, the caller interprets meaning. Downgrading the 409
 * from a throw to a return value is part of that: a normal answer should not arrive as an
 * exception, and a caller that has to catch to find out it succeeded-but-was-declined will
 * eventually catch it alongside a network failure and treat the two alike.
 */
// named, because `409` at the point of use says what was sent and not what it meant. The
// controller returns it for "the account's rules said no" and for nothing else on these two
// routes.
const REFUSED = 409

async function transact(accountId, operation, amount) {
  try {
    // the amount is a QUERY PARAMETER, not a body - @RequestParam, not @RequestBody. Hence no
    // jsonRequest() here: there is nothing to serialise and no Content-Type to get right.
    // encodeURIComponent on a number is belt and braces, and the habit is worth more than the
    // characters it saves.
    await request(
      `/accounts/${accountId}/${operation}?amount=${encodeURIComponent(amount)}`,
      { method: 'POST' },
    )
    return true
  } catch (err) {
    if (err instanceof ApiError && err.status === REFUSED) {
      return false
    }
    throw err
  }
}

export const deposit = (accountId, amount) => transact(accountId, 'deposit', amount)
export const withdraw = (accountId, amount) => transact(accountId, 'withdraw', amount)


/* ==========================================================================================
 * ACCOUNT CRUD
 *
 * Absent until now on the stated grounds that a function nothing calls is dead code. The
 * admin dashboard calls them, so they arrive with it.
 * ========================================================================================== */

/*
 * Open an account for a customer.
 *
 * POST /api/v1/customers/{customerId}/accounts rather than POST /api/v1/accounts, which the
 * controller also offers. The difference is ownership: the plain create makes an account
 * owned by NOBODY - it exists, it has a balance, and no customer lists it. The nested one
 * creates it and adds its id to the customer in a single call. There is no screen in this
 * app that wants an ownerless account, so there is no function here that makes one.
 *
 * TWO TRANSLATIONS, and they are the mirror image of toAccount's:
 *
 *   type - the form speaks in words ("savings"), the model in enum constants ("SAVINGS").
 *   overdraftLimit - the form collects a MAGNITUDE, because "how far below zero may this go"
 *          is the question a person can answer. The model stores a FLOOR, which is that
 *          number negated. toAccount does Math.abs on the way in; this does the negation on
 *          the way out, and the pair of them is the entire reason no component has to know
 *          the model's sign convention. (Savings is forced to 0 by the constructor either
 *          way - "savings has no overdraft" is a fact about savings, not a caller's choice.)
 */
export const openAccountForCustomer = (customerId, account) =>
  request(`/customers/${customerId}/accounts`, jsonRequest('POST', {
    id: Number(account.id),
    type: String(account.type).toUpperCase(),
    balance: Number(account.balance),
    overdraftLimit: -Math.abs(Number(account.overdraftLimit)),
  }))

// 204 on success, 404 if there is no such account. The controller also unlinks the id from
// whichever customer listed it, so there is nothing to tidy up here afterwards.
export const deleteAccount = (id) => request(`/accounts/${id}`, { method: 'DELETE' })

/* ==========================================================================================
 * SIGNING IN - and a careful statement of what that does and does not mean
 *
 * The API is adding an endpoint that takes a username and a password, compares them against
 * the stored customer, and returns that customer - role included - when they match.
 *
 * THAT IS CREDENTIAL COMPARISON. IT IS NOT AUTHENTICATION, and the difference is not
 * pedantry, it is the whole security posture of this application:
 *
 *   Nothing is issued. No token, no cookie, no session id. The response is a customer record
 *   and nothing else.
 *   Nothing is remembered. The server does not know this exchange happened; there is no
 *   server-side state keyed to it.
 *   THE NEXT REQUEST IS AS ANONYMOUS AS THE LAST. Every other endpoint - every GET, every
 *   DELETE - continues to serve any caller with no credential of any kind. Signing in changes
 *   what this UI chooses to SHOW. It changes nothing about what the server will DO.
 *
 * So the honest description of the whole feature is: a lookup that happens to require you to
 * know a password. It tells the UI which dashboard is useful. It does not, and cannot, gate
 * anything, because a gate that only exists in the client is not a gate - curl does not run
 * this code. LoginPage.jsx carries the fuller argument.
 *
 * PASSWORDS ARE PLAINTEXT ON THE SERVER, deliberately and temporarily. That is a decision
 * taken knowingly and deferred to a later phase, and it has one consequence this module must
 * respect: a password is the one value in this app that must never be stored, logged, or put
 * anywhere it could be read back. It is passed straight to the request below and forgotten.
 * It is never held in this module, never returned from it, and the customer that comes back
 * does not contain one - the server marks the field write-only.
 *
 * THE SHAPE, read from the controller rather than taken from a summary:
 *
 *   POST /api/v1/customers/signin, body { username, password }
 *   200 with the customer on success. No password on it - the field is WRITE_ONLY server-side
 *       and is never serialized out.
 *   401 with an EMPTY BODY on failure.
 *
 * A JSON BODY, NOT A QUERY STRING, which is a deliberate choice on the server's side and worth
 * repeating here because the reflex is to reach for ?username=&password=. A query string is
 * part of the URL, and URLs go places bodies do not: web server access logs, the browser's own
 * history, the Referer header handed to whatever the next page links out to, and any proxy in
 * between. A credential in any of those is a credential leaked somewhere nobody thought about,
 * and no amount of HTTPS helps, because those copies are made at the endpoints. It costs
 * nothing to put it in the body, and the habit is worth more than this application is.
 * (Deposit and withdraw DO take a query parameter - an amount is not a secret, and there the
 * argument runs the other way.)
 *
 * BOTH FAILURES ARE THE SAME FAILURE. An unknown username and a wrong password produce the
 * identical 401 with the identical empty body, by design. That is what stops anyone from
 * discovering which usernames exist, one request per guess - and it is a property the CLIENT
 * can destroy without touching the server, simply by writing copy that distinguishes them.
 * Nothing in this module or above it may imply which half was wrong.
 *
 * The path was originally guessed as /login and corrected once the endpoint landed. The guess
 * cost one line because it lived here and nowhere else - the same reason the @RequestParam
 * mismatch on deposit cost one line.
 * ========================================================================================== */

const SIGN_IN_PATH = '/customers/signin'

/*
 * The status that means "those credentials do not match anybody".
 *
 * This was a Set of 401, 403 and 404 while the endpoint was still being written, hedging
 * across the codes somebody might reasonably have reached for. It is 401, and the hedge is
 * deleted rather than left in place - a guess kept after the answer is known is not caution,
 * it is a comment that has quietly stopped being true.
 *
 * Collapsing it also FIXES something, which is the argument for doing it rather than leaving a
 * harmless-looking Set alone. While 404 was absorbed here, a missing ENDPOINT was silently
 * reported as a wrong password. Now a 404 throws, and LoginPage's branch for it - "sign-in is
 * not available" - can finally fire and mean what it says.
 *
 * A named constant rather than a bare 401 at the point of use, for the same reason REFUSED is
 * one: the number says what was sent, the name says what it meant.
 *
 * The same shape as REFUSED for a declined withdrawal, and for the same reason: "no match" is
 * an ordinary, expected answer to a sign-in attempt, not a malfunction, and an ordinary answer
 * should not arrive as an exception. A caller forced to catch in order to discover a wrong
 * password will eventually catch a network outage in the same block and treat the two alike -
 * which is how "the server is down" becomes "your password is wrong" on somebody's screen.
 */
const NOT_RECOGNISED = 401

/*
 * Resolves to the customer when the credentials match, or NULL when they do not.
 *
 * Throws only for a real failure - the network, a 500, an endpoint that is not there - so the
 * caller can tell "those credentials are wrong" from "could not ask".
 *
 * NULL CARRIES NO DETAIL, and that is the contract rather than a shortcut. The server answers
 * an unknown username and a wrong password with the same 401 and the same empty body, so there
 * is nothing here to tell them apart WITH - which is the point, not a limitation. Callers must
 * not invent a distinction the API is deliberately refusing to provide; the only honest
 * message names both possibilities without choosing between them.
 */
export async function signIn(username, password) {
  try {
    // built here rather than taking the form's state object, so that whatever else that
    // object happens to be carrying cannot ride along into a request body by accident. Two
    // named parameters is also a smaller thing to be careless with than an object.
    const customer = await request(SIGN_IN_PATH, jsonRequest('POST', { username, password }))

    // the server spells failure as a 401, so this ?? is not carrying a second contract - it
    // is one character standing between an unexpectedly empty 200 and a caller reading .id
    // off undefined on the very next line. Kept for that alone.
    return customer ?? null
  } catch (err) {
    if (err instanceof ApiError && err.status === NOT_RECOGNISED) {
      return null
    }
    throw err
  }
}

/*
 * Change one customer's role.
 *
 * A PUT, and no longer with any misgivings about it. This function used to carry a warning
 * that it might be sending a body the server would reject, and a suggestion that a dedicated
 * PATCH /customers/{id}/role would express the operation better. Both are resolved:
 *
 *   The password question is closed. PUT preserves the stored password when the body omits
 *   one, which is what toWireCustomer now does deliberately rather than by necessity.
 *
 *   PATCH was CONSIDERED AND DECLINED, not overlooked, and the reasoning is better than the
 *   suggestion was. A role is a field a client both reads and owns, which is precisely the
 *   set PUT replaces - so a role change is an ordinary edit and needs no special endpoint.
 *   The fields that seemed to argue for one, password and accountIds, are outside that set
 *   and are handled by being left out of the body entirely. Adding a verb per field would
 *   have solved by proliferation what the rule solves by definition.
 *
 * The spread is safe now that toWireCustomer names its fields: whatever else the caller's
 * customer object happens to be carrying - an accountIds list from the admin dashboard's
 * table, say - is dropped on the way out rather than sent and ignored.
 *
 * IT IS NOT A PERMISSION CHECK, and no code path in this app makes it one. The control that
 * calls this appears only on the admin dashboard, which is a decision about which screen the
 * operation belongs on. The API accepts a role change from any caller, with no credential,
 * and will continue to. Hiding the control hides it from people using the UI as intended and
 * from nobody else.
 */
export const setCustomerRole = (customer, role) =>
  updateCustomer(customer.id, { ...customer, role })

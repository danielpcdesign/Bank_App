import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'

import AdminDashboard from '../components/AdminDashboard.jsx'
import CustomerDashboard from '../components/CustomerDashboard.jsx'
import NoAuthNotice from '../components/NoAuthNotice.jsx'
import {
  accountIdsOf,
  deleteAccount,
  deleteCustomer,
  getAccounts,
  getCustomer,
  getCustomerAccounts,
  getCustomers,
  isAdmin,
  roleOf,
  setCustomerRole,
} from '../services/api.js'
import { getSignedInId, signOut } from '../services/viewer.js'

/*
 * The dashboard for one person, at /dashboard/:id. Which dashboard depends on their role.
 *
 * ==========================================================================================
 * HOW THE APP KNOWS WHOSE DASHBOARD TO SHOW
 * ==========================================================================================
 *
 * A password. There is now a real sign-in: LoginPage posts a username and password, the
 * server compares them against the stored customer, and the customer that comes back - role
 * included - decides which dashboard renders. A wrong password is refused by the server. You
 * cannot get somebody else's dashboard by typing their name into that form.
 *
 * AND IT IS STILL NOT AUTHENTICATION, which is the sentence this comment exists for. The
 * check is real; everything that would make it matter is absent. Nothing is issued - no
 * token, no cookie, no session. Nothing is remembered by the server. The next request is as
 * anonymous as the last, and every endpoint continues to answer any caller with no credential
 * at all. So:
 *
 *   /dashboard/4 TYPED INTO THE ADDRESS BAR STILL WORKS, signed in or not. This page does not
 *   check, and that is deliberate rather than an omission. A client-side redirect away from a
 *   URL is not access control: the data behind it is one unauthenticated GET away for anybody
 *   who wants it, and a guard in front of a door with no lock is worse than no guard, because
 *   the next reader believes the door is locked. NoAuthNotice says so on the screen instead.
 *
 * The identity therefore lives in TWO places, doing two different jobs, and keeping them
 * apart is what makes the arrangement legible:
 *
 *   THE URL says which dashboard is being rendered. It is the parameter this component reads,
 *   it is what makes a dashboard linkable, and it is plainly editable by anyone - which is an
 *   honest disclosure of a system that checks nothing, not a leak.
 *
 *   sessionStorage says who signed in, so a refresh does not dump you back at the form. It
 *   holds an id and nothing else - never the password, never the role. services/viewer.js is
 *   the only file that touches it and sets out the compromise in full, including the part
 *   that has to be said out loud: a restore does not re-check the password, so anyone who can
 *   edit sessionStorage can put any id there. That adds no weakness, because typing the URL
 *   already does the same thing - but it WOULD be a genuine vulnerability the moment anything
 *   is enforced, which is why that file is marked for deletion rather than migration.
 *
 * WHAT WAS REJECTED, and the rejections still matter more than the choice:
 *
 *   A CHOOSER PAGE listing every customer with a link into each dashboard - which is what this
 *   app had before the sign-in existed, and it was the right answer then. It is wrong now: a
 *   way to step into anybody's dashboard without a password, sitting beside a form that
 *   demands one, makes the password decorative - and the password is the one part of this
 *   that genuinely works.
 *
 *   CACHING THE WHOLE CUSTOMER, role and all, so the dashboard renders without a request. It
 *   is what most apps do and it saves a round trip. It also makes the client the holder of its
 *   own claim about what it may see. That is inert today, since nothing is enforced anywhere -
 *   but it is the wrong SHAPE, and the shape is what Phase 10 inherits. An id, re-read from
 *   the server on every load, keeps the server the authority on who somebody is.
 *
 *   A ROLE SWITCH with no person attached - honest about being fake, but it detaches the role
 *   from the record it belongs to, and then "your accounts" has no antecedent. The thing being
 *   chosen is a WHO; the role is something that who happens to have.
 *
 * WHAT PHASE 10 CHANGES: the server issues a principal and verifies it on every request. The
 * id stops coming from the path, /dashboard means "mine" because the token says so,
 * services/viewer.js is deleted, and the endpoints start refusing callers who present nothing.
 * Because nothing here persists a credential and nothing here decides an entitlement, that is
 * a change to a route and a fetch rather than an unpicking.
 *
 * ==========================================================================================
 * WHY THIS IS A ROUTE AND NOT A COLUMN ON THE CUSTOMERS TABLE
 * ==========================================================================================
 *
 * Adding a column to a working table is much the cheaper change, so the burden is on the new
 * route. It is discharged by what has to fit: an account carries an id, a type, a balance, an
 * overdraft limit AND, for its owner, an amount input with two buttons that move real money.
 * A customer may have several. That is a table's worth of content per person, and a table
 * does not go in a table cell.
 *
 * Rejected: A COLUMN reading "3 accounts" - cheapest, and possible now that Customer carries
 * accountIds, but a count is not a balance, so it discharges no part of the brief while
 * making a working table depend on a new field. AN EXPANDABLE ROW - keeps it on one screen,
 * but the expanded state is not in the URL, so it cannot be linked, the back button does not
 * close it, and a refresh loses it; App.jsx made this same argument when "/" was split into
 * "/" and "/customers". A TOP-LEVEL /accounts PAGE of every account - that view exists, but
 * it belongs to the admin dashboard, where the customers are also loaded and an owner can
 * therefore be named.
 *
 * ==========================================================================================
 * THE OWNER COLUMN, AND WHAT THE API WOULD NEED
 * ==========================================================================================
 *
 * GET /api/v1/accounts returns accounts with no owner on them. An admin looking at every
 * account in the bank cannot read a row that does not say whose it is, and the naive fix -
 * one getCustomer per row - is N+1 requests to render one table.
 *
 * It is avoided here because the admin view needs the full customer list ANYWAY, and Customer
 * carries accountIds. Two requests, made in parallel, and the join happens in the map below.
 * That is the right shape for this dashboard specifically. It is NOT a general answer, and
 * the API would need one of the following before any other screen wants an owner beside an
 * account - recorded here so the next person does not rediscover it:
 *
 *   Account gains a customerId. The obvious fix, and it makes the ownership fact readable
 *   from the account rather than only from the far side of the relationship. The cost is that
 *   ownership is then stored twice - once here, once in the customer's accountIds - and two
 *   copies of one fact can disagree.
 *
 *   Or /accounts grows an expansion - ?expand=owner - returning the owner inline. Keeps one
 *   copy of the fact and moves the join to the server, where it is one query rather than a
 *   round trip per row.
 *
 * There is one thing the client-side join cannot fix, and it is a genuine finding rather than
 * a limitation: POST /api/v1/accounts creates an account owned by NOBODY. Such an account is
 * in no customer's accountIds, so it appears in this join with no owner - and AccountRow
 * labels it "Unassigned" rather than leaving a blank cell, because it is real money in a real
 * record that no other screen in the app can reach.
 */
export default function DashboardPage() {
  const { id } = useParams()

  // the person whose dashboard this is. null until loaded, so the render can tell "not
  // loaded" from "loaded" - the same reason EditCustomerPage starts its form as null.
  const [viewer, setViewer] = useState(null)

  // arrays, not null: the first render happens before any data arrives and the lists below
  // would crash reading .length off null.
  const [accounts, setAccounts] = useState([])
  const [customers, setCustomers] = useState([])

  // the three states kept apart, as CustomersPage keeps them. A blank screen means three
  // different things and the user is entitled to know which: still asking, asked and failed,
  // asked and the answer is none. The third belongs to the list components; the first two
  // belong here.
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  /*
   * Set when the SIGNED-IN customer turns out not to exist. See the load() catch below - this
   * is state rather than an immediate redirect because a component may not navigate during
   * render or from inside an async callback without React complaining; it records the fact and
   * the render returns a <Navigate>.
   */
  const [signedInGone, setSignedInGone] = useState(false)

  /*
   * WRAPPED IN useCallback, which CustomersPage's equivalent is not, and the difference is
   * forced by the dependency array. A function declared in a component body is a new object
   * every render, which is harmless when the effect that calls it depends on nothing.
   * CustomersPage loads once on mount and passes []. This page cannot: it has to reload when
   * :id changes, or switching between two people would leave one person's balances under the
   * other's name. An effect with a real dependency array that also calls a freshly-built
   * function is either a lie about what it depends on or an infinite loop, depending on
   * whether the function is listed. useCallback resolves that honestly rather than with a
   * suppression comment claiming the dependency does not matter.
   */
  const load = useCallback(async () => {
    try {
      // the role is a property of a record, so it has to be fetched before anything can be
      // decided by it. Hence two phases rather than one Promise.all: what to request second
      // genuinely depends on the first answer.
      const loadedViewer = await getCustomer(id)

      if (isAdmin(loadedViewer)) {
        // every account and every customer, in parallel - neither needs the other's answer to
        // be sent. The customers are not only for the list further down: they are what turns
        // an ownerless account row into a named one, and what fills the owner select on the
        // open-account form.
        const [allAccounts, allCustomers] = await Promise.all([getAccounts(), getCustomers()])
        setAccounts(allAccounts)
        setCustomers(allCustomers)
      } else {
        // a customer's dashboard asks only for their own accounts. Not a restriction - the
        // endpoint that returns everyone's is one line away and would answer - simply the
        // data this screen displays.
        setAccounts(await getCustomerAccounts(id))
        setCustomers([])
      }

      setViewer(loadedViewer)
      // a successful load clears a previous failure, or a transient error sits on screen
      // contradicting the fresh data underneath it.
      setError(null)
    } catch (err) {
      /*
       * A 404 IS TWO DIFFERENT SITUATIONS AND THEY NEED DIFFERENT ANSWERS.
       *
       * The ordinary one: somebody typed an id, or followed a stale link, or an admin is
       * looking at a customer another admin has just deleted. The page says so and offers a
       * way out. That was the only case that existed while a dashboard was one screen among
       * many.
       *
       * The one that matters now: the missing customer IS the signed-in one. The whole
       * application is behind a sign-in gate, and that gate checks only that an id is
       * present - it cannot check that the id still means anything without a fetch, and a
       * fetch is the thing that would leak a rendered frame. So an id that has stopped being
       * real gets a visitor through the gate and then fails on every page behind it. A
       * customer deleted in another tab, or a wiped and reseeded database, would leave the
       * app looking signed in and being completely unusable, with no control anywhere that
       * obviously fixes it. "Sign out" is the fix and it is in a nav bar the user has no
       * reason to suspect.
       *
       * So this signs them out and sends them back to the form, with the reason. The stored
       * id is the only thing standing between the user and a working app, and the moment it
       * is known to be worthless it should stop being believed. Erring toward signed-out is
       * also the harmless direction: the cost is retyping a password, where the cost of the
       * other choice is an app that cannot be recovered without DevTools.
       */
      if (err.status === 404) {
        if (String(id) === getSignedInId()) {
          signOut()
          setSignedInGone(true)
          return
        }
        // not me - just a bad address. The signed-in visitor is fine and stays signed in.
        setError(`No customer with id ${id}.`)
      } else {
        setError(err.message)
      }
    } finally {
      // the request is over either way, and the one thing worse than an error message is a
      // spinner that never stops.
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    // the effect callback itself is NOT async: React reads an effect's return value as a
    // cleanup function and would try to call the promise an async function returns.
    //
    // The suppression is a judgement, not a shrug, and CustomersPage documents it at length.
    // set-state-in-effect exists to catch a genuine loop - state set synchronously inside an
    // effect starts a render, which runs the effect, which sets state. An async function runs
    // synchronously only as far as its first await, and load() awaits before it touches
    // state, so every setState lands in a later tick. The rule cannot see past the call
    // boundary into the suspension point.
    // eslint-disable-next-line react/set-state-in-effect
    load()

    // [load], and load changes exactly when id changes. Navigating from /dashboard/2 to
    // /dashboard/3 matches the SAME route, so React reuses this component instance rather
    // than remounting it; with no dependency the page would keep showing customer 2's money
    // under a URL saying 3.
  }, [load])

  /*
   * THE JOIN. account id -> the customer whose accountIds list contains it.
   *
   * Built once per customers array rather than once per row: a .find() inside the render
   * would be O(customers) per account and O(accounts x customers) for the table, where a Map
   * built once makes each lookup O(1). At this size neither is measurable - the reason to
   * build the map is that it states the relationship in one place instead of re-deriving it
   * on every row.
   *
   * Empty on a customer's dashboard, because customers is empty there and no owner column is
   * rendered. useMemo, not because it is expensive, but because the map's IDENTITY is what
   * ownerOf closes over, and rebuilding it every render would give every row a new function.
   */
  const ownerByAccountId = useMemo(() => {
    const map = new Map()
    for (const customer of customers) {
      // accountIdsOf, not customer.accountIds: the field name and the missing-versus-empty
      // question are API facts, and they live in the api module with every other API fact.
      for (const accountId of accountIdsOf(customer)) {
        map.set(accountId, customer)
      }
    }
    return map
  }, [customers])

  // null rather than undefined for "no owner found", so AccountRow can tell "this table has
  // no owner column" from "this table has one and this account is in nobody's list".
  const ownerOf = useCallback(
    (accountId) => ownerByAccountId.get(accountId) ?? null,
    [ownerByAccountId],
  )

  /*
   * DELETING, and the 404 that is not a failure.
   *
   * Both handlers follow the rule CustomersPage established: a 404 means someone else already
   * deleted it, or this tab's list is stale. The outcome the user asked for is already true,
   * so it is not worth an error message - but the screen is provably wrong, which is exactly
   * why the refetch still runs.
   *
   * And the refetch is a refetch rather than a local splice, for the reason the transaction
   * handler gives: it costs a round trip and leaves the screen agreeing with the database.
   * Deleting a customer also unlinks their accounts on the server, so a local edit here would
   * have to reproduce a server-side cascade to stay correct - which is the point at which
   * "just remove the row" stops being cheaper.
   */
  const handleDeleteAccount = async (accountId) => {
    try {
      await deleteAccount(accountId)
    } catch (err) {
      if (err.status !== 404) {
        setError(err.message)
        return
      }
    }
    load()
  }

  const handleDeleteCustomer = async (customerId) => {
    try {
      await deleteCustomer(customerId)
    } catch (err) {
      if (err.status !== 404) {
        setError(err.message)
        return
      }
    }
    // deleting the customer whose dashboard this is is not blocked, and the refetch handles
    // it honestly: getCustomer 404s and the page says there is no customer with that id.
    // Blocking it would be a permission-shaped guard on an API that would carry out the
    // request regardless, which is the exact kind of theatre this app is avoiding.
    load()
  }

  /*
   * Changing somebody's role.
   *
   * A PUT of the whole customer with one field replaced - the only update the API offers -
   * and then a refetch, for the reason every other mutation on this page refetches: it costs
   * a round trip and leaves the screen agreeing with the database. Here that matters more
   * than usual, because the customer being edited may be the one whose dashboard this is, and
   * demoting yourself should visibly change the page you are standing on rather than leaving
   * an admin dashboard rendered for somebody who is no longer an admin.
   *
   * Which is allowed, and deliberately not guarded. Blocking an admin from changing their own
   * role would be a permission-shaped check on an operation the API grants to anyone, and the
   * refetch handles the outcome honestly: the page re-renders as the customer dashboard.
   *
   * THE 400 BRANCH used to be the one to watch, because a PUT could not supply the write-only
   * password and might have been rejected for it. That is settled - the server preserves the
   * stored password when the body omits it - so a 400 here now means what a 400 should mean:
   * the body was genuinely malformed. The branch stays, because the server names no field and
   * a bare "Invalid request." on a control the user did not type into is worth more words
   * than that.
   */
  const handleRoleChange = async (customer, role) => {
    try {
      await setCustomerRole(customer, role)
    } catch (err) {
      if (err.status === 400) {
        setError('The server rejected the role change as an invalid request.')
      } else if (err.status !== 404) {
        // 404 means somebody else deleted them, so the record this was editing is gone. The
        // refetch below is what puts the screen right; an error about it would be describing
        // a customer that no longer exists.
        setError(err.message)
      }
      // falls through to the refetch either way - the list on screen is provably stale now.
    }
    load()
  }

  /*
   * The signed-in customer no longer exists, and signOut has already run. Redirecting from the
   * render rather than from the catch is what keeps this legal - <Navigate> is a component
   * that navigates as an effect of being rendered, which is the supported way to say "this
   * route resolves somewhere else" without calling navigate() at a moment React objects to.
   *
   * Before the error branches below, deliberately: this is not an error to display, it is a
   * different destination.
   */
  if (signedInGone) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ notice: 'That account no longer exists. Please sign in again.' }}
      />
    )
  }

  // failed with nothing to show. Distinct from loading, and it gets a way out: this is a
  // screen reached by following a stale link or typing an id, so a dead end is the wrong
  // answer. EditCustomerPage does the same thing for the same reason.
  if (error && !viewer) {
    return (
      <section>
        <p className="error">{error}</p>
        <p><Link to="/dashboard">Choose someone to view as</Link></p>
      </section>
    )
  }

  if (loading) {
    return <p>Loading...</p>
  }

  return (
    <section>
      <h1>
        {isAdmin(viewer) ? 'Administrator dashboard' : 'Customer dashboard'}
      </h1>

      <NoAuthNotice />

      {/* said plainly and near the top, because the whole page is about one person and the
          URL is the only other place that says which. */}
      <p>
        Viewing as <strong>{viewer.fullName}</strong>{' '}
        {/* roleOf, not "admin or else customer". A record written before the field existed
            has NO role, and printing "customer" for it would state something the data does
            not say. It still gets the customer dashboard - roleOf('') is not 'admin' - and
            the difference between having that role and merely not having the other one is
            worth one span. */}
        <span className="muted">
          ({roleOf(viewer) || 'no role set'})
        </span>
      </p>

      {/* a failure AFTER a successful first load - a refetch that could not reach the server.
          The tables below are still on screen and are now possibly stale, which is exactly
          why this says so rather than silently leaving old numbers up. */}
      {error && <p className="error">{error}</p>}

      {isAdmin(viewer)
        ? (
          <AdminDashboard
            accounts={accounts}
            customers={customers}
            ownerOf={ownerOf}
            onDeleteAccount={handleDeleteAccount}
            onDeleteCustomer={handleDeleteCustomer}
            onRoleChange={handleRoleChange}
            onChanged={load}
          />
        )
        : (
          // onCompleted is load: a deposit or withdrawal refreshes from the server rather
          // than adjusting the number locally. A balance is arithmetic, and the server may
          // have done more to it than this one transaction; a balance the client worked out
          // for itself is a second source of truth for the one number that must not have one.
          <CustomerDashboard
            customer={viewer}
            accounts={accounts}
            onCompleted={load}
          />
        )}
    </section>
  )
}

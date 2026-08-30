import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'

import AdminDashboard from '../components/AdminDashboard.jsx'
import CustomerDashboard from '../components/CustomerDashboard.jsx'
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
 *   THE URL NO LONGER DECIDES WHAT YOU MAY SEE, and this paragraph used to say the opposite.
 *   It said /dashboard/4 worked for anybody signed in, that this page did not check, and that
 *   not checking was deliberate because a client-side redirect is not access control.
 *
 *   The first two were true and the conclusion was wrong, and a user found out how wrong: a
 *   registered customer could type an administrator's id and be handed the administrator's
 *   screen. The reasoning had slipped from "a redirect is not security" - correct - to "so do
 *   not bother deciding at all", which is not the same claim. Refusing to build a fake lock is
 *   right; refusing to decide which screen a person gets is just a missing feature, and here
 *   it was a missing feature that showed every customer in the bank to any of them.
 *
 *   So this page decides now, from `me`. It is still not access control - the data behind it
 *   is one unauthenticated GET away for anybody who wants it - but "not a security boundary"
 *   and "not a boundary at all" are different things,
 *   and only the first was ever the intention.
 *
 * The identity therefore lives in TWO places, doing two different jobs, and keeping them
 * apart is what makes the arrangement legible - and confusing them is what caused the bug:
 *
 *   THE URL says WHICH CUSTOMER'S ACCOUNTS are being displayed. It is an address. It is
 *   supplied by the person it would have to constrain, so it can say what is being asked for
 *   and can never say who is entitled to it.
 *
 *   sessionStorage says WHO IS ASKING, and it is the only thing consulted for that. It holds
 *   an id and nothing else - never the password, never the role. services/viewer.js is the
 *   only file that touches it and sets out the compromise in full, including the part that has
 *   to be said out loud: a restore does not re-check the password, so anyone willing to edit
 *   sessionStorage can put any id there and be treated as that person.
 *
 *   THAT SENTENCE USED TO END "which adds no weakness, because typing the URL already does the
 *   same thing", and that reassurance is now expired. It was true while the URL decided the
 *   dashboard: the two were equivalent, so neither was worth worrying about. Now the URL
 *   decides nothing and this value decides everything, so editing it is strictly the more
 *   powerful move of the two.
 *
 *   It is still not a NEW weakness, and the reason is worth keeping straight: the API answers
 *   every one of these requests to any caller with no credential, so nothing behind this is
 *   protected from anyone determined enough to open DevTools. What changed is that this value
 *   is now the single input the interface trusts - which is exactly why services/viewer.js is
 *   marked for deletion rather than migration. A client-held identity the server believes is
 *   the bug, not a step toward the fix.
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

  /*
   * TWO CUSTOMERS, AND CONFUSING THEM WAS THE BUG.
   *
   * This page used to hold one, called `viewer`, fetched from the id in the URL - and it chose
   * which dashboard to render from THAT record's role. So the URL decided its own
   * authorisation: anybody signed in could type an administrator's id into the address bar and
   * be handed the administrator's screen, with every customer on it, a create form, and a
   * delete button per row.
   *
   * Those are two different questions and only one of them is about permission:
   *
   *   me       - the SIGNED-IN customer, fetched from the id in sessionStorage. The only
   *              authority on what this person may be shown. Nothing typed in the URL can
   *              change it.
   *   subject  - the customer whose accounts are on screen, from the id in the URL. Usually
   *              the same person; different only when an administrator inspects somebody.
   *
   * The rule that falls out is the whole authorisation model of this page: you may view your
   * own dashboard, and an administrator may view anybody's. A non-admin asking for somebody
   * else's is returned to their own.
   *
   * SAME DEFECT CLASS AS THE ONE THE USER REPORTED, one layer deeper. Removing the /customers
   * route closed a door while /dashboard/<admin-id> stayed ajar, because both mistakes came
   * from the same habit - letting the address name the thing being read AND stand in for
   * permission to read it. Worth remembering when adding the next route: the URL is an
   * address. It is supplied by the person it is meant to constrain, so it can say WHAT is
   * being asked for and never WHO is entitled to it.
   */
  const [me, setMe] = useState(null)
  const [subject, setSubject] = useState(null)

  // arrays, not null: the first render happens before any data arrives and the lists below
  // would crash reading .length off null.
  const [accounts, setAccounts] = useState([])
  const [customers, setCustomers] = useState([])

  // the three states kept apart, as every loading screen in this app keeps them. A blank screen
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

  // set when a non-admin asks for somebody else's dashboard. State rather than a redirect
  // issued from the async callback, for the same reason as signedInGone: the render turns it
  // into a <Navigate>, which is the supported way to say "this resolves somewhere else".
  const [denied, setDenied] = useState(false)

  /*
   * WRAPPED IN useCallback, which the simpler loaders in this app do not need, and the reason
   * forced by the dependency array. A function declared in a component body is a new object
   * every render, which is harmless when the effect that calls it depends on nothing.
   * A loader that runs once on mount passes []. This page cannot: it has to reload when
   * :id changes, or switching between two people would leave one person's balances under the
   * other's name. An effect with a real dependency array that also calls a freshly-built
   * function is either a lie about what it depends on or an infinite loop, depending on
   * whether the function is listed. useCallback resolves that honestly rather than with a
   * suppression comment claiming the dependency does not matter.
   */
  const load = useCallback(async () => {
    const signedInId = getSignedInId()
    const viewingSelf = String(id) === String(signedInId)

    try {
      /*
       * WHO I AM, FIRST AND SEPARATELY. Everything else follows from the answer.
       *
       * Its own try/catch so a 404 here is unambiguous: it can only mean the SIGNED-IN
       * customer is gone, never the customer named in the URL. Folded into the outer catch
       * the two would be indistinguishable whenever an admin inspects somebody else, and the
       * handling for them is completely different - one signs you out, one is a bad address.
       */
      let loadedMe
      try {
        loadedMe = await getCustomer(signedInId)
      } catch (err) {
        if (err.status === 404) {
          signOut()
          setSignedInGone(true)
          return
        }
        throw err
      }

      /*
       * THE AUTHORISATION DECISION - taken here, from `me`, and taken BEFORE any of the
       * subject's data is requested.
       *
       * Before, not after, is the point. A non-admin asking for somebody else's dashboard
       * never causes a request for that person's accounts, so there is nothing fetched and
       * nothing in memory to leak even if the redirect below were somehow missed. Deciding
       * after the fetch would mean the data had already been handed to the browser.
       *
       * Still a product boundary and not a security one. GET /customers/{id}/accounts is
       * served to any caller with no credential at all; this decides what THIS INTERFACE
       * shows and stops nobody holding curl.
       */
      if (!isAdmin(loadedMe) && !viewingSelf) {
        setDenied(true)
        return
      }

      if (isAdmin(loadedMe) && viewingSelf) {
        // the administrator's own dashboard: every account and every customer, in parallel -
        // neither needs the other's answer to be sent. The customers are not only for the
        // list further down: they are what turns an ownerless account row into a named one,
        // and what fills the owner select on the open-account form.
        const [allAccounts, allCustomers] = await Promise.all([getAccounts(), getCustomers()])
        setSubject(loadedMe)
        setAccounts(allAccounts)
        setCustomers(allCustomers)
      } else {
        // a customer dashboard - either my own, or one an administrator is inspecting. Only
        // that person's accounts, and no customer list at all: an admin looking at somebody
        // else's dashboard is looking at THEIR screen, not carrying admin controls into it.
        const loadedSubject = viewingSelf ? loadedMe : await getCustomer(id)
        setSubject(loadedSubject)
        setAccounts(await getCustomerAccounts(id))
        setCustomers([])
      }

      setMe(loadedMe)
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
      // reaching here, a 404 can only be about the SUBJECT - the signed-in customer's own
      // fetch has its own catch above. So this is an administrator who typed an id, followed
      // a stale link, or is looking at somebody another admin has just deleted. The visitor
      // is fine and stays signed in.
      setError(err.status === 404 ? `No customer with id ${id}.` : err.message)
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
    // The suppression is a judgement, not a shrug.
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
   * Both handlers follow the same rule: a 404 means someone else already
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
      /*
       * THE LAST ADMINISTRATOR CANNOT BE DELETED, and this branch is the one place in this
       * file where the server says no and means it.
       *
       * Worth being precise about why it is different in kind from everything else here. Every
       * other restriction in this area is a PRODUCT boundary: the customer list is admin-only
       * in this interface, and GET, POST and DELETE /customers answer any caller with no
       * credential regardless. Removing a control removes it from people using the UI as
       * intended and from nobody else.
       *
       * This one is an INVARIANT ABOUT SYSTEM STATE rather than a claim about the caller. "The
       * bank must not end up with zero administrators" needs no idea who is asking, so it
       * needs no authentication to enforce - and therefore it holds for curl exactly as it
       * holds for this button. It is real, and it is real precisely because it does not depend
       * on identity.
       *
       * The status is state-dependent - the same request succeeds once another admin exists -
       * which is the same reasoning that makes a declined withdrawal a 409 rather than a 400.
       * 400 is accepted too, in case the server expresses it that way, because a wrong guess
       * here shows a bare "HTTP error! status: 409" for a refusal the user can actually act on.
       *
       * The message says what to DO. "Could not delete" would be true and useless; the reason
       * is specific, the remedy is specific, and the remedy is unusual enough to be worth
       * spelling out - there is no way to promote somebody through the API, so another
       * administrator has to come from a seed or a database edit.
       */
      if (err.status === 409 || err.status === 400) {
        setError(
          'That is the only administrator, and the server will not leave the bank without one. '
          + 'Another administrator must exist first - roles are set at seed time, so that means '
          + 'a database change rather than anything in this interface.',
        )
        return
      }
      if (err.status !== 404) {
        setError(err.message)
        return
      }
    }
    // deleting the customer whose dashboard this is is not blocked here, and the refetch
    // handles it honestly: getCustomer 404s, the page signs them out and returns them to the
    // form. Blocking it in the client would be a permission-shaped guard on an API that would
    // carry out the request regardless, which is the kind of theatre this app avoids - and the
    // one case that genuinely must not happen, deleting the last admin, is refused by the
    // server, which is where a refusal means something.
    load()
  }

  /*
   * THERE IS NO ROLE CONTROL, AND THAT IS NOW A REAL GUARANTEE RATHER THAN A HIDDEN ONE.
   *
   * This page used to carry handleRoleChange, and the admin dashboard a per-row selector. Both
   * are gone, because PUT /api/v1/customers/{id} no longer reads a role from the body - it
   * replaces username and fullName and nothing else. The field is server-owned, alongside id,
   * password and accountIds.
   *
   * So the control was removed for a different reason than the last two times something like
   * it was removed. It would have gone through, returned 200, changed nothing, and refetched a
   * list showing the old role. A control that appears to work and silently does not is worse
   * than no control: it teaches the operator that the system is lying rather than that the
   * operation is unavailable.
   *
   * WHAT IT COSTS, because this is a genuine trade rather than a free win: there is now NO WAY
   * to make a second administrator through the API. Roles are assigned at seed time. Promoting
   * somebody means a database edit or a code change and a redeploy. That is the price of the
   * guarantee, and it was paid deliberately.
   */

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

  /*
   * A non-admin asked for somebody else's dashboard. Back to their own, without a message.
   *
   * The only way to arrive here is by typing an address - nothing in the interface links one
   * customer to another's dashboard. Announcing "you may not view that" would also overstate
   * what happened: nothing was refused by anything that could refuse it, and the same data is
   * one unauthenticated request away. This is not a screen anybody is forbidden from; it is a
   * screen that is not theirs, and being returned to the one that is says that accurately.
   */
  if (denied) {
    return <Navigate to={`/dashboard/${getSignedInId()}`} replace />
  }

  // failed with nothing to show. Distinct from loading, and it gets a way out: this is a
  // screen reached by following a stale link or typing an id, so a dead end is the wrong
  // answer. EditCustomerPage does the same thing for the same reason.
  if (error && !subject) {
    return (
      <section>
        <p className="error">{error}</p>
        <p><Link to="/dashboard">Back to your dashboard</Link></p>
      </section>
    )
  }

  /*
   * THE NO-LEAK GUARANTEE FOR THE ADMIN SURFACE, and it is this line rather than anything
   * cleverer.
   *
   * `loading` starts true, so the FIRST thing this component ever returns is this. Neither
   * dashboard is in the returned tree until the signed-in customer's role has arrived, which
   * means AdminDashboard cannot mount, cannot run an effect, and cannot put a customer list
   * into the DOM before the decision that gates it has been made.
   *
   * This page deliberately does not use RequireAdmin, and this is why it does not need to:
   * /dashboard/:id serves administrators and customers both, so there is no admin-only route
   * to wrap. Same discipline, resolved one level in.
   */
  if (loading) {
    return <p>Loading...</p>
  }

  // the admin surface is shown when the SIGNED-IN customer is an administrator AND is looking
  // at their own dashboard. An admin inspecting somebody else gets that person's screen, not
  // their own controls pointed at another person's data.
  const showAdmin = isAdmin(me) && String(id) === String(getSignedInId())

  return (
    <section>
      <h1>
        {showAdmin ? 'Administrator dashboard' : 'Customer dashboard'}
      </h1>


      {/* said plainly and near the top, because the whole page is about one person and the
          URL is the only other place that says which. Two lines when they differ, because
          "signed in as" and "looking at" being the same person is the normal case and worth
          not cluttering. */}
      <p>
        Signed in as <strong>{me.fullName}</strong>{' '}
        {/* roleOf, not "admin or else customer". A record written before the field existed
            has NO role, and printing "customer" for it would state something the data does
            not say. It still gets the customer dashboard - roleOf('') is not 'admin' - and
            the difference between having that role and merely not having the other one is
            worth one span. */}
        <span className="muted">
          ({roleOf(me) || 'no role set'})
        </span>
      </p>

      {me.id !== subject.id && (
        <p>
          Viewing the accounts of <strong>{subject.fullName}</strong>{' '}
          <span className="muted">(customer {subject.id})</span>
        </p>
      )}

      {/* a failure AFTER a successful first load - a refetch that could not reach the server.
          The tables below are still on screen and are now possibly stale, which is exactly
          why this says so rather than silently leaving old numbers up. */}
      {error && <p className="error">{error}</p>}

      {showAdmin
        ? (
          <AdminDashboard
            accounts={accounts}
            customers={customers}
            ownerOf={ownerOf}
            onDeleteAccount={handleDeleteAccount}
            onDeleteCustomer={handleDeleteCustomer}
            onChanged={load}
          />
        )
        : (
          /*
           * onCompleted is load: a deposit or withdrawal refreshes from the server rather
           * than adjusting the number locally. A balance is arithmetic, and the server may
           * have done more to it than this one transaction; a balance the client worked out
           * for itself is a second source of truth for the one number that must not have one.
           *
           * isSelf is the SAME COMPARISON the "Viewing the accounts of" line above makes, and
           * it is passed rather than recomputed there. CustomerDashboard cannot derive it -
           * it is handed the subject and never sees the signed-in id, which is the correct
           * arrangement: a presentational component should not be reading identity. Without
           * it every string on that screen said "your accounts" to an administrator looking
           * at somebody else's.
           *
           * =====================================================================
           * THESE TWO DECISIONS ARE COUPLED. DO NOT TAKE THEM SEPARATELY.
           * =====================================================================
           *
           * Passing onCompleted here is what puts the deposit/withdraw form on an account
           * that is not the viewer's, whenever an administrator inspects a customer. It is an
           * open question whether it should - an admin moving somebody else's money through
           * the same control the owner uses is arguably an operation that ought to look
           * different, which is exactly why AdminDashboard withholds these controls from the
           * admin's OWN view.
           *
           * What makes it more than a style question now: an account is created at ZERO and
           * money enters only through deposit, because the create endpoint stopped taking an
           * opening balance. AdminDashboard has no deposit control. So THIS IS THE ONLY PATH
           * IN THE APPLICATION BY WHICH A NEWLY OPENED ACCOUNT CAN BE FUNDED - an admin opens
           * it, follows the owner's name in the accounts table to their dashboard, and
           * deposits here.
           *
           * Removing onCompleted from this call would therefore not merely tighten a screen.
           * It would leave every new account at zero with no way to fund it through any
           * interface, and the break would show up nowhere near this line - AddAccountForm
           * would keep reporting "Account opened" perfectly correctly. If that tightening is
           * ever wanted, the admin side needs a funding control FIRST, and then this can go.
           */
          <CustomerDashboard
            customer={subject}
            accounts={accounts}
            isSelf={me.id === subject.id}
            onCompleted={load}
          />
        )}
    </section>
  )
}

import { useState } from 'react'
import { Link, useNavigate } from 'react-router'

import CreateCustomerForm from '../components/CreateCustomerForm.jsx'
import NoAuthNotice from '../components/NoAuthNotice.jsx'
import { signIn } from '../services/api.js'
import { setSignedInId } from '../services/viewer.js'

/*
 * Open an account of your own. One of exactly two pages an unauthenticated visitor may reach.
 *
 * A CONTAINER NOW, not a form. It used to carry its own copy of the three fields, the submit
 * handler and the 409/400/0 status switch, because registering hit a different endpoint from
 * the admin-side create and the two requests were genuinely different. The API merged those
 * endpoints, the requests became identical, and the second copy of that switch stopped being a
 * difference and started being a liability - the kind where one copy learns about a new status
 * and the other quietly does not.
 *
 * So the form is CreateCustomerForm, shared with the customers page and the admin dashboard,
 * and what is left here is the part actually particular to registering: the framing, and what
 * happens after it succeeds. The child announces; this page decides what that means - the same
 * division every other form in this app follows.
 *
 * THREE FIELDS, AND WHERE THE RESTRICTION LIVES. Username, password, full name. No id and no
 * role, because the endpoint accepts neither - from this form, from curl, from anybody. The
 * server assigns the id and forces the role.
 *
 * That is worth stating carefully, because this codebase made the opposite argument about the
 * admin form's old role selector and was right to: removing a control PREVENTS NOTHING while
 * the endpoint behind it still accepts the field. The difference now is that the endpoint does
 * not. The capability is gone from the create path, on the server, which is the only place a
 * capability can be removed.
 *
 * AND THE LIMIT. That is the CREATE path only. PUT /api/v1/customers/{id} still accepts a role
 * from any caller with no credential - the deliberate UI-only gating decision recorded on
 * setCustomerRole. Anybody may register here and then promote themselves with a single PUT.
 * "Registration cannot mint an administrator" is true. "The API cannot mint an administrator"
 * is false, and nothing on this page implies it.
 */
export default function RegisterPage() {
  const navigate = useNavigate()

  // only for failures of the step AFTER creation. The form reports its own outcomes; this is
  // for the handoff below, which the form deliberately does not catch on this page's behalf.
  const [message, setMessage] = useState(null)

  /*
   * REGISTER, THEN SIGN IN - two requests, deliberately, rather than trusting the response.
   *
   * The create returns the new customer with its assigned id, and using that would save a
   * round trip. Three reasons not to:
   *
   *   It does not depend on the response SHAPE. Whether that endpoint returns the customer, a
   *   bare id, or a 201 with no body is not something this page should be betting on - the
   *   create path has already been reshaped once during this work. Signing in works whichever
   *   it turns out to be.
   *
   *   There is then exactly ONE path into the signed-in state, and it is the one that checks a
   *   password. A second way in - "you just registered, so you must be you" - is a second thing
   *   to reason about, and the first thing anybody would forget when the sign-in rules change.
   *   That matters more now that being signed in gates the entire application rather than
   *   choosing which dashboard to render.
   *
   *   It proves the account actually works before telling the user it does. An account created
   *   but not signable-into is a failure worth finding here rather than on their next visit.
   *
   * The credentials arrive as the first callback argument because the form has already cleared
   * them from its own state by this point - it does not keep a password after sending one.
   * They are used for one request and stored nowhere.
   */
  const handleCreated = async (submitted) => {
    let customer
    try {
      customer = await signIn(submitted.username, submitted.password)
    } catch (err) {
      // the account exists; only the sign-in failed. Say exactly that, because "registration
      // failed" would be false and would invite them to register again into a 409.
      setMessage(
        err.status === 0
          ? `Your account was created, but signing in failed: ${err.message}`
          : 'Your account was created, but signing in failed. Please try signing in.',
      )
      return
    }

    if (customer === null) {
      // created, but the credentials it just created were not accepted. Nothing useful this
      // page can do about that, and inventing a signed-in state to paper over it would be
      // exactly the fake session this app has refused everywhere else. Hand them to the form
      // that reports sign-in failures properly.
      navigate('/login', {
        replace: true,
        state: { notice: 'Your account was created. Please sign in.' },
      })
      return
    }

    setSignedInId(customer.id)
    // replace: true - "back" from a dashboard should not return to a registration form that has
    // already been submitted, and whose resubmission would now be a 409.
    navigate(`/dashboard/${customer.id}`, { replace: true })
  }

  return (
    <section>
      <h1>Create an account</h1>

      <NoAuthNotice />

      <CreateCustomerForm
        submitLabel="Create account"
        successMessage="Account created. Signing you in..."
        onCreated={handleCreated}
      />

      {message && <p className="error">{message}</p>}

      {/* the absence of an id or role field needs no explanation on screen - a person
          registering has no reason to expect either. What IS worth saying is what they get,
          because "account" is ambiguous on a banking site: this creates a customer record, not
          a bank account. */}
      <p className="muted">
        This creates a customer record. An administrator opens bank accounts for you
        afterwards.
      </p>

      <p>Already registered? <Link to="/login">Sign in</Link></p>
    </section>
  )
}

import { Link } from 'react-router'

/*
 * The landing page.
 *
 * Static - no state, no effects, no props. Worth saying out loud because it is easy to
 * assume every React component needs hooks: a component is just a function returning
 * markup, and one that returns the same markup every time is the simplest and cheapest kind
 * there is. Reach for state when something actually changes.
 *
 * PLACEHOLDER, and honest about it. The copy below describes what the app genuinely does
 * today rather than inventing features - a landing page promising "instant transfers" while
 * the API exposes CRUD on customers is a lie that gets shipped. It will grow as phases land.
 */
export default function HomePage() {
  return (
    <section>
      <h1>Bank App</h1>
      <p className="lead">
        A customer management service built through the JUMP by Cognixia programme -
        Spring Boot and MongoDB behind a React front end.
      </p>

      {/* SPLIT BY ROLE, because one flat list was describing a screen most readers do not
          have. It offered "Browse the customer directory", "Add a customer" and "Remove a
          customer" to everybody - three admin surfaces, one of which no longer has an address
          at all: the directory was a /customers route, it was deleted because any signed-in
          customer could reach it and delete anybody, and the list it held now lives inside the
          administrator dashboard.

          Two headed lists rather than a fetch. The alternative was to read the signed-in
          customer's role and print only their half, which is one request and a loading state on
          a page that otherwise has neither - and it would still have to name the admin
          capabilities somewhere, because a reader deciding whether this application does what
          they need is not always the reader holding the account. Saying who each thing is for
          is honest to both, and keeps the page a plain function of nothing. */}
      <h2>What you can do today</h2>

      <h3>With any account</h3>
      <ul>
        <li>Register a customer record, and sign in to it</li>
        <li>See your own dashboard: your accounts, their types and their balances</li>
        <li>Deposit to and withdraw from one of your accounts</li>
      </ul>

      <h3>As an administrator</h3>
      <ul>
        <li>See every customer and every account in the bank, with the owner of each</li>
        <li>Add a customer, edit one&rsquo;s username or full name, or remove one</li>
        <li>
          Open an account for a customer, or close one. Accounts open empty &mdash; money
          arrives by deposit and by nothing else
        </li>
        <li>Open any customer&rsquo;s dashboard as they see it</li>
      </ul>

      {/* the two lists differ in KIND and not only in content, and this is the sentence that
          says so - without it, a heading reading "As an administrator" is easily read as a
          permission the server would honour. It is not one. It belongs to the same argument the
          section below makes, and is kept short here because that section makes it properly. */}
      <p className="muted">
        The second list is what this browser offers an administrator. It is not a restriction
        the server applies &mdash; see below.
      </p>

      {/* said on the landing page rather than only on the screens themselves, because this is
          the page somebody lands on before forming an impression of what the app enforces.
          The copy rule this file set for itself - describe what the app genuinely does today
          rather than what it looks like it does - applies hardest to the thing it does not
          do. */}
      <h2>What it does not do</h2>
      <p>
        Signing in checks a real password against the database, and the role on the customer
        it returns decides which dashboard you see. Every page here now requires it. That is
        where it stops. Nothing is issued and nothing is remembered, so every request after
        the sign-in is anonymous, and the API answers all of them from any caller &mdash; the
        gate is in this browser, not in the server. It means the site no longer shows a
        stranger every customer by default, which is worth having. It does not mean the data
        is protected.
      </p>

      {/* Link, not a button with an onClick + navigate(). A navigation that a user chooses
          should be a real link: it can be middle-clicked into a new tab, copied, and read
          by a screen reader as a destination. useNavigate is for moves that are a
          CONSEQUENCE - a save completing - where there is nothing to click. */}
      {/* to the dashboard rather than to a customer directory, which no longer has an
          address. /dashboard means "mine" and resolves to whoever is signed in, so this one
          link is right for an administrator and a customer both - it lands each of them on
          the screen that is theirs. */}
      <p><Link to="/dashboard">Go to your dashboard &rarr;</Link></p>

      <h2>Coming next</h2>
      <ul>
        <li>Transfers between accounts</li>
        <li>The API enforcing the role, rather than the browser</li>
        <li>Search and filtering</li>
      </ul>
    </section>
  )
}

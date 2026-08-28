/*
 * Contact. Static placeholder.
 *
 * NO FORM HERE, on purpose, and this is the interesting decision on the page.
 *
 * A contact form with nothing behind it is worse than no form. It looks like it works, the
 * user types a real message, presses Send, and the message goes nowhere - a silent failure
 * that the user has no way to detect. Given the choice between a control that lies and a
 * link that is plainly limited, take the link.
 *
 * What a real one would need, so the gap is recorded rather than forgotten:
 *   - an endpoint on the API to receive it, with validation
 *   - somewhere for it to go - a mail relay, or a table
 *   - rate limiting and a spam control, because a public unauthenticated POST endpoint is
 *     found and abused within days of going live
 *
 * That is three phases of work, not a component. Until then, mailto: costs one line and is
 * honest about handing the job to the user's own mail client.
 */
export default function ContactPage() {
  return (
    <section>
      <h1>Contact</h1>

      <p className="lead">
        Questions about this application, or about the programme it was built for.
      </p>

      <h2>Project</h2>
      <p>
        {/* A real <a>, not a Link. Link is for routes inside this app; mailto: is not a
            route, and handing it to the router would produce a 404 on a path called
            "mailto:...". The rule: Link for anywhere in this app, <a> for anywhere else. */}
        <a href="mailto:support@example.invalid">support@example.invalid</a>
      </p>

      <h2>Programme</h2>
      <p>JUMP by Cognixia</p>

      {/* .invalid is a reserved TLD that is guaranteed never to resolve - RFC 2606. Using it
          for a placeholder address means a stray click fails immediately and visibly,
          instead of quietly mailing whoever happens to own example.com. */}
      <p className="muted">
        Placeholder details &mdash; real contact information goes here before launch.
      </p>
    </section>
  )
}

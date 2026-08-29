import { Link } from 'react-router'

/*
 * The client-side 404, rendered by App's path="*" route.
 *
 * It was an inline <p>No such page.</p> in the route table. Promoting it to a file is not
 * ceremony: it is the page a user sees at their most confused, so it is the page that most
 * needs a way out - and a route element that grows past one line has become a page whether
 * or not it lives in one.
 *
 * Worth being clear about what this is NOT. It is not an HTTP 404. nginx has already
 * answered this request with 200 and index.html - it has to, or deep links like
 * /customers/2/edit would never load the app that knows what they mean. So the status line
 * says success and the page says not found.
 *
 * That mismatch is a real cost of client-side routing, and the honest one to accept here:
 * the alternative is server-rendered routing, which is a different architecture, not a
 * setting. Crawlers reading the status code rather than the page will index this as a valid
 * page - which is why a real product eventually renders on the server or pre-renders known
 * routes. Not this phase.
 */
export default function NotFoundPage() {
  return (
    <section>
      <h1>Page not found</h1>

      <p>
        There is nothing at this address. It may have been a typo, or a link to something
        that has since been removed.
      </p>

      <p><Link to="/">Back to the home page</Link></p>
    </section>
  )
}

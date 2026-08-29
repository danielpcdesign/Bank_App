/*
 * About. Static placeholder.
 *
 * No import of React needed - JSX has not required it since the automatic runtime, which
 * @vitejs/plugin-react turns on. The transform emits a call to react/jsx-runtime rather than
 * React.createElement, so the identifier `React` never appears in the output.
 */
export default function AboutPage() {
  return (
    <section>
      <h1>About</h1>

      <p className="lead">
        This application is the running deliverable of a full-stack training programme. Every
        layer was built deliberately rather than generated, and each phase adds one idea.
      </p>

      <h2>How it is built</h2>
      <dl>
        <dt>Front end</dt>
        <dd>React with client-side routing, served as static files.</dd>

        <dt>API</dt>
        <dd>Spring Boot, layered as controller &rarr; service &rarr; repository.</dd>

        <dt>Data</dt>
        <dd>MongoDB.</dd>

        <dt>Delivery</dt>
        <dd>Continuous integration on every push; both halves ship as container images.</dd>
      </dl>

      {/* Deliberately vague about versions and hostnames. An About page is public, and
          naming exact framework versions tells anyone reading it which published
          vulnerabilities to try first. The README carries that detail, for people who
          already have the repository. */}
      <p className="muted">
        Placeholder copy &mdash; the product description belongs here once it is written.
      </p>
    </section>
  )
}

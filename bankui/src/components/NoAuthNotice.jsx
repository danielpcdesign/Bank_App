/*
 * The banner on the sign-in page, the register page, and every dashboard.
 *
 * A component rather than a paragraph copied into five files, because the one thing worse than
 * an unenforced boundary is an unenforced boundary that some screens mention and others do
 * not - the silent screens are then the ones that look enforced.
 *
 * IT HAS GOT MORE IMPORTANT AT EVERY STEP, NEVER LESS, and it is worth tracking why, because
 * each step made the application look more protected while protecting exactly as much as
 * before - which is to say, nothing.
 *
 *   When the login was an obvious placeholder, nobody was misled.
 *   Then the password check became real, and the screen started to look like it did something
 *   it does not: the check decides which dashboard renders and nothing else.
 *   Now the whole application sits behind a sign-in gate. That is the most convincing of the
 *   three by a distance - an app that shows you nothing until you authenticate is the shape
 *   of an app with access control - and it is still a client-side redirect in front of an API
 *   that answers every request from every caller with no credential at all.
 *
 * So the wording has to do two jobs at once and neither may be dropped for the other. It must
 * not claim there is no security, which is now slightly unfair to a real password check. It
 * must not let the gate imply there is any, which is badly false. The line falls between what
 * the UI shows and what the server does, and that is where the sentence is drawn.
 *
 * WHAT THE GATE HONESTLY BUYS is named too, because a reader told only that it protects
 * nothing will reasonably conclude it is theatre and delete it. It removes ACCIDENTAL
 * exposure: before it, opening the site showed every customer to whoever arrived. Not
 * volunteering that is worth doing on its own terms, and it is a completely different claim
 * from "this data is protected".
 *
 * It goes away in Phase 10, along with the situation it describes.
 */
/*export default function NoAuthNotice() {
  return (
    <p className="notice">
      <strong>Signing in does not protect anything.</strong> Your password is really checked,
      and signing in is required to reach any page here &mdash; but that gate is in the
      browser, not the API. Nothing is issued and nothing is remembered, so every request
      after it is as anonymous as the one before, and the server answers all of them from any
      caller. What this buys is that the site no longer shows a stranger every customer by
      default. What it does not buy is protection: a role here controls what is{' '}
      <em>displayed</em>, never what is permitted.
    </p>
  )
}*/

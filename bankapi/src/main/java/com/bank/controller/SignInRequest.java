package com.bank.controller;

import jakarta.validation.constraints.NotBlank;

/*
 * The body of a sign-in request, and the only record of its kind in this project.
 *
 * Every other endpoint binds a @Document model straight from the body, and this one cannot:
 * a partial Customer would fail @NotNull on id and role before it ever reached the service,
 * and relaxing those to let sign-in through would weaken validation on create, which is the
 * route that actually needs it.
 *
 * The alternative was ?username=&password= on the query string. Rejected: query strings are
 * written to server access logs, kept in browser history, and forwarded in Referer headers.
 * A password does not belong in a URL even in a training app, because the habit is the thing
 * that carries forward. A body keeps it out of all three.
 *
 * Lives in the controller package because it is an HTTP input shape and nothing below this
 * layer knows it exists - the service takes two plain strings.
 */
public record SignInRequest(@NotBlank String username, @NotBlank String password)
{
}

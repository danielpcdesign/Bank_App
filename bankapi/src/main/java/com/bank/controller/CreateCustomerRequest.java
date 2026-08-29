package com.bank.controller;

import jakarta.validation.constraints.NotBlank;

/*
 * The body of a customer create, and THE ABSENCE OF FIELDS HERE IS THE SECURITY PROPERTY.
 *
 * There is no id and no role, so no caller can express either. The server assigns both: the
 * next free id, and always CUSTOMER. That is a structural guarantee rather than a checked
 * one - nothing has to remember to strip a role, because there is nowhere for a role to
 * arrive. Binding a Customer here instead would reintroduce exactly that: a body carrying
 * role and id, and a check that has to catch them every time, forever.
 *
 * Do NOT add fields to this record to save writing an endpoint. Its shape is the feature.
 *
 * Named for the operation, not the use case. This was briefly RegisterRequest, serving a
 * separate POST /api/v1/customers/register while POST /api/v1/customers stayed an
 * administrative create that DID take a role and an id. The two were merged: with exactly
 * one admin, and that one seeded directly through the repository, nothing ever needed an
 * endpoint that sets a role, so the admin-shaped create preserved only a hole. There is now
 * one way a customer comes into existence. Do not re-add /register believing self-service
 * needs its own route - it is this.
 *
 * WHAT THIS DOES AND DOES NOT GUARANTEE - read both halves:
 *
 *   TRUE: no create path anywhere in this API can mint an admin. Not "will not" - cannot.
 *
 *   FALSE: that the API cannot mint an admin. PUT /api/v1/customers/{id} still accepts a
 *   role from any caller, by an explicit decision that role changes stay on PUT with
 *   UI-only gating. So an anonymous caller can create a customer here and then PUT itself
 *   to ADMIN. The create path is closed; the update path is open, deliberately.
 */
public record CreateCustomerRequest(@NotBlank String username, @NotBlank String password, @NotBlank String fullName)
{
}

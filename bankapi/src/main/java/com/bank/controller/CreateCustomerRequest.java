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
 * WHAT THIS GUARANTEES, and it is now the whole statement rather than half of it:
 *
 *   NO CALLER CAN SET A ROLE ANYWHERE IN THIS API. Not on create - there is no field for
 *   one here. Not on update - PUT /api/v1/customers/{id} no longer writes role either.
 *   Roles are assigned at seed time and by nothing else, so this holds for curl exactly as
 *   it holds for the UI.
 *
 * This comment previously said the opposite: that PUT left the update path open and a
 * caller could create a customer here and then promote itself. That was true when written
 * and is not any more. What it costs, so nobody is surprised: promoting a second admin
 * needs a database edit or a code change, and there is deliberately no endpoint for it.
 */
public record CreateCustomerRequest(@NotBlank String username, @NotBlank String password, @NotBlank String fullName)
{
}

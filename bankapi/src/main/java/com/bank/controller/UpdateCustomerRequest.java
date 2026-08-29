package com.bank.controller;

import jakarta.validation.constraints.NotBlank;

/*
 * The body of a customer update. Two fields, because two fields are all a client owns.
 *
 * THIS RECORD EXISTS TO END A RECURRING BUG, not to tidy anything up. Three times a
 * validation annotation on the shared Customer entity broke a route it was never written
 * for: role's @NotNull broke the create forms, password's @NotBlank made every edit a 400,
 * and role's @NotNull then made every edit a 400 again the moment the front end correctly
 * stopped sending a field it is not allowed to change.
 *
 * The cause was the same each time and it was never really the annotation. Customer was
 * being bound as the request body for operations with different requirements, so a
 * model-level rule had to be simultaneously true for creates, updates and stored documents.
 * It cannot be. An annotation that is right for one route is wrong for another, and the
 * failure always lands on the route nobody was thinking about.
 *
 * With a record per operation, the rule stops being something the code has to REMEMBER not
 * to violate and becomes the SHAPE OF THE REQUEST:
 *
 *   - an omitted role cannot fail validation, because there is no role to omit;
 *   - a smuggled role, password, accountIds or id cannot arrive and be dropped - it cannot
 *     arrive at all;
 *   - Customer's own annotations go back to describing what a STORED customer must be,
 *     which is what a model annotation should have meant all along.
 *
 * NO ID FIELD. The path names the record being edited, so there is no body id to disagree
 * with it - the 400-on-mismatch case is gone because the mismatch is now unrepresentable.
 *
 * Do NOT add fields here to save writing an endpoint. Its shape is the contract.
 *
 * ONE THING THIS COSTS, stated so it is not discovered by accident: PUT used to change a
 * password when one was supplied, and there is no password here, so nothing in this API
 * changes a password any more. It is set at creation and never again. A password change is
 * a different operation with different rules - it needs the current password to be safe -
 * and it is owed rather than forbidden. Do not solve it by adding a password field here.
 */
public record UpdateCustomerRequest(@NotBlank String username, @NotBlank String fullName)
{
}

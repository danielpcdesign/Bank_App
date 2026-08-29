package com.bank.model;

/*
 * What a customer IS, not what a customer may DO. Phase 1 modelled this as a User subclass
 * with getRole() returning "Admin" or "Customer"; one collection means one class, so the
 * subclass becomes a field - the same move AccountType made for savings and checking.
 *
 * READ THIS BEFORE TREATING IT AS A PERMISSION.
 *
 * This role is NOT ENFORCED. Nothing in this application checks it before serving a request,
 * and no endpoint behaves differently because of it. It is stored, returned and filterable:
 * data about a customer, and nothing more.
 *
 * It cannot be enforced yet, and the reason is not that the check was forgotten. There is no
 * authentication in this application - no session, no token, no principal - so at the moment
 * a request arrives the server has no idea who sent it. A role can only gate an action once
 * something has established WHO is acting, and nothing here does.
 *
 * The tempting shortcut is the dangerous one: reading a role off a query parameter, a header,
 * or a field in the request body and branching on it. That is not a check. It is a blank the
 * caller fills in, and any caller would write ADMIN. It would be worse than the nothing we
 * have now, because it would LOOK like access control and be trusted as such by the next
 * person to read it.
 *
 * Enforcement arrives in Phase 10, with Spring Security establishing an authenticated
 * principal. Until then: do not add a role parameter to a service method, and do not let any
 * caller-supplied value decide what a request is allowed to do.
 */
public enum Role
{
    ADMIN,
    CUSTOMER
}

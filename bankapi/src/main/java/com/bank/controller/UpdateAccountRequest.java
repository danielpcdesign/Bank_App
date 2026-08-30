package com.bank.controller;

import com.bank.model.AccountType;

import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotNull;

/*
 * The body of an account replace. Two fields, because two fields are all a client owns.
 *
 * NO BALANCE, for the same reason as CreateAccountRequest and with one extra edge. PUT
 * /api/v1/accounts/{id} bound the whole Account entity and wrote the balance straight to
 * storage - the controller's own documentation described it as "the one route that moves
 * money without going through the account's own rules". That is the same money-creation
 * primitive the create route had, and closing it on create while leaving it here would have
 * fixed half a bug. The stored balance is now carried across untouched by the repository;
 * deposit and withdraw are the only things in this application that change it.
 *
 * NO ID FIELD. The path names the account being replaced, so there is no body id to disagree
 * with it - the 400-on-mismatch case is gone because the mismatch is now UNREPRESENTABLE
 * rather than checked. Same outcome UpdateCustomerRequest produced, and the same reasoning:
 * a rule that cannot be violated beats a rule that has to run correctly every time.
 *
 * WHAT IS LEFT IS WHAT A CLIENT BOTH READS AND OWNS - the rule stated in AGENTS.md and
 * applied here for the fourth time. Balance fails the OWN half: the account's own operations
 * maintain it. Id fails the OWN half: the path is the identity. Type and the overdraft floor
 * pass both, so they are the replacement.
 *
 * A savings account can still never gain an overdraft, because the replacement goes through
 * Account's constructor and the constructor coerces a savings floor to zero.
 */
public record UpdateAccountRequest(
    @NotNull AccountType type,
    @NegativeOrZero Double overdraftLimit)
{
    // an omitted floor is zero, not null. same reasoning as CreateAccountRequest.floor()
    public double floor()
    {
        return (overdraftLimit == null) ? 0.0 : overdraftLimit;
    }
}

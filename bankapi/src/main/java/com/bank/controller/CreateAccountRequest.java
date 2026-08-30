package com.bank.controller;

import com.bank.model.AccountType;

import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/*
 * The body of an account create, and exactly as with CreateCustomerRequest, THE ABSENCE OF
 * A FIELD IS THE SECURITY PROPERTY.
 *
 * THERE IS NO BALANCE HERE. That is the entire reason this record exists.
 *
 * Both create routes used to bind the Account entity itself. Account carries constraints on
 * id and type and NONE on the two money fields, because they were never meant to arrive from
 * a client at all - so this was a 201:
 *
 *     POST /api/v1/accounts?customerId=3
 *     {"id":999,"type":"CHECKING","balance":100.55,"overdraftLimit":50}
 *
 * Two defects in one request. The fractional balance broke the premise that makes double the
 * right type for money in the first place - see Account.balance: a double holds every integer
 * exactly, and admitting one fractional amount is what turns the answer into BigDecimal. And
 * a caller-chosen opening balance is unlimited money creation: open an account holding a
 * million, then withdraw it. It never touched deposit(), which is correct, by going round it.
 *
 * A NEW ACCOUNT OPENS AT ZERO, and money arrives through POST /accounts/{id}/deposit, which
 * is the guarded path - where the positive-and-whole rule actually lives. A balance is a fact
 * the server maintains, never a value a client states. That is the same reasoning that
 * already denies Account a setter for it: a second way to move money is a way to skip the
 * rules the model exists to enforce, and a request body was exactly that.
 *
 * NOTE WHAT THIS DID NOT NEED. Nobody has to be identified to enforce it. "A balance is not
 * client-supplied" is a fact about how the system works, not a judgement about who is asking,
 * so it holds for curl exactly as it holds for the UI - the Enforced row of the three
 * categories in AGENTS.md, alongside the overdraft floor and the last-admin rule.
 *
 * THE FLOOR IS STILL THE CALLER'S, because "how far below zero may this account go" is a real
 * choice made when an account is opened, unlike a balance. It is constrained rather than
 * removed: @NegativeOrZero, because a POSITIVE limit is a floor ABOVE zero, which would make
 * the account refuse ordinary withdrawals - the second half of the bug above. Savings is
 * forced to a zero floor by Account's constructor whatever is sent, since "savings has no
 * overdraft" is a fact about savings rather than a caller's choice.
 *
 * Do NOT add a balance field here to save writing a deposit call. Its shape is the feature.
 */
public record CreateAccountRequest(
    @NotNull @Positive Integer id,
    @NotNull AccountType type,
    @NegativeOrZero Double overdraftLimit)
{
    // an omitted floor is zero, not null. boxed so that "absent" and "0" are distinguishable
    // to validation, unboxed here so the service only ever sees a number.
    public double floor()
    {
        return (overdraftLimit == null) ? 0.0 : overdraftLimit;
    }
}

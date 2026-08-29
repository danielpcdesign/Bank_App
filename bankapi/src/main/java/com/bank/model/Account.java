package com.bank.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Document(collection = "accounts")
public class Account
{
    @Id
    @NotNull
    @Positive
    private Integer id;

    @NotNull
    private AccountType type;

    // double for money, deliberately, and not the mistake it looks like. amounts here are
    // whole numbers - no cents - and a double represents every integer exactly up to 2^53,
    // which is far past any balance this app will hold. the floating point money problem
    // (0.1 + 0.2 != 0.3, error compounding over a run of transactions) only exists once
    // amounts carry fractional parts. excluding them removes the failure mode instead of
    // tolerating it. the constraint is load-bearing: accept one fractional amount and this
    // type becomes the wrong one, and the answer becomes BigDecimal. isWholeAmount() below
    // is what keeps the premise true.
    private double balance;

    // the floor the balance may not cross. zero or negative. savings is always zero -
    // the constructor forces it, because "savings has no overdraft" is a fact about
    // savings, not something a caller gets to choose per account. same whole-number
    // reasoning as balance above.
    private double overdraftLimit;

    public Account()
    {

    }

    public Account(Integer id, AccountType type, double balance, double overdraftLimit)
    {
        this.id = id;
        this.type = type;
        this.balance = balance;
        this.overdraftLimit = (type == AccountType.SAVINGS) ? 0.0 : overdraftLimit;
    }

    //----------------------------------------------------------------OPERATIONS----------------------------------------------------------------

    // false means the account's rules refused it, not that something broke.
    // phase 1 put deposit on the abstract parent because money in behaves identically
    // everywhere. only withdraw ever varied, and that is still true here.
    public boolean deposit(double amt)
    {
        if (amt <= 0 || !isWholeAmount(amt))
        {
            return false;
        }

        balance += amt;
        return true;
    }

    // phase 1 wrote this twice - savings refused anything below zero, checking allowed
    // an overdraft down to a hard coded limit. with the floor stored as a field the two
    // rules are the same comparison, and savings is the case where the floor happens to be zero.
    public boolean withdraw(double amt)
    {
        if (amt <= 0 || !isWholeAmount(amt))
        {
            return false;
        }

        if (balance - amt < overdraftLimit)
        {
            return false;
        }

        balance -= amt;
        return true;
    }

    // whole amounts only. this sits with the other invariants because it is true of every
    // amount for all time, not a rule about one caller. rint rather than a cast: a cast
    // truncates silently, and NaN or an infinity would slip past a plain != comparison.
    private static boolean isWholeAmount(double amt)
    {
        return Double.isFinite(amt) && amt == Math.rint(amt);
    }

    //----------------------------------------------------------------GETTERS----------------------------------------------------------------

    public Integer getId()
    {
        return id;
    }

    public AccountType getType()
    {
        return type;
    }

    public double getBalance()
    {
        return balance;
    }

    public double getOverdraftLimit()
    {
        return overdraftLimit;
    }

    //----------------------------------------------------------------SETTERS----------------------------------------------------------------

    //no setter for id, hard coded at seed

    //no setter for balance either. deposit and withdraw own it, and a setter would be a
    //way to move money that skips the rules above - the one thing this model exists to prevent.
}

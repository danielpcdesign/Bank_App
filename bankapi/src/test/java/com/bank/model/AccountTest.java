package com.bank.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// no spring, no mockito, no storage. these are the rules the model owns, and they are the
// ones carried over from phase 1: money in behaves the same everywhere, money out depends
// on the floor, and a refusal is an in-contract answer rather than a failure.
class AccountTest
{
    private static Account savings(double balance)
    {
        return new Account(1, AccountType.SAVINGS, balance, 0.0);
    }

    private static Account checking(double balance)
    {
        return new Account(2, AccountType.CHECKING, balance, -100.0);
    }

    @Test
    void deposit_addsToTheBalance_andReportsTrue()
    {
        Account account = savings(500.0);

        assertThat(account.deposit(250.0)).isTrue();
        assertThat(account.getBalance()).isEqualTo(750.0);
    }

    // false is "the rules refused it", not "something broke". the balance must be untouched.
    @Test
    void deposit_refusesZeroAndNegative_andLeavesTheBalanceAlone()
    {
        Account account = savings(500.0);

        assertThat(account.deposit(0.0)).isFalse();
        assertThat(account.deposit(-50.0)).isFalse();
        assertThat(account.getBalance()).isEqualTo(500.0);
    }

    // the guard that keeps double a correct choice for money. accept 10.50 once and the
    // stored balance stops being exactly representable, and the type becomes the wrong one.
    @Test
    void deposit_refusesAFractionalAmount()
    {
        Account account = savings(500.0);

        assertThat(account.deposit(10.50)).isFalse();
        assertThat(account.getBalance()).isEqualTo(500.0);
    }

    @Test
    void withdraw_refusesAFractionalAmount()
    {
        Account account = savings(500.0);

        assertThat(account.withdraw(0.01)).isFalse();
        assertThat(account.getBalance()).isEqualTo(500.0);
    }

    // NaN slips past a plain <= 0 test and an infinity slips past a plain integrality test,
    // so both are checked explicitly rather than left to luck.
    @Test
    void deposit_refusesNaNAndInfinity()
    {
        Account account = savings(500.0);

        assertThat(account.deposit(Double.NaN)).isFalse();
        assertThat(account.deposit(Double.POSITIVE_INFINITY)).isFalse();
        assertThat(account.getBalance()).isEqualTo(500.0);
    }

    //----------------------------------------------------------------THE PHASE 1 DIFFERENCE----------------------------------------------------------------

    @Test
    void savings_allowsAWithdrawalDownToExactlyZero()
    {
        Account account = savings(500.0);

        assertThat(account.withdraw(500.0)).isTrue();
        assertThat(account.getBalance()).isEqualTo(0.0);
    }

    // the whole difference between the two types in phase 1, and still the whole difference here
    @Test
    void savings_refusesAWithdrawalThatWouldGoBelowZero()
    {
        Account account = savings(500.0);

        assertThat(account.withdraw(501.0)).isFalse();
        assertThat(account.getBalance()).isEqualTo(500.0);
    }

    @Test
    void checking_allowsAWithdrawalIntoOverdraft()
    {
        Account account = checking(250.0);

        assertThat(account.withdraw(300.0)).isTrue();
        assertThat(account.getBalance()).isEqualTo(-50.0);
    }

    @Test
    void checking_allowsAWithdrawalDownToExactlyTheLimit()
    {
        Account account = checking(250.0);

        assertThat(account.withdraw(350.0)).isTrue();
        assertThat(account.getBalance()).isEqualTo(-100.0);
    }

    @Test
    void checking_refusesAWithdrawalPastTheLimit()
    {
        Account account = checking(250.0);

        assertThat(account.withdraw(351.0)).isFalse();
        assertThat(account.getBalance()).isEqualTo(250.0);
    }

    // "savings has no overdraft" is a fact about savings, so the constructor refuses to
    // store a limit that would contradict it. without this a caller could open a savings
    // account with a -500 limit and withdraw straight through the floor the type promises.
    @Test
    void savings_forcesItsOverdraftLimitToZero_evenWhenOneIsSupplied()
    {
        Account account = new Account(1, AccountType.SAVINGS, 500.0, -500.0);

        assertThat(account.getOverdraftLimit()).isEqualTo(0.0);
        assertThat(account.withdraw(501.0)).isFalse();
    }

    @Test
    void checking_keepsTheOverdraftLimitItWasGiven()
    {
        Account account = new Account(2, AccountType.CHECKING, 0.0, -100.0);

        assertThat(account.getOverdraftLimit()).isEqualTo(-100.0);
    }
}

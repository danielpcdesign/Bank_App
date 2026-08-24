package bank;

// what an account can do. separate from Account, which holds what an account is
public interface AccountOperations
{
    // false means the account's rules refused it, not that something broke
    boolean withdraw(double amt);

    boolean deposit(double amt);
}

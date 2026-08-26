package bank;

public class CheckingAccount extends Account
{
    public CheckingAccount(String acctId, Customer owner, double balance)
    {
        super(acctId, owner, balance);
        this.limit = -100.0; // hard code the overdraft limit for checking accounts
    }

    public CheckingAccount(String acctId, Customer owner)
    {
        super(acctId, owner);
        this.limit = -100.0; // hard code the overdraft limit for checking accounts
    }

    @Override
    public String getType()
    {
        return "Checking";
    }

    @Override
    public boolean withdraw(double amt)
    {
        // checking allows overdraft, but not negative withdrawals
        if (amt <= 0)
        {
            return false;
        }
        if (getBalance() - amt < limit)
        {
            return false;
        }

        setBalance(getBalance() - amt);
        return true;
    }
}

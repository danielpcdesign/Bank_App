package bank;

// savings refuses anything that would take it below zero
public class SavingsAccount extends Account
{
    public SavingsAccount(String acctId, Customer owner, double balance)
    {
        super(acctId, owner, balance);
    }

    public SavingsAccount(String acctId, Customer owner)
    {
        super(acctId, owner);
    }

    @Override
    public String getType()
    {
        return "Savings";
    }

    @Override
    public boolean withdraw(double amt)
    {
        // no overdraft here. this is the whole difference from checking
        if (amt <= 0 || amt > getBalance())
        {
            return false;
        }

        setBalance(getBalance() - amt);
        return true;
    }
}

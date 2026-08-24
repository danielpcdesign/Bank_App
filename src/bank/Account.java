package bank;

// what every account is: an id, an owner, a balance
public abstract class Account implements AccountOperations
{
    private final String acctId;
    private final Customer owner;
    private double balance;
    protected double Limit;

    public Account(String acctId, Customer owner, double balance)
    {
        this.acctId = acctId;
        this.owner = owner;
        this.balance = balance;
    }

    // new accounts open empty, seeded ones don't
    public Account(String acctId, Customer owner)
    {
        this(acctId, owner, 0.0);
    }

    // each subclass names itself, same idea as User.getRole()
    public abstract String getType();

    // deposit is identical everywhere, so it lives here instead of in both children
    @Override
    public boolean deposit(double amt)
    {
        if (amt <= 0)
        {
            return false;
        }

        balance += amt;
        return true;
    }

    public String getAcctId()
    {
        return acctId;
    }

    public Customer getOwner()
    {
        return owner;
    }

    public double getBalance()
    {
        return balance;
    }

    // subclasses only. keeps the field private while withdraw() rules stay in the children
    protected void setBalance(double balance)
    {
        this.balance = balance;
    }

    @Override
    public String toString()
    {
        return acctId + " [" + getType() + "] balance: " + balance;
    }
}

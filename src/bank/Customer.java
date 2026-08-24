package bank;

public class Customer extends User 
{
    public Customer(String username, String password, String fullName) 
    {
        super(username, password, fullName);
    }

    @Override
    public String getRole() 
    {
        return "Customer";
    }
}


package bank;

public class Admin extends User 
{
    public Admin(String username, String password, String fullName) 
    {
        super(username, password, fullName);
    }

    @Override
    public String getRole() 
    {
        return "Admin";
    }
}
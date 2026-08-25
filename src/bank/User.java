package bank;
import java.util.List;
import java.util.Scanner;

 //base type for everyone who can log in. 
public abstract class User 
{

    private String username;
    private String password;
    private String fullName;

    public User(String username, String password, String fullName) 
    {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
    }

    // each subclass has own implementation
    public abstract String getRole();

    public String getUsername() 
    {
        return username;
    }

    public String getFullName() 
    {
        return fullName;
    }

    public void setFullName(String fullName)
    {
        this.fullName = fullName;
    }

    // no getter for password, let user handle check
    public boolean checkPassword(String attempt) 
    {
        return password.equals(attempt);
    }

    public void setPassword(String password) 
    {
        this.password = password;
    }

    //each class implements its own dashboard, since the options are different for each role
    public abstract void dashboard(Scanner in, List<User> users, List<Account> accounts);

    //override for better fromatting
    @Override
    public String toString() 
    {
        return fullName + " (" + username + ") - " + getRole();
    }
}

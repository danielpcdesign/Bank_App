package bank;

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

    //override for better fromatting
    @Override
    public String toString() 
    {
        return fullName + " (" + username + ") - " + getRole();
    }
}

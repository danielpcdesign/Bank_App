package bank;

/**
 * Base type for everyone who can log in. Abstract because "a User" is never
 * a thing you create directly — you create an Admin or a Customer.
 */
public abstract class User {

    private String username;
    private String password;
    private String fullName;

    public User(String username, String password, String fullName) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
    }

    // Overloaded constructor: when we don't know the display name, fall back to
    // the username. Delegates with this(...) so the assignment logic lives in
    // exactly one place.
    public User(String username, String password) {
        this(username, password, username);
    }

    // Each subclass answers this differently. Declaring it abstract forces them
    // to, which is what makes the base class worth having.
    public abstract String getRole();

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    // No getPassword(). The password never leaves the object — callers ask the
    // User to check a guess instead of pulling the value out and comparing it
    // themselves. That is encapsulation doing real work, not just a getter wall.
    public boolean checkPassword(String attempt) {
        return password.equals(attempt);
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return fullName + " (" + username + ") - " + getRole();
    }
}

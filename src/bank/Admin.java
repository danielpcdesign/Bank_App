
package bank;
import java.util.List;
import java.util.Scanner;

public class Admin extends User 
{
    public Admin(String username, String password, String fullName) 
    {
        super(username, password, fullName);
    }

    public void viewAllAccounts(List<Account> accounts) 
    {
        System.out.println("All Accounts:");
        for (Account acct : accounts) 
        {
            System.out.println("Account ID: " + acct.getAcctId() + ", Owner: " + acct.getOwner().getFullName() + ", Balance: " + acct.getBalance());
        }
    }

    public void viewAllCustomers(List<User> customers) 
    {
        System.out.println("All Customers:");
        for (User customer : customers) 
        {
            if (customer.getRole().equals("Customer")) 
            {
                System.out.println("Username: " + customer.getUsername() + ", Full Name: " + customer.getFullName());
            }
        }
    }

    //CRUD
    public void createCustomer(List<User> customers, String username, String password, String fullName) 
    {
        Customer newCustomer = new Customer(username, password, fullName);
        customers.add(newCustomer);
        System.out.println("Customer created: " + username);
    }

    public void deleteCustomer(List<User> customers, String username, List<Account> accounts) 
    {
        for (User customer : customers) 
        {
            if (customer.getUsername().equals(username)) 
            {
                if (customer instanceof Admin) 
                {
                    System.out.println("Cannot delete an admin user.");
                    return;
                }
                // Remove associated accounts
                accounts.removeIf(acct -> acct.getOwner().equals(customer));
                customers.remove(customer);
                System.out.println("Customer deleted: " + username);
                return;
            }
        }
        System.out.println("Customer not found: " + username);
    }

    public void createAccount(List<Account> accounts, String acctId, Customer owner, double balance, String accountType) 
    {
        Account newAccount;
        if (accountType.equalsIgnoreCase("savings")) 
        {
            newAccount = new SavingsAccount(acctId, owner, balance);
        } 
        else if (accountType.equalsIgnoreCase("checking")) 
        {
            newAccount = new CheckingAccount(acctId, owner, balance);
        } 
        else 
        {
            System.out.println("Invalid account type.");
            return;
        }
        accounts.add(newAccount);
        System.out.println("Account created: " + acctId);
    }

    public void deleteAccount(List<Account> accounts, String acctId) 
    {
        for (Account acct : accounts) 
        {
            if (acct.getAcctId().equals(acctId)) 
            {   
                accounts.remove(acct);
                System.out.println("Account deleted: " + acctId);
                return;
            }
        }
    }

    public void editCustomer(List<User> customers, String username, String newPassword, String newFullName) 
    {
        for (User customer : customers) 
        {
            if (customer.getUsername().equals(username)) 
            {
                if (customer instanceof Admin) 
                {
                    System.out.println("Cannot edit an admin user.");
                    return;
                }
                customer.setPassword(newPassword);
                customer.setFullName(newFullName);
                System.out.println("Customer updated: " + username);
                return;
            }
        }
        System.out.println("Customer not found: " + username);
    }

    @Override
    public void dashboard(Scanner in, List<User> users, List<Account> accounts) 
    {
        // Implementation for admin dashboard
        boolean running = true;
        while (running) 
        {
            Main.printMenu(getRole());
            String choice = in.nextLine().trim();
            switch (choice) 
            {
                case "1":
                    viewAllAccounts(accounts);
                    break;

                case "2":
                    viewAllCustomers(users);
                    break;

                case "3":
                    // Create customer
                    System.out.print("Enter username: ");
                    String username = in.nextLine().trim();
                    System.out.print("Enter password: ");
                    String password = in.nextLine().trim();
                    System.out.print("Enter full name: ");
                    String fullName = in.nextLine().trim();
                    createCustomer(users, username, password, fullName);
                    break;

                case "4":
                    // Delete customer
                    System.out.print("Enter username to delete: ");
                    String delUsername = in.nextLine().trim();
                    deleteCustomer(users, delUsername, accounts);
                    break;

                case "5":
                    // Create account
                    System.out.print("Enter account ID: ");
                    String acctId = in.nextLine().trim();
                    System.out.print("Enter owner username: ");
                    String ownerUsername = in.nextLine().trim();
                    User owner = users.stream()
                            .filter(user -> user.getUsername().equals(ownerUsername) && user.getRole().equals("Customer"))
                            .findFirst()
                            .orElse(null);
                    if (owner == null) 
                    {
                        System.out.println("Owner not found or not a customer.");
                        break;
                    }
                    System.out.print("Enter initial balance: ");
                    double balance = Double.parseDouble(in.nextLine().trim());
                    System.out.print("Enter account type (savings/checking): ");
                    String accountType = in.nextLine().trim();
                    createAccount(accounts, acctId, (Customer) owner, balance, accountType);
                    break;

                case "6":
                    // Delete account
                    System.out.print("Enter account ID to delete: ");
                    String delAcctId = in.nextLine().trim();
                    deleteAccount(accounts, delAcctId);
                    break;

                case "7":
                    // Edit customer
                    System.out.print("Enter username to edit: ");
                    String editUsername = in.nextLine().trim();
                    System.out.print("Enter new password: ");
                    String newPassword = in.nextLine().trim();
                    System.out.print("Enter new full name: ");
                    String newFullName = in.nextLine().trim();
                    editCustomer(users, editUsername, newPassword, newFullName);
                    break;
                case Main.EXIT_KEY:
                    running = false;
                    break;  
            }
        }
    }

    @Override
    public String getRole() 
    {
        return "Admin";
    }
}

package bank;
import java.util.List;

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

    public void viewAllCustomers(List<Customer> customers) 
    {
        System.out.println("All Customers:");
        for (Customer customer : customers) 
        {
            System.out.println("Username: " + customer.getUsername() + ", Full Name: " + customer.getFullName());
        }
    }

    //CRUD
    public void createCustomer(List<Customer> customers, String username, String password, String fullName) 
    {
        Customer newCustomer = new Customer(username, password, fullName);
        customers.add(newCustomer);
        System.out.println("Customer created: " + username);
    }

    public void deleteCustomer(List<Customer> customers, String username) 
    {
        customers.removeIf(customer -> customer.getUsername().equals(username));
        System.out.println("Customer deleted: " + username);
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
        accounts.removeIf(acct -> acct.getAcctId().equals(acctId));
        System.out.println("Account deleted: " + acctId);
    }

    public void editCustomer(List<Customer> customers, String username, String newPassword, String newFullName) 
    {
        for (Customer customer : customers) 
        {
            if (customer.getUsername().equals(username)) 
            {
                customer.setPassword(newPassword);
                customer.setFullName(newFullName);
                System.out.println("Customer updated: " + username);
                return;
            }
        }
        System.out.println("Customer not found: " + username);
    }
    @Override
    public String getRole() 
    {
        return "Admin";
    }
}
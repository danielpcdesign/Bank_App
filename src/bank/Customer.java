package bank;
import java.util.List;
import java.util.Scanner;

public class Customer extends User 
{
    public Customer(String username, String password, String fullName) 
    {
        super(username, password, fullName);
    }

    public List<Account> ownedAccounts(List<Account> accounts) 
    {
        return accounts.stream()
                .filter(acct -> acct.getOwner().equals(this))
                .toList();
    }

    @Override
    public void dashboard(Scanner in, List<User> users, List<Account> accounts) 
    {
        // Implementation for customer dashboard
        boolean running = true;
        List <Account> customerAccounts = ownedAccounts(accounts);
        while (running)
            {
                Main.printMenu(getRole());
                String choice = in.nextLine().trim();
                    switch (choice)
                    {
                    case "1":
                        Main.printBalances(customerAccounts);
                        break;

                    case "2":
                        Main.doDeposit(in, customerAccounts);
                        break;

                    case "3":
                        Main.doWithdraw(in, customerAccounts);
                        break;

                    case Main.EXIT_KEY:
                        running = false;
                        break;

                    default:
                        Main.printMessage("unknown option");
                        break; 
                    }
            }
        
    }

    @Override
    public String getRole() 
    {
        return "Customer";
    }
}

package bank;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

// temporary harness for exercising accounts. the login flow replaces this later.
// the menu loop below is for testing only, AGENTS.md wants a single top to bottom flow
public class Main
{
    protected static final String EXIT_KEY = "q";
    private static final double SEED_BALANCE = 1000.0;

    public static void main(String[] args)
    {
        Scanner in = new Scanner(System.in);

        List <User> users = new ArrayList<>();
        Customer daniel = new Customer("daniel", "1234", "Daniel Palencia");
        Customer emily = new Customer("emily", "1234", "Emily Romero");
        User admin = new Admin("admin", "admin", "Admin User");
        users.add(daniel);
        users.add(emily);
        users.add(admin);

        // typed as Account, not SavingsAccount, so the menu never knows which kind it holds
        List<Account> accounts = new ArrayList<>();
        accounts.add(new SavingsAccount("SAV-001", daniel, SEED_BALANCE));
        accounts.add(new CheckingAccount("CHK-001", daniel, SEED_BALANCE));
        accounts.add(new SavingsAccount("SAV-002", emily, SEED_BALANCE));

        printMessage("welcome to the bank");
        printMessage("please sign in with your username and password");
        System.out.print("username: ");
        String username = in.nextLine().trim();
        System.out.print("password: ");
        String password = in.nextLine().trim();
        User user = null;
        List<Account> userAccounts = new ArrayList<>();

        for (User u : users)
        {
            if (u.getUsername().equals(username))
            {
                if (u.checkPassword(password))
                {
                    user = u;
                    //loop through all accounts and add them to user list
                    for (Account acct : accounts)
                    {
                        if (acct.getOwner().getUsername().equals(username))
                        {
                            userAccounts.add(acct);
                        }
                    }
                    break; // user found and authenticated. accounts added. exit loop
                }
            }
        }

        if (user == null)
        {
            printMessage("invalid credentials");
            return;
        }
        
        boolean running = true;
        printMessage("signed in as " + user.getUsername());
        //main application loop, menu driven for testing. the real app is event driven
        user.dashboard(in, users, accounts);

        printMessage("goodbye");
        in.close();
    }

    static void printMenu(String customerType)
    {
        switch (customerType) {
        //admin menu
            case "Admin":
                printMessage("");
                printMessage("1) view all accounts");
                printMessage("2) view all customers");
                printMessage("3) create customer");
                printMessage("4) delete customer");
                printMessage("5) create account");
                printMessage("6) delete account");
                printMessage("7) edit customer");
                printMessage(EXIT_KEY + ") quit");
                break;
        //regular customer menu
            case "Customer":
                printMessage("");
                printMessage("1) show balances");
                printMessage("2) deposit");
                printMessage("3) withdraw");
                printMessage(EXIT_KEY + ") quit");
                break;
            default:
                printMessage("Unknown customer type.");
                break;
        }
        System.out.print("choice: ");
    }

    static void printBalances(List<Account> accounts)
    {
        for (Account acct : accounts)
        {
            printMessage("  " + acct);
        }
    }

    static void doDeposit(Scanner in, List<Account> accounts)
    {
        Account acct = pickAcct(in, accounts);

        if (acct == null)
        {
            return;
        }

        double amt = readAmt(in);
        boolean ok = acct.deposit(amt);

        printMessage(ok ? "deposited " + amt : "deposit refused");
        printMessage("  " + acct);
    }

    static void doWithdraw(Scanner in, List<Account> accounts)
    {
        Account acct = pickAcct(in, accounts);

        if (acct == null)
        {
            return;
        }

        double amt = readAmt(in);

        // the call site cannot tell savings from checking. the object decides
        boolean ok = acct.withdraw(amt);

        printMessage(ok ? "withdrew " + amt : "withdrawal refused by " + acct.getType());
        printMessage("  " + acct);
    }

    //-------------------------------------Helper methods below, not part of the menu loop-------------------------------------

    static Account pickAcct(Scanner in, List<Account> accounts)
    {
        if (accounts.size() == 1)
        {
            //early return for one account
            return accounts.get(0);
        }

        printBalances(accounts);
        System.out.print("account id: ");
        String id = in.nextLine().trim();

        for (Account acct : accounts)
        {
            if (acct.getAcctId().equalsIgnoreCase(id))
            {
                return acct;
            }
        }

        printMessage("no such account");
        return null;
    }

    // bad input becomes 0
    static double readAmt(Scanner in)
    {
        System.out.print("amount: ");
        String raw = in.nextLine().trim();

        try
        {
            return Double.parseDouble(raw);
        }
        catch (NumberFormatException e)
        {
            printMessage("not a number");
            return 0.0;
        }
    }

    // centralized output method, change formatting here
    static void printMessage(String message)
    {
        System.out.println(message);
    }
}

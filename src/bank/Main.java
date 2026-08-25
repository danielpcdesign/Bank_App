package bank;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

// temporary harness for exercising accounts. the login flow replaces this later.
// the menu loop below is for testing only, AGENTS.md wants a single top to bottom flow
public class Main
{
    private static final String EXIT_KEY = "q";
    private static final double SEED_BALANCE = 1000.0;

    public static void main(String[] args)
    {
        Scanner in = new Scanner(System.in);

        List <User> users = new ArrayList<>();
        Customer daniel = new Customer("daniel", "1234", "Daniel Palencia");
        User admin = new Admin("admin", "admin", "Admin User");
        users.add(daniel);
        users.add(admin);

        // typed as Account, not SavingsAccount, so the menu never knows which kind it holds
        List<Account> accounts = new ArrayList<>();
        accounts.add(new SavingsAccount("SAV-001", daniel, SEED_BALANCE));
        accounts.add(new CheckingAccount("CHK-001", daniel, SEED_BALANCE));

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
        while (running)
        {
            printMenu(user.getRole());
            String choice = in.nextLine().trim();

            switch (user.getRole())
            {
                case "Admin":
                    switch (choice)
                    {
                    case "1":
                        ((Admin) user).viewAllAccounts(accounts);
                        break;

                    case "2":
                        ((Admin) user).viewAllCustomers((List<Customer>) (List<?>) users);
                        break;

                    case "3":
                        System.out.print("username: ");
                        String newUsername = in.nextLine().trim();
                        System.out.print("password: ");
                        String newPassword = in.nextLine().trim();
                        System.out.print("full name: ");
                        String newFullName = in.nextLine().trim();
                        ((Admin) user).createCustomer((List<Customer>) (List<?>) users, newUsername, newPassword, newFullName);
                        break;

                    case "4":
                        System.out.print("username: ");
                        String delUsername = in.nextLine().trim();
                        ((Admin) user).deleteCustomer((List<Customer>) (List<?>) users, delUsername);
                        break;

                    case "5":
                        System.out.print("account id: ");
                        String acctId = in.nextLine().trim();
                        System.out.print("owner username: ");
                        String ownerUsername = in.nextLine().trim();
                        Customer owner = null;
                        for (User u : users)
                        {
                            if (u instanceof Customer && u.getUsername().equals(ownerUsername))
                            {
                                owner = (Customer) u;
                                break;
                            }
                        }
                        if (owner == null)
                        {
                            printMessage("no such customer");
                            break;
                        }
                        
                        System.out.print("initial balance: ");
                        double balance = Double.parseDouble(in.nextLine().trim());
                        
                        System.out.print("account type (savings/checking): ");
                        String accountType = in.nextLine().trim();
                        
                        ((Admin) user).createAccount(accounts, acctId, owner, balance, accountType);
                        break;
                    
                    case "6":
                        System.out.print("account id: ");
                        String delAcctId = in.nextLine().trim();
                        ((Admin) user).deleteAccount(accounts, delAcctId);
                        break;

                    case "7":
                        System.out.print("username: ");
                        String editUsername = in.nextLine().trim();
                        System.out.print("new password: ");
                        String editPassword = in.nextLine().trim();
                        System.out.print("new full name: ");
                        String editFullName = in.nextLine().trim();
                        ((Admin) user).editCustomer((List<Customer>) (List<?>) users, editUsername, editPassword, editFullName);
                        break;

                    case EXIT_KEY:
                        running = false;
                        break;
                        
                    default:
                        printMessage("unknown option");
                        break; 
                    }
                case "Customer":
                    switch (choice)
                    {
                    case "1":
                        printBalances(userAccounts);
                        break;

                    case "2":
                        doDeposit(in, userAccounts);
                        break;

                    case "3":
                        doWithdraw(in, userAccounts);
                        break;

                    case EXIT_KEY:
                        running = false;
                        break;

                    default:
                        printMessage("unknown option");
                        break; 
                    }
                break;
            }
            
        }

        printMessage("goodbye");
        in.close();
    }

    private static void printMenu(String customerType)
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

    private static void printBalances(List<Account> accounts)
    {
        for (Account acct : accounts)
        {
            printMessage("  " + acct.getBalance());
        }
    }

    private static void doDeposit(Scanner in, List<Account> accounts)
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

    private static void doWithdraw(Scanner in, List<Account> accounts)
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

    private static Account pickAcct(Scanner in, List<Account> accounts)
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
    private static double readAmt(Scanner in)
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
    private static void printMessage(String message)
    {
        System.out.println(message);
    }
}

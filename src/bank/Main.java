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

        Customer daniel = new Customer("daniel", "pw1234", "Daniel Palencia");

        // typed as Account, not SavingsAccount, so the menu never knows which kind it holds
        List<Account> accounts = new ArrayList<>();
        accounts.add(new SavingsAccount("SAV-001", daniel, SEED_BALANCE));

        printMessage("welcome to the bank");
        printMessage("signed in as " + daniel);
        printBalances(accounts);

        boolean running = true;

        while (running)
        {
            printMenu();
            String choice = in.nextLine().trim();

            switch (choice)
            {
                case "1":
                    printBalances(accounts);
                    break;

                case "2":
                    doDeposit(in, accounts);
                    break;

                case "3":
                    doWithdraw(in, accounts);
                    break;

                case EXIT_KEY:
                    running = false;
                    break;

                default:
                    printMessage("unknown option");
            }
        }

        printMessage("goodbye");
        in.close();
    }

    private static void printMenu()
    {
        printMessage("");
        printMessage("1) show balances");
        printMessage("2) deposit");
        printMessage("3) withdraw");
        printMessage(EXIT_KEY + ") quit");
        System.out.print("choice: ");
    }

    private static void printBalances(List<Account> accounts)
    {
        for (Account acct : accounts)
        {
            printMessage("  " + acct);
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

    private static Account pickAcct(Scanner in, List<Account> accounts)
    {
        if (accounts.size() == 1)
        {
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

    // bad input becomes 0, which the account rules already refuse. no crash, no extra branch
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

    private static void printMessage(String message)
    {
        System.out.println(message);
    }
}

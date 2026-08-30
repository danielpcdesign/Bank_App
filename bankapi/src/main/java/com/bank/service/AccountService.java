package com.bank.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.Customer;
import com.bank.repository.AccountRepository;
import com.bank.repository.CustomerRepository;

// rules that need more than one object live here: whether the customer exists, whether an
// account belongs to the customer named in the path, whether an id is already taken.
// what is always true of a single account - the balance floor, whole amounts - is enforced
// on the model, not repeated here.
@Service
public class AccountService
{
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    // two repositories rather than a call to CustomerService. opening an account touches
    // both collections, and going through another service would put a service in the
    // middle of this one's transaction for no gain.
    public AccountService(AccountRepository accountRepository, CustomerRepository customerRepository)
    {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    public List<Account> getAllAccounts()
    {
        return accountRepository.getAccounts();
    }

    public Optional<Account> getAccountById(int id)
    {
        return accountRepository.findById(id);
    }

    /*
     * Replaces the two fields a client owns. It cannot do anything else, because
     * UpdateAccountRequest cannot express anything else.
     *
     * THE ID MISMATCH CHECK IS GONE, and it is unreachable rather than removed on a whim -
     * the same thing that happened to editCustomer when it stopped binding a whole Customer.
     * The request carries no id to disagree with the path, so there is no mismatch to reject
     * and no 400 left for the controller to return.
     *
     * A METHOD THAT TOOK A WHOLE Account IS ALSO GONE, and that one was load-bearing: it
     * handed the repository a client-built object whose balance went straight to storage.
     * Taking two named fields instead is what makes writing a balance IMPOSSIBLE here rather
     * than merely something this method happens not to do.
     */
    public Optional<Account> editAccount(int id, AccountType type, double overdraftLimit)
    {
        // existence checked at repo level
        return accountRepository.editAccount(id, type, overdraftLimit);
    }

    // deleting an account also drops it from whichever customer listed it. leaving the id
    // behind would leave the customer pointing at a document that no longer exists.
    public boolean deleteAccountById(int id)
    {
        if (!accountRepository.findById(id).isPresent())
        {
            return false;
        }

        unlinkFromOwner(id);
        return accountRepository.deleteById(id);
    }

    //----------------------------------------------------------------CUSTOMER SCOPED----------------------------------------------------------------

    // empty Optional means no such customer, which the controller reads as 404. a customer
    // with no accounts is an empty list, which is a 200 - absence of the customer and
    // absence of accounts are different answers.
    public Optional<List<Account>> getAccountsForCustomer(int customerId)
    {
        Optional<Customer> customer = customerRepository.findById(customerId);
        if (customer.isEmpty())
        {
            return Optional.empty();
        }

        List<Account> accounts = new ArrayList<>();
        for (Integer accountId : customer.get().getAccountIds())
        {
            accountRepository.findById(accountId).ifPresent(accounts::add);
        }
        return Optional.of(accounts);
    }

    /*
     * THE ACCOUNT IS BUILT HERE, not bound from a request, and that is the fix for the
     * money-creation defect. The caller names an id, a type and a floor; the balance is not
     * a parameter, so there is no value a caller could send that this method would write.
     *
     * Mirrors CustomerService.addNewCustomer exactly - the controller passes the fields the
     * request record carries and the service constructs the document. The server owning the
     * fields the client does not is the same shape in both, which is why an id and a role
     * cannot be smuggled into a customer either.
     *
     * An empty Optional means the id was already taken, OR that there is no such customer.
     * The controller establishes the customer first so its 404 and this 409 stay distinct;
     * the guard below is belt and braces, because the service cannot assume it was called
     * through that controller.
     */
    public Optional<Account> openAccountForCustomer(int customerId, Integer accountId, AccountType type, double overdraftLimit)
    {
        Optional<Customer> customer = customerRepository.findById(customerId);
        if (customer.isEmpty())
        {
            return Optional.empty();
        }

        // OPENS AT ZERO. money enters through deposit, which is where the positive and
        // whole-amount rules live - an opening balance would be a way round both.
        Optional<Account> created = accountRepository.addAccount(new Account(accountId, type, 0.0, overdraftLimit));
        if (created.isEmpty())
        {
            return Optional.empty();
        }

        link(customer.get(), accountId);
        return created;
    }

    // false means this customer does not own that account - either the customer is absent,
    // or the account is not in its list. an account is only closable through its owner.
    public boolean closeAccountForCustomer(int customerId, int accountId)
    {
        Optional<Customer> customer = customerRepository.findById(customerId);
        if (customer.isEmpty())
        {
            return false;
        }

        if (!customer.get().getAccountIds().contains(accountId))
        {
            return false;
        }

        unlink(customer.get(), accountId);
        return accountRepository.deleteById(accountId);
    }

    //----------------------------------------------------------------OPERATIONS----------------------------------------------------------------

    // the model decides whether the deposit is allowed; this only persists the result.
    // an empty Optional means either no such account or the model refused - the controller
    // resolves which by establishing existence first.
    public Optional<Account> deposit(int accountId, double amount)
    {
        Optional<Account> found = accountRepository.findById(accountId);
        if (found.isEmpty())
        {
            return Optional.empty();
        }

        Account account = found.get();
        if (!account.deposit(amount))
        {
            return Optional.empty();
        }

        return Optional.of(accountRepository.save(account));
    }

    public Optional<Account> withdraw(int accountId, double amount)
    {
        Optional<Account> found = accountRepository.findById(accountId);
        if (found.isEmpty())
        {
            return Optional.empty();
        }

        Account account = found.get();
        if (!account.withdraw(amount))
        {
            return Optional.empty();
        }

        return Optional.of(accountRepository.save(account));
    }

    //----------------------------------------------------------------LINKING----------------------------------------------------------------

    /*
     * Both of these MUTATE THE STORED CUSTOMER AND SAVE IT. They must never go back to
     * building a replacement with new Customer(...).
     *
     * That is what they used to do, and it was a silent data-destroying bug: the four
     * argument constructor takes (id, username, fullName, accountIds) and chains to defaults
     * for the two it omits, so every link and unlink wrote password=null and role=CUSTOMER
     * over whatever was there. Opening an account locked the owner out permanently and
     * demoted them if they were an admin, while returning 201. See Customer.setAccountIds.
     *
     * The customer passed in here always came from customerRepository.findById, so it
     * carries every field. Changing one and saving cannot lose the rest.
     */
    private void link(Customer customer, Integer accountId)
    {
        List<Integer> ids = new ArrayList<>(customer.getAccountIds());
        if (!ids.contains(accountId))
        {
            ids.add(accountId);
        }
        customer.setAccountIds(ids);
        customerRepository.save(customer);
    }

    private void unlink(Customer customer, Integer accountId)
    {
        List<Integer> ids = new ArrayList<>(customer.getAccountIds());
        ids.remove(accountId);
        customer.setAccountIds(ids);
        customerRepository.save(customer);
    }

    // used by the unscoped delete, which is not told who the owner is.
    private void unlinkFromOwner(int accountId)
    {
        for (Customer customer : customerRepository.getCustomers())
        {
            if (customer.getAccountIds().contains(accountId))
            {
                unlink(customer, accountId);
                return;
            }
        }
    }
}

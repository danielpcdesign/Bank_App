package com.bank.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bank.model.Account;
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

    // unique _id. an empty Optional back means the insert was rejected as a duplicate
    public boolean addNewAccount(Account account)
    {
        return accountRepository.addAccount(account).isPresent();
    }

    public Optional<Account> editAccount(int id, Account data)
    {
        // Objects.equals, not !=, so a null body id is a mismatch rather than an NPE.
        if (!Objects.equals(data.getId(), id))
        {
            return Optional.empty(); // id mismatch
        }

        // existence checked at repo level
        return accountRepository.editAccount(id, data);
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

    // false means the id was already taken. the controller has already established that the
    // customer exists, so the guard here is belt and braces rather than the reported case -
    // but the service cannot assume it was called through that controller.
    public boolean openAccountForCustomer(int customerId, Account account)
    {
        Optional<Customer> customer = customerRepository.findById(customerId);
        if (customer.isEmpty())
        {
            return false;
        }

        if (!addNewAccount(account))
        {
            return false;
        }

        link(customer.get(), account.getId());
        return true;
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

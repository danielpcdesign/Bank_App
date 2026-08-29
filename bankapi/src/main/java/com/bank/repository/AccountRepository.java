package com.bank.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.bank.model.Account;
import com.bank.model.AccountType;

// the same shape as CustomerRepository, against the accounts collection. storage is atlas;
// this wrapper exists so the service never sees a spring data type.
@Repository
public class AccountRepository
{
    private final AccountMongoRepository mongo;

    public AccountRepository(AccountMongoRepository mongo)
    {
        this.mongo = mongo;
        seedIfEmpty();
    }

    // only write the three seed accounts when the collection is empty. ids line up with the
    // accountIds on the three seeded customers.
    private void seedIfEmpty()
    {
        if (mongo.count() == 0)
        {
            mongo.save(new Account(101, AccountType.SAVINGS, 500.0, 0.0));
            mongo.save(new Account(102, AccountType.CHECKING, 250.0, -100.0));
            mongo.save(new Account(103, AccountType.SAVINGS, 1000.0, 0.0));
        }
    }

    // findAll already builds a fresh list, so no defensive copy is needed here
    public List<Account> getAccounts()
    {
        return mongo.findAll();
    }

    // int autoboxes to the Integer the interface was declared with
    public Optional<Account> findById(int id)
    {
        return mongo.findById(id);
    }

    //catch duplicate and return empty optional, otherwise return the saved account in an optional.
    public Optional<Account> addAccount(Account account)
    {
        Optional<Account> result;
        try
        {
            result = Optional.of(mongo.insert(account));
        }
        catch (DuplicateKeyException e)
        {
            return Optional.empty();
        }
        return result;
    }

    // deleteById returns void, so we have to check for existence first to know if it was deleted or not.
    public boolean deleteById(int id)
    {
        if (!mongo.existsById(id))
        {
            return false;
        }

        mongo.deleteById(id);
        return true;
    }

    // empty Optional when no document has that id.
    // otherwise build the replacement from data using path as key
    public Optional<Account> editAccount(int id, Account data)
    {
        // existence is checked on the path id, and the replacement is keyed by the path id.
        if (!mongo.existsById(id))
        {
            return Optional.empty();
        }
        // Rebuilt rather than mutated, and DELIBERATELY so - the opposite choice to
        // CustomerRepository.editCustomer, for a reason worth stating.
        //
        // Customer is mutated because rebuilding it dropped fields a constructor did not
        // take. Account cannot be mutated: it exposes no setter for balance on purpose,
        // because a setter would be a way to move money that skips deposit and withdraw.
        // Going through the constructor also re-applies the savings coercion, so a savings
        // account can never be edited into having an overdraft.
        //
        // This names all four fields, which is currently every field Account has. It is the
        // one place a fifth would be silently dropped, so add it here at the same time.
        return Optional.of(mongo.save(new Account(id, data.getType(), data.getBalance(), data.getOverdraftLimit())));
    }

    // deposit and withdraw mutate the account in place, so the changed object is written back
    // as it stands rather than rebuilt from a body. no existence check - the service has
    // already resolved the account it hands over.
    public Account save(Account account)
    {
        return mongo.save(account);
    }
}

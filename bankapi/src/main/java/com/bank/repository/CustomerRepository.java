package com.bank.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.bank.model.Customer;
import com.bank.model.Role;

// same five methods as before. the storage behind them is now atlas, not a map.
// nothing else changes
@Repository
public class CustomerRepository
{
    private final CustomerMongoRepository mongo;

    public CustomerRepository(CustomerMongoRepository mongo)
    {
        this.mongo = mongo;
        seedIfEmpty();
    }

    // only write the seed customers when the collection is empty. one of them is an admin:
    // a role filter nothing distinguishes is a filter nobody can test.
    private void seedIfEmpty()
    {
        if (mongo.count() == 0)
        {
            // THESE CREDENTIALS ARE IN SOURCE CONTROL. Anyone with the repository has every
            // seeded password, and they are stored in plaintext besides (see Customer.password).
            // Acceptable here only because this is a training deployment holding no real
            // customer data and no real money. It would be a serious incident anywhere else,
            // and none of these accounts should ever exist in an environment that matters.
            //
            // account ids match the three seeded in AccountRepository, so the seeded data
            // is coherent from either end of the relationship.
            mongo.save(new Customer(1, "alice", "Alice Smith", List.of(101), Role.CUSTOMER, "alice123"));
            mongo.save(new Customer(2, "bob", "Bob Jones", List.of(102), Role.CUSTOMER, "bob123"));
            mongo.save(new Customer(3, "carol", "Carol Johnson", List.of(103), Role.CUSTOMER, "carol123"));
            // exactly one admin, and a dedicated one rather than a promoted customer - the
            // console app seeds it the same way, and an admin owning accounts would muddle
            // which of the two roles the seed is demonstrating.
            mongo.save(new Customer(4, "admin", "Admin User", List.of(), Role.ADMIN, "admin123"));
        }
    }

    // findAll already builds a fresh list, so no defensive copy is needed here
    public List<Customer> getCustomers()
    {
        return mongo.findAll();
    }

    /*
     * The next free customer id. The create endpoint has no field for one, so the SERVER assigns it.
     *
     * Named after the convention in CLAUDE.md, which gives nextAcctId as its example of a
     * short technical method name. NOTE: the phase 1 console app has no id-assignment scheme
     * to copy - its customers are keyed by username and carry no numeric id at all, and its
     * account ids are typed in by the admin. So this is new here rather than carried over.
     *
     * Highest existing id plus one, rather than a count: a count reuses the id of a deleted
     * customer and would collide with the record that still holds it. Reading the whole
     * collection is acceptable at this size and consistent with the rest of this class,
     * which does the same for getCustomers().
     *
     * WHY MONGO DOES NOT DO THIS FOR US. It generates an _id only when the field is absent,
     * and what it generates is an ObjectId - a 12-byte value, not an integer. Customer.id is
     * an Integer and the API exposes it in paths like /api/v1/customers/5, so a generated
     * ObjectId would change both the model's type and every URL. Max-plus-one is therefore
     * something this application computes; there is no server-side counter to delegate to.
     *
     * NOT race-free. Two creates arriving together can read the same maximum, and the
     * loser's insert is then rejected by the unique _id index - which addCustomer already
     * turns into an empty Optional, so the caller gets a 409 rather than a corrupt write.
     * The index is what actually protects the data; this only picks a likely-free number.
     *
     * The atomic alternative is a counters collection incremented with findAndModify, which
     * hands back a reserved number in one round trip and cannot collide. Deliberately not
     * built: it adds a second collection and a write on every create to close a race that
     * the unique index already fails safely, at a scale where two simultaneous registrations
     * is not a realistic load. Revisit if creates ever become concurrent in earnest.
     */
    public int nextCustomerId()
    {
        return mongo.findAll().stream()
            .map(Customer::getId)
            .filter(id -> id != null)
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0) + 1;
    }

    // sign-in resolves a customer by the only field a person actually types. username is not
    // the @Id, so this is a derived query rather than a findById.
    public Optional<Customer> findByUsername(String username)
    {
        return mongo.findByUsername(username);
    }

    // the filter runs in the database rather than over an in-memory list. it is the same
    // answer either way today, with four documents; it stops being the same answer later.
    public List<Customer> getCustomersByRole(Role role)
    {
        return mongo.findByRole(role);
    }

    // int autoboxes to the Integer the interface was declared with
    public Optional<Customer> findById(int id)
    {
        return mongo.findById(id);
    }

    //catch duplicate and return empty optional, otherwise return the saved customer in an optional.
    public Optional<Customer> addCustomer(Customer customer)
    {
        Optional<Customer> result; 
        try
        {
            result = Optional.of(mongo.insert(customer));
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
    // used when only the account list changed, so there is nothing to rebuild from a body.
    // opening or closing an account writes the owning customer back through here.
    public Customer save(Customer customer)
    {
        return mongo.save(customer);
    }

    // empty Optional when no document has that id.
    // otherwise build the replacement from data using path as key
    public Optional<Customer> editCustomer(int id, String username, String fullName)
    {
        Optional<Customer> stored = mongo.findById(id);
        if (stored.isEmpty())
        {
            return Optional.empty();
        }

        /*
         * THE RULE, and it is now the method signature rather than a comment anyone has to
         * obey:
         *
         *   PUT replaces the fields a client both READS and OWNS - username and fullName.
         *
         * There used to be a list here of the four fields this method must be careful NOT to
         * write, and being careful is exactly what kept failing. The caller cannot pass them
         * any more: UpdateCustomerRequest has no id, role, password or accountIds, so none of
         * them reaches this method to be mishandled.
         *
         * Why each is excluded, kept because the reasoning is still what justifies the shape:
         *
         *   PASSWORD fails the READ half - WRITE_ONLY, so a client is never given one and has
         *   nothing to send back. Changing it needs its own operation, which does not exist yet.
         *   ID fails the OWN half - the path names the record.
         *   ACCOUNTIDS fails the OWN half - maintained by the account endpoints. Honouring an
         *   echoed list would let a stale copy unlink an account opened moments earlier.
         *   ROLE fails the OWN half - assigned at seed time and by nothing else.
         *
         * MUTATE THE STORED DOCUMENT, never rebuild it. A field this method does not mention
         * keeps the value it already had, which is why dropping fields from the request is
         * safe here and would not have been under a constructor rebuild.
         */
        Customer customer = stored.get();
        customer.setUsername(username);
        customer.setFullName(fullName);

        return Optional.of(mongo.save(customer));

    }
}

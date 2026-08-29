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
    public Optional<Customer> editCustomer(int id, Customer data)
    {
        // findById rather than existsById: the stored password is needed below, and fetching
        // the document is the only way to keep it.
        Optional<Customer> stored = mongo.findById(id);
        if (stored.isEmpty())
        {
            return Optional.empty();
        }

        /*
         * THE RULE FOR THIS METHOD, and it is one rule rather than two exceptions:
         *
         *   PUT fully replaces the fields a client both READS and OWNS.
         *
         * Two fields fail that test, each for a different half of it.
         *
         * PASSWORD fails the READ half. It is WRITE_ONLY - it goes in and never comes out -
         * so a client editing a username is never given one and has nothing to send back.
         * Requiring it made every edit a 400 (verified, not assumed); writing whatever
         * arrived would blank the password of any client that did not invent a value.
         *
         * ACCOUNTIDS fails the OWN half. A client can read it, so it can echo it back, and
         * the front end does exactly that - the list rides through an edit form untouched.
         * But ownership is maintained by the ACCOUNT endpoints, which add and remove ids as
         * accounts are opened and closed. Honouring the echo means two endpoints own one
         * fact, and this one has no idea what it is overwriting: if an account is opened or
         * closed between the client's GET and its PUT, the stale list is written back and
         * the newer account is silently unlinked. That window is short on an edit form and
         * much wider for anything that holds a customer object for a while.
         *
         * Both are therefore taken from the stored document. Supplying a password still
         * changes it; supplying accountIds does nothing at all, because this is not the
         * endpoint that owns them.
         */
        // MUTATE THE STORED CUSTOMER, never rebuild it. A replacement built with a
        // constructor silently loses whatever that constructor does not take - the bug that
        // destroyed passwords through the account endpoints. Setting only the fields the
        // client owns means a field added tomorrow is left ALONE by default, which is the
        // safe failure, rather than quietly reset to a default.
        Customer customer = stored.get();
        customer.setUsername(data.getUsername());
        customer.setFullName(data.getFullName());

        // role is a field the client both reads and owns, so it is replaced. NOTHING CHECKS
        // WHO IS ASKING: any caller may PUT role: ADMIN and become an admin. Decided, not
        // overlooked - the admin-only restriction lives in the FRONT END alone and is
        // bypassed by curl or any client that is not our UI. Real enforcement needs an
        // authenticated principal, in phase 10.
        //
        // A dedicated PATCH /customers/{id}/role was considered and is not needed: it was
        // proposed because a role change used to drag a password and an account list through
        // the request and could lose either, and neither is true any more.
        customer.setRole(data.getRole());

        // password is set ONLY when one was supplied. It fails the "can the client read it"
        // half of the rule below - it is WRITE_ONLY, so a client editing a username is never
        // given one and has nothing to send back. Requiring it made every edit a 400.
        if (data.getPassword() != null && !data.getPassword().isBlank())
        {
            customer.setPassword(data.getPassword());
        }

        // accountIds is deliberately NOT touched. It fails the "does the client own it" half:
        // readable and echoable, but maintained by the ACCOUNT endpoints. Honouring the echo
        // means two endpoints own one fact, and a list that went stale between the client's
        // GET and this PUT would silently unlink the newer account.
        return Optional.of(mongo.save(customer));
    }
}

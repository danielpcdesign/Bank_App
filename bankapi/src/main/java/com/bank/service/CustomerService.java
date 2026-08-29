package com.bank.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bank.model.Customer;
import com.bank.model.Role;
import com.bank.repository.CustomerRepository;

@Service
public class CustomerService
{
    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository)
    {
        this.customerRepository = customerRepository;
    }

    public List<Customer> getAllCustomers()
    {
        return customerRepository.getCustomers();
    }

    /*
     * CREDENTIAL COMPARISON, NOT AUTHENTICATION. It answers one question - "do these two
     * strings match the stored pair?" - and establishes nothing that outlives the reply.
     * No session is created, no token is issued, nothing is remembered. The very next
     * request arrives as anonymous as this one did, so a caller who never signs in can
     * still reach every endpoint. Phase 10 replaces this with Spring Security.
     *
     * The comparison is equals() on a plaintext field (see Customer.password), and it is
     * not constant-time. That is a real weakness and it is deferred with the hashing.
     *
     * ONE ANSWER FOR BOTH FAILURES, deliberately. An unknown username and a wrong password
     * both return an empty Optional, and the caller cannot tell which it hit. Separating
     * them would turn this endpoint into a username oracle: try a list, keep the ones that
     * say "wrong password", and you have an account inventory before guessing anything.
     * Same reasoning already recorded for keeping the 409 body terse.
     */
    public Optional<Customer> signIn(String username, String password)
    {
        Optional<Customer> found = customerRepository.findByUsername(username);
        if (found.isEmpty())
        {
            return Optional.empty();
        }

        String stored = found.get().getPassword();

        /*
         * STORED PASSWORD FIRST, AND NULL-CHECKED. Calling equals() on it directly threw an
         * NPE for any customer whose password was missing, so sign-in answered 500 where it
         * owed a 401 - which is how the link/unlink data loss was first noticed, as a server
         * error rather than a rejection.
         *
         * A missing stored password is not exotic: records seeded before the field existed,
         * a partial write, or any future migration can produce one. None of those is a
         * server fault, and none of them is a way in either - the null is rejected outright
         * rather than compared, so a caller sending null cannot match a stored null.
         *
         * Still one answer for every failure. Unknown username, missing password and wrong
         * password are indistinguishable to the caller.
         */
        if (stored == null || !stored.equals(password))
        {
            return Optional.empty();
        }

        return found;
    }

    // filtering by role is a query, not a permission. this returns admins to anyone who
    // asks - see Role. nothing here decides what a caller is allowed to do.
    public List<Customer> getCustomersByRole(Role role)
    {
        return customerRepository.getCustomersByRole(role);
    }

    public Optional<Customer> getCustomerById(int id)
    {
        return customerRepository.findById(id);
    }

    // unique _id. 
    // an empty Optional back
    // means the insert was rejected as a duplicate
    /*
     * THE ONLY WAY A CUSTOMER IS CREATED through the API. The caller supplies a username, a
     * password and a full name; it cannot supply an id or a role, because
     * CreateCustomerRequest has no field for either. The id is assigned here and the role is
     * always CUSTOMER.
     *
     * This replaced two methods. There used to be an administrative create taking a whole
     * Customer - id and role included, from any anonymous caller - alongside a separate
     * self-service registration. With exactly one admin, seeded directly through the
     * repository rather than over HTTP, nothing ever needed an endpoint that sets a role, so
     * the admin-shaped create preserved only a hole. Merging them makes the guarantee
     * universal rather than true of one route.
     *
     * WHAT IS GUARANTEED, now the complete statement rather than half of it:
     *
     *   NO CALLER CAN SET A ROLE ANYWHERE IN THIS API. There is nowhere in any create
     *   request to say so, and editCustomer no longer writes role either. Roles are assigned
     *   at seed time and by nothing else, for every caller including curl.
     *
     * This block used to record the opposite - that closing the create path left PUT open,
     * so a caller could create a customer and then promote itself. True when written, false
     * now. Cost of the change: promoting a second admin needs a database edit or a code
     * change, and there is deliberately no endpoint for it.
     *
     * Empty Optional means the username is taken. That is a genuine conflict now: a caller
     * cannot pick a colliding id, so the username is the only thing two creates can fight over.
     */
    public Optional<Customer> addNewCustomer(String username, String password, String fullName)
    {
        // username uniqueness is enforced HERE, not by the database. only _id carries a
        // unique index, so nothing below this line would stop a second "alice" - and two
        // customers sharing a username make findByUsername throw, which would turn sign-in
        // into a 500 for both of them. a rule needing more than one document is service work.
        if (customerRepository.findByUsername(username).isPresent())
        {
            return Optional.empty();
        }

        Customer customer = new Customer(
            customerRepository.nextCustomerId(),
            username,
            fullName,
            List.of(),
            Role.CUSTOMER,
            password);

        return customerRepository.addCustomer(customer);
    }

    /*
     * THE ONE PROTECTION IN THIS APPLICATION THAT IS NOT COSMETIC.
     *
     * Almost everything else guarding admin behaviour is gated in the front end only, and
     * curl walks straight past it - the role on PUT is the clearest case, and that is a
     * decided position rather than an oversight. This is different in kind, and the reason
     * is worth being precise about: "there must always be at least one admin" is an
     * invariant about the STATE OF THE SYSTEM, not a rule about who is asking. Evaluating
     * it needs no principal, no session and no credential, so it holds for every caller
     * including curl. It is the same shape as the overdraft floor on Account, which refuses
     * a withdrawal without caring who requested it.
     *
     * THE INVARIANT IS "AT LEAST ONE ADMIN MUST REMAIN", not "an admin row must not be
     * deleted". Two operations can breach it and both are guarded by isLastAdmin below:
     * deleting the last admin, and demoting the last admin through editCustomer. They reach
     * the same end state - nobody can administer the system, and no route exists to make a
     * new admin, because the create endpoint cannot express a role. Recovery would mean
     * editing the database by hand, or emptying the customers collection and restarting so
     * the seed runs again.
     *
     * Deliberately scoped to the LAST admin. A second admin, if one ever existed, could be
     * deleted or demoted freely - the invariant is about the count that remains, not about
     * the target being an admin.
     *
     * false here means REFUSED, not "not found". The controller establishes existence first,
     * exactly as it does for deposit and withdraw, and reports this as 409: the failure is
     * state-dependent, and the same request succeeds once another admin exists.
     */
    public boolean deleteCustomerById(int id)
    {
        if (isLastAdmin(id))
        {
            return false;
        }

        return customerRepository.deleteById(id);
    }

    // the rule, stated once. both callers ask the same question of the same data.
    private boolean isLastAdmin(int id)
    {
        List<Customer> admins = customerRepository.getCustomersByRole(Role.ADMIN);
        return admins.size() == 1 && Objects.equals(admins.get(0).getId(), id);
    }

    /*
     * Updates the two fields a client owns. It cannot do anything else, because
     * UpdateCustomerRequest cannot express anything else.
     *
     * THE ID MISMATCH CHECK IS GONE, and it is unreachable rather than removed on a whim:
     * the request has no id to disagree with the path, so there is no mismatch to reject.
     * The 400 it produced is likewise gone from the controller.
     *
     * THE DEMOTION GUARD IS GONE TOO, and this is the better outcome of the two. It used to
     * refuse demoting the last admin, then became unreachable when PUT stopped writing role,
     * and is now IMPOSSIBLE TO EXPRESS - there is no role parameter to demote anyone with.
     * A guard that cannot be bypassed because the operation does not exist beats a guard
     * that has to run correctly every time. The DELETE half of that invariant is still live
     * and still enforced in deleteCustomerById, which is where isLastAdmin is now used.
     */
    public Optional<Customer> editCustomer(int id, String username, String fullName)
    {
        return customerRepository.editCustomer(id, username, fullName);

    }
}
package com.bank.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.bank.model.Customer;
import com.bank.model.Role;

// CustomerMongoRepository is the mock, so nothing here touches atlas. what is under test
// is the wrapper's own branching: the seed guard, the duplicate catch, and the two
// existence guards that turn "absent" into an empty Optional.
@ExtendWith(MockitoExtension.class)
class CustomerRepositoryTest
{
    @Mock
    private CustomerMongoRepository mongo;

    // deliberately NOT @InjectMocks. the constructor calls seedIfEmpty(), so construction
    // is itself a behaviour under test - and in the other tests it would record three
    // save() calls before the test body ran, corrupting every verify().
    private CustomerRepository repositoryWithPopulatedCollection()
    {
        when(mongo.count()).thenReturn(3L);
        return new CustomerRepository(mongo);
    }

    // four now, not three: the seed gained a dedicated admin so that filtering by role has
    // something to find. the count is the assertion, so it moves with the seed.
    @Test
    void seedsFourCustomers_whenTheCollectionIsEmpty()
    {
        when(mongo.count()).thenReturn(0L);

        new CustomerRepository(mongo);

        verify(mongo, times(4)).save(any(Customer.class));
    }

    // the behaviour that stopped deletes being undone by a restart. before seeding
    // was guarded, every boot re-inserted customers 1-3.
    @Test
    void doesNotSeed_whenTheCollectionAlreadyHasDocuments()
    {
        when(mongo.count()).thenReturn(3L);

        new CustomerRepository(mongo);

        verify(mongo, never()).save(any(Customer.class));
    }

    @Test
    void addCustomer_returnsTheInsertedCustomer_onSuccess()
    {
        CustomerRepository repository = repositoryWithPopulatedCollection();
        Customer customer = new Customer(4, "nina", "Nina Cortez");
        when(mongo.insert(customer)).thenReturn(customer);

        assertThat(repository.addCustomer(customer)).contains(customer);
    }

    // the catch block that makes 409 possible. the unique index on _id rejects the write,
    // spring translates it to its own store-neutral DuplicateKeyException, and the
    // repository converts that into an empty Optional for the service to read.
    @Test
    void addCustomer_returnsEmpty_whenTheInsertHitsADuplicateKey()
    {
        CustomerRepository repository = repositoryWithPopulatedCollection();
        Customer customer = new Customer(1, "dupe", "Duplicate Id");
        when(mongo.insert(customer)).thenThrow(new DuplicateKeyException("_id 1 already exists"));

        assertThat(repository.addCustomer(customer)).isEmpty();
    }

    @Test
    void deleteById_reportsFalseAndDeletesNothing_whenTheIdIsAbsent()
    {
        CustomerRepository repository = repositoryWithPopulatedCollection();
        when(mongo.existsById(99)).thenReturn(false);

        assertThat(repository.deleteById(99)).isFalse();

        verify(mongo, never()).deleteById(any());
    }

    @Test
    void deleteById_reportsTrue_whenTheIdExists()
    {
        CustomerRepository repository = repositoryWithPopulatedCollection();
        when(mongo.existsById(2)).thenReturn(true);

        assertThat(repository.deleteById(2)).isTrue();

        verify(mongo).deleteById(2);
    }

    @Test
    void editCustomer_returnsEmptyAndSavesNothing_whenTheIdIsAbsent()
    {
        CustomerRepository repository = repositoryWithPopulatedCollection();
        when(mongo.findById(99)).thenReturn(Optional.empty());

        assertThat(repository.editCustomer(99, "ghost", "Ghost User")).isEmpty();

        verify(mongo, never()).save(any(Customer.class));
    }

    // the invariant argued over at length in phase 2: the stored document is keyed by the
    // PATH id, never by whatever the client put in the body. an ArgumentCaptor is the only
    // way to assert on the object handed to a collaborator rather than on a return value.

    @Test
    void findById_passesAbsenceStraightThrough()
    {
        CustomerRepository repository = repositoryWithPopulatedCollection();
        when(mongo.findById(99)).thenReturn(Optional.empty());

        assertThat(repository.findById(99)).isEmpty();
    }

    // the point of seeding an admin is that the role filter has something to find. if this
    // ever goes back to four customers all in one role, the filter is untestable again.
    @Test
    void seedIncludesExactlyOneAdmin()
    {
        when(mongo.count()).thenReturn(0L);

        new CustomerRepository(mongo);

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(mongo, times(4)).save(saved.capture());

        assertThat(saved.getAllValues())
            .filteredOn(customer -> customer.getRole() == Role.ADMIN)
            .singleElement()
            .extracting(Customer::getUsername)
            .isEqualTo("admin");
    }

    @Test
    void getCustomersByRole_handsTheRoleToTheDerivedQuery()
    {
        CustomerRepository repository = repositoryWithPopulatedCollection();
        Customer admin = new Customer(4, "admin", "Admin User", List.of(), Role.ADMIN);
        when(mongo.findByRole(Role.ADMIN)).thenReturn(List.of(admin));

        assertThat(repository.getCustomersByRole(Role.ADMIN)).containsExactly(admin);
    }

    /*
     * THE PAIR THAT PINS THE ROLE RULE. Between them they cover the only two things a body
     * can do about role - carry one, or not - and both must leave the stored value alone.
     *
     * This one is the new rule: a body asking for ADMIN is ignored. It replaced a test that
     * asserted the opposite and had been passing for the wrong reason - stored and body were
     * both ADMIN, so it could not have told the difference either way.
     */

    /*
     * And this one is the worry that goes with it: "we stopped writing a field" is the exact
     * sentence that described the data-loss bug, so it has to be shown NOT to be that.
     *
     * It is not, because this method mutates the stored document rather than rebuilding it.
     * An unmentioned field keeps the value it already had - there is no constructor default
     * for it to fall back to. Not writing role is one less field to set, not one more to
     * remember. Here the stored admin sends a body with no role at all and stays an admin.
     */

    // the one admin, with the credentials the front end signs in with. if these drift the
    // seeded admin becomes unreachable and nobody can reach an admin view at all.
    @Test
    void seedsTheAdminWithTheExpectedCredentials()
    {
        when(mongo.count()).thenReturn(0L);

        new CustomerRepository(mongo);

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(mongo, times(4)).save(saved.capture());

        Customer admin = saved.getAllValues().stream()
            .filter(customer -> customer.getRole() == Role.ADMIN)
            .findFirst()
            .orElseThrow();

        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getPassword()).isEqualTo("admin123");
    }

    // every seeded customer needs a password now that the field is required, or the three
    // non-admin seeds could never sign in.
    @Test
    void seedsEveryCustomerWithAPassword()
    {
        when(mongo.count()).thenReturn(0L);

        new CustomerRepository(mongo);

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(mongo, times(4)).save(saved.capture());

        assertThat(saved.getAllValues())
            .allSatisfy(customer -> assertThat(customer.getPassword()).isNotBlank());
    }

    @Test
    void findByUsername_passesAbsenceStraightThrough()
    {
        CustomerRepository repository = repositoryWithPopulatedCollection();
        when(mongo.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThat(repository.findByUsername("ghost")).isEmpty();
    }

    // password is carried from the body like every other field, which is what makes PUT a
    // genuine full replacement rather than a partial one.

    // the fix for the deadlock between WRITE_ONLY and full-replacement PUT. a client is never
    // sent a password, so a body that omits one must leave the stored value alone rather than
    // blanking it. without this, editing a username locks the customer out permanently.

    // a blank string is treated as "not supplied" too. an empty form field must not be able
    // to erase a password by accident.

    // accountIds is now taken from the stored document, never from the body. a client
    // editing a username and saying nothing about accounts keeps its accounts.

    /*
     * THE LOST UPDATE THIS PREVENTS, and the reason omission was never the real risk.
     *
     * The front end echoes accountIds: it fetches a customer, carries the list through a
     * form untouched, and sends it back. So the dangerous body is not the one that omits the
     * list - it is the one that faithfully repeats a list that has since gone stale. If an
     * account is opened between that GET and this PUT, honouring the body would unlink it.
     *
     * Here the stored customer has account 102 (opened moments ago) and the body still
     * carries the older [101] it was handed. The write must keep 102.
     */

    // the endpoint that DOES own the list still changes it. preserving on PUT must not turn
    // accountIds into a field nothing can ever write.
    @Test
    void save_stillWritesAChangedAccountList()
    {
        CustomerRepository repository = repositoryWithPopulatedCollection();
        when(mongo.save(any(Customer.class))).thenAnswer(call -> call.getArgument(0));

        repository.save(new Customer(1, "alice", "Alice Smith", List.of(101, 104), Role.CUSTOMER, "alice123"));

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(mongo).save(saved.capture());
        assertThat(saved.getValue().getAccountIds()).containsExactly(101, 104);
    }

    // the two fields PUT does replace, asserted alongside the four it does not. a rename
    // must still work, and must still leave role, password and accounts exactly as stored.

    //----------------------------------------------------------------SERVER ASSIGNED IDS----------------------------------------------------------------

    @Test
    void nextCustomerId_isOneAboveTheHighestExistingId()
    {
        CustomerRepository repository = repositoryWithPopulatedCollection();
        when(mongo.findAll()).thenReturn(List.of(
            new Customer(1, "alice", "Alice Smith"),
            new Customer(4, "admin", "Admin User")));

        assertThat(repository.nextCustomerId()).isEqualTo(5);
    }

    // highest-plus-one rather than count-plus-one. with 1 and 4 stored, a count would
    // propose 3 - free here, but it proposes 4 the moment id 3 exists and 1 has been
    // deleted, colliding with a live record.
    @Test
    void nextCustomerId_doesNotReuseAnIdFreedByADelete()
    {
        CustomerRepository repository = repositoryWithPopulatedCollection();
        when(mongo.findAll()).thenReturn(List.of(
            new Customer(2, "bob", "Bob Jones"),
            new Customer(3, "carol", "Carol Johnson")));

        // a count would say 3, which is taken
        assertThat(repository.nextCustomerId()).isEqualTo(4);
    }

    @Test
    void nextCustomerId_startsAtOne_whenNoCustomersExist()
    {
        CustomerRepository repository = repositoryWithPopulatedCollection();
        when(mongo.findAll()).thenReturn(List.of());

        assertThat(repository.nextCustomerId()).isEqualTo(1);
    }

    /*
     * ONE TEST NOW COVERS WHAT NINE USED TO, and the shrinkage is the point rather than a
     * loss of coverage.
     *
     * Those nine each pinned a field this method had to be careful not to write - role from
     * the body, a stale accountIds echo, a blank password, an id mismatch. None of them can
     * be expressed any more: UpdateCustomerRequest carries username and fullName and nothing
     * else, so there is no id, role, password or accountIds arriving to be mishandled. Cases
     * that cannot occur do not need tests; what needs a test is that the two fields which DO
     * arrive are written, and that everything else survives untouched.
     */
    @Test
    void editCustomer_replacesUsernameAndFullName_andLeavesEveryServerOwnedFieldAlone()
    {
        CustomerRepository repository = repositoryWithPopulatedCollection();
        when(mongo.findById(4)).thenReturn(Optional.of(
            new Customer(4, "admin", "Admin User", List.of(101), Role.ADMIN, "admin123")));
        when(mongo.save(any(Customer.class))).thenAnswer(call -> call.getArgument(0));

        repository.editCustomer(4, "admin2", "Renamed Admin");

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(mongo).save(saved.capture());

        // the two fields a client owns
        assertThat(saved.getValue().getUsername()).isEqualTo("admin2");
        assertThat(saved.getValue().getFullName()).isEqualTo("Renamed Admin");

        // and the four it does not, none of which this method can even see
        assertThat(saved.getValue().getId()).isEqualTo(4);
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getPassword()).isEqualTo("admin123");
        assertThat(saved.getValue().getAccountIds()).containsExactly(101);
    }
}

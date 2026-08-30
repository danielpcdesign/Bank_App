package com.bank.service;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bank.model.Customer;
import com.bank.model.Role;
import com.bank.repository.CustomerRepository;

// no spring, no atlas. the repository is a fake, so these test the service's own logic
// and nothing else. that is only possible because CustomerService takes its dependency
// through the constructor.
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest
{
    @Mock
    private CustomerRepository repository;

    @InjectMocks
    private CustomerService service;



    // the mismatch is rejected by the service itself, so storage is never consulted.
    // verifyNoInteractions is what proves the short circuit - an assertion on the
    // return value alone would still pass if the service called the repository first.

    // a regression test for the bug fixed at the end of phase 2. `data.getId() != id`
    // unboxed the Integer, so a null body id was an NPE rather than a rejection.
    // @Valid stops that at the controller - and a unit test has no controller in front of it.

    // stub repository.editCustomer(2, body) to return the updated customer,
    // assert the service hands back exactly what the repository gave it.

    @Test
    void deleteCustomerById_reportsWhatTheRepositoryReported()
    {
        when(repository.deleteById(2)).thenReturn(true);
        assertThat(service.deleteCustomerById(2)).isTrue();

        when(repository.deleteById(99)).thenReturn(false);
        assertThat(service.deleteCustomerById(99)).isFalse();
    }
    // testing wiring purely

    // a filter, not a permission check. the service hands back admins to whoever asked,
    // because there is no caller identity here to decide otherwise.
    @Test
    void getCustomersByRole_reportsWhatTheRepositoryReturned()
    {
        Customer admin = new Customer(4, "admin", "Admin User", List.of(), Role.ADMIN);
        when(repository.getCustomersByRole(Role.ADMIN)).thenReturn(List.of(admin));

        assertThat(service.getCustomersByRole(Role.ADMIN)).containsExactly(admin);
    }

    //----------------------------------------------------------------SIGN IN----------------------------------------------------------------

    @Test
    void signIn_returnsTheCustomer_whenTheUsernameAndPasswordMatch()
    {
        Customer alice = new Customer(1, "alice", "Alice Smith", List.of(), Role.CUSTOMER, "alice123");
        when(repository.findByUsername("alice")).thenReturn(Optional.of(alice));

        assertThat(service.signIn("alice", "alice123")).contains(alice);
    }

    // the two failures below must be INDISTINGUISHABLE to the caller. asserting they return
    // the same thing is the test - if one day one of them carried a reason, this would still
    // pass on the happy path and the enumeration hole would ship unnoticed.
    @Test
    void signIn_returnsEmpty_whenNoSuchUsername()
    {
        when(repository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThat(service.signIn("ghost", "anything")).isEmpty();
    }

    @Test
    void signIn_returnsEmpty_whenThePasswordIsWrong()
    {
        Customer alice = new Customer(1, "alice", "Alice Smith", List.of(), Role.CUSTOMER, "alice123");
        when(repository.findByUsername("alice")).thenReturn(Optional.of(alice));

        assertThat(service.signIn("alice", "wrong")).isEmpty();
    }

    @Test
    void signIn_reportsTheAdminRole_soTheUiCanChooseADashboard()
    {
        Customer admin = new Customer(4, "admin", "Admin User", List.of(), Role.ADMIN, "admin123");
        when(repository.findByUsername("admin")).thenReturn(Optional.of(admin));

        assertThat(service.signIn("admin", "admin123"))
            .get()
            .extracting(Customer::getRole)
            .isEqualTo(Role.ADMIN);
    }

    /*
     * A stored null must be a failed match, not a 500. This threw an NPE before the
     * comparison was reordered, which is how the account-linking data loss surfaced: the
     * password had been destroyed, and the next sign-in reported a server error instead of
     * rejecting the attempt.
     *
     * The customer can arrive without a password from more than one direction - records
     * seeded before the field existed, a partial write, a future migration - and none of
     * those is a fault of the caller.
     */
    @Test
    void signIn_returnsEmptyRatherThanThrowing_whenTheStoredPasswordIsNull()
    {
        Customer noPassword = new Customer(9, "ghost", "No Password", List.of(), Role.CUSTOMER, null);
        when(repository.findByUsername("ghost")).thenReturn(Optional.of(noPassword));

        assertThat(service.signIn("ghost", "anything")).isEmpty();
    }

    // and a null stored password must not become a way in for a caller who also sends null.
    // rejected outright rather than compared, so null never equals null here.
    @Test
    void signIn_returnsEmpty_whenBothTheStoredAndSuppliedPasswordAreNull()
    {
        Customer noPassword = new Customer(9, "ghost", "No Password", List.of(), Role.CUSTOMER, null);
        when(repository.findByUsername("ghost")).thenReturn(Optional.of(noPassword));

        assertThat(service.signIn("ghost", null)).isEmpty();
    }

    //----------------------------------------------------------------CREATE----------------------------------------------------------------

    /*
     * The guarantee that matters: registration cannot produce an admin. Not because a check
     * rejects one, but because addNewCustomer takes no role - there is nowhere for a
     * caller to put it. This asserts the role the SERVER chose.
     */
    @Test
    void addNewCustomer_alwaysAssignsTheCustomerRole()
    {
        when(repository.findByUsername("nina")).thenReturn(Optional.empty());
        when(repository.nextCustomerId()).thenReturn(5);
        when(repository.addCustomer(any(Customer.class))).thenAnswer(call -> Optional.of(call.getArgument(0)));

        service.addNewCustomer("nina", "pw123", "Nina Cortez");

        ArgumentCaptor<Customer> created = ArgumentCaptor.forClass(Customer.class);
        verify(repository).addCustomer(created.capture());
        assertThat(created.getValue().getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void addNewCustomer_takesTheIdFromTheServer_notTheCaller()
    {
        when(repository.findByUsername("nina")).thenReturn(Optional.empty());
        when(repository.nextCustomerId()).thenReturn(5);
        when(repository.addCustomer(any(Customer.class))).thenAnswer(call -> Optional.of(call.getArgument(0)));

        service.addNewCustomer("nina", "pw123", "Nina Cortez");

        ArgumentCaptor<Customer> created = ArgumentCaptor.forClass(Customer.class);
        verify(repository).addCustomer(created.capture());
        assertThat(created.getValue().getId()).isEqualTo(5);
        assertThat(created.getValue().getPassword()).isEqualTo("pw123");
        assertThat(created.getValue().getAccountIds()).isEmpty();
    }

    // a real conflict on this route: the caller cannot pick an id, so the username is the
    // only thing two registrations can collide on.
    @Test
    void addNewCustomer_returnsEmptyAndCreatesNothing_whenTheUsernameIsTaken()
    {
        when(repository.findByUsername("alice")).thenReturn(Optional.of(
            new Customer(1, "alice", "Alice Smith", List.of(), Role.CUSTOMER, "alice123")));

        assertThat(service.addNewCustomer("alice", "pw", "Alice Two")).isEmpty();

        verify(repository, never()).addCustomer(any(Customer.class));
    }

    //----------------------------------------------------------------USERNAME UNIQUENESS----------------------------------------------------------------

    /*
     * Only _id carries a unique index, so nothing in the database stops a second "alice".
     * That matters beyond tidiness: findByUsername returns an Optional, so two customers
     * sharing a username make it throw, and sign-in would answer 500 for both of them.
     */
    @Test
    void addNewCustomer_returnsEmptyAndInsertsNothing_whenTheUsernameIsTaken()
    {
        when(repository.findByUsername("alice")).thenReturn(Optional.of(
            new Customer(1, "alice", "Alice Smith", List.of(), Role.CUSTOMER, "alice123")));

        assertThat(service.addNewCustomer("alice", "pw", "Alice Impostor")).isEmpty();

        verify(repository, never()).addCustomer(any(Customer.class));
    }

    //----------------------------------------------------------------THE LAST ADMIN----------------------------------------------------------------

    /*
     * The invariant is "at least one admin must remain", not "an admin must not be deleted".
     * These four pin the difference: the LAST admin is protected, a second one is not, and a
     * non-admin is never affected no matter how many admins exist.
     *
     * This is the only rule in the application that binds every caller. It needs no
     * authentication because it asks about the state of the system rather than who is
     * asking - the same reason Account can enforce its overdraft floor without a principal.
     */
    @Test
    void deleteCustomerById_refusesToRemoveTheLastAdmin()
    {
        when(repository.getCustomersByRole(Role.ADMIN)).thenReturn(List.of(
            new Customer(4, "admin", "Admin User", List.of(), Role.ADMIN, "admin123")));

        assertThat(service.deleteCustomerById(4)).isFalse();

        verify(repository, never()).deleteById(4);
    }

    @Test
    void deleteCustomerById_allowsRemovingAnAdmin_whenAnotherAdminRemains()
    {
        when(repository.getCustomersByRole(Role.ADMIN)).thenReturn(List.of(
            new Customer(4, "admin", "Admin User", List.of(), Role.ADMIN, "admin123"),
            new Customer(5, "admin2", "Second Admin", List.of(), Role.ADMIN, "admin456")));
        when(repository.deleteById(4)).thenReturn(true);

        assertThat(service.deleteCustomerById(4)).isTrue();
    }

    @Test
    void deleteCustomerById_allowsRemovingANonAdmin_whileOnlyOneAdminExists()
    {
        when(repository.getCustomersByRole(Role.ADMIN)).thenReturn(List.of(
            new Customer(4, "admin", "Admin User", List.of(), Role.ADMIN, "admin123")));
        when(repository.deleteById(1)).thenReturn(true);

        assertThat(service.deleteCustomerById(1)).isTrue();
    }

    /*
     * The demotion half of the invariant. UNREACHABLE THROUGH THE API as it stands, because
     * editCustomer no longer writes role at all - no request can demote anyone. These test
     * the service method directly, which still carries the guard.
     *
     * Kept deliberately: the invariant is correct independently of which routes exist today,
     * and if any future route ever sets a role, this is already there to refuse the demotion
     * rather than something someone has to remember to add back. The DELETE half above is
     * fully live and reachable.
     */


    // only the DEMOTION is refused. the last admin can still change their name or password.

    /*
     * What is left of editCustomer at this layer: it passes two strings down and hands back
     * what the repository gave it.
     *
     * FOUR TESTS WERE DELETED HERE, all of them describing requests that can no longer be
     * built. Two checked an id mismatch between body and path - UpdateCustomerRequest has no
     * id, so there is nothing to mismatch. Two more checked the last-admin demotion guard,
     * which is gone because the method has no role parameter to demote anyone with.
     *
     * That guard ending up unbuildable rather than merely unreachable is the better outcome:
     * an operation that cannot be expressed needs no rule forbidding it. The DELETE half of
     * the same invariant is still live and still tested, in the section above.
     */
    @Test
    void editCustomer_passesTheTwoOwnedFieldsToTheRepository()
    {
        Customer updated = new Customer(2, "bob2", "Bob J. Jones", List.of(), Role.CUSTOMER, "bob123");
        when(repository.editCustomer(2, "bob2", "Bob J. Jones")).thenReturn(Optional.of(updated));

        assertThat(service.editCustomer(2, "bob2", "Bob J. Jones")).contains(updated);
    }

    @Test
    void editCustomer_reportsAbsenceStraightThrough()
    {
        when(repository.editCustomer(99, "ghost", "Ghost User")).thenReturn(Optional.empty());

        assertThat(service.editCustomer(99, "ghost", "Ghost User")).isEmpty();
    }

    //---------------------------------------------USERNAME UNIQUENESS ON RENAME---------------------------------------------

    /*
     * THE TESTS THAT WOULD HAVE CAUGHT IT, and none of them existed.
     *
     * The uniqueness rule has been enforced on CREATE since it was written, and tested twice
     * in the section above. editCustomer was a pure pass-through - and a pass-through cannot
     * fail a rule nobody asked it to apply. Every edit test here checked that two strings
     * reached the repository, which they always did. The rule was simply never asked for on
     * the second route that could break it.
     *
     * The live sequence: PUT a second customer onto "carol", and GET /customers returns two
     * of them. findByUsername is a single Optional, so it THROWS on two matches and sign-in
     * answers 500 - for the ORIGINAL carol as much as the impostor. Any anonymous caller
     * could lock a real user out permanently, without touching that user's record at all.
     *
     * No identity is needed to refuse it, which is why this is fixed now rather than deferred
     * to phase 10: "two customers must not share a username" is a fact about the state of the
     * system, the same category as the last-admin invariant above.
     */
    @Test
    void editCustomer_refusesAUsernameHeldByAnotherCustomer()
    {
        when(repository.findByUsername("carol")).thenReturn(Optional.of(
            new Customer(3, "carol", "Carol Johnson", List.of(103), Role.CUSTOMER, "carol123")));

        assertThat(service.editCustomer(6, "carol", "Heidi L III")).isEmpty();

        verify(repository, never()).editCustomer(anyInt(), any(), any());
    }

    /*
     * THE OTHER HALF, and the reason the check asks "held by ANOTHER" rather than "held".
     *
     * A customer keeping its own username while changing its full name must still succeed. A
     * plain findByUsername(...).isPresent() would refuse that - every PUT which did not also
     * rename would 409, and most of them do not. The id is what tells "this record" apart
     * from "a different record", and CREATE never needs that distinction because a record
     * that does not exist yet cannot be the one already holding the name.
     */
    @Test
    void editCustomer_allowsACustomerToKeepItsOwnUsername()
    {
        Customer renamed = new Customer(3, "carol", "Carol J. Johnson", List.of(103), Role.CUSTOMER, "carol123");
        when(repository.findByUsername("carol")).thenReturn(Optional.of(
            new Customer(3, "carol", "Carol Johnson", List.of(103), Role.CUSTOMER, "carol123")));
        when(repository.editCustomer(3, "carol", "Carol J. Johnson")).thenReturn(Optional.of(renamed));

        assertThat(service.editCustomer(3, "carol", "Carol J. Johnson")).contains(renamed);
    }

    @Test
    void editCustomer_allowsARenameToAFreeUsername()
    {
        Customer renamed = new Customer(2, "bobby", "Bob Jones", List.of(102), Role.CUSTOMER, "bob123");
        when(repository.findByUsername("bobby")).thenReturn(Optional.empty());
        when(repository.editCustomer(2, "bobby", "Bob Jones")).thenReturn(Optional.of(renamed));

        assertThat(service.editCustomer(2, "bobby", "Bob Jones")).contains(renamed);
    }
}

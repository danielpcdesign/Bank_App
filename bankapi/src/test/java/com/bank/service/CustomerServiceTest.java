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
    @Test
    void editCustomer_rejectsAnIdMismatch_withoutTouchingTheRepository()
    {
        Customer body = new Customer(5, "bob", "Bob Jones");

        assertThat(service.editCustomer(2, body)).isEmpty();

        verifyNoInteractions(repository);
    }

    @Test
    void editCustomer_rejectsANullBodyId_withoutThrowing()
    {
        Customer body = new Customer(null, "bob", "Bob Jones");

        assertThat(service.editCustomer(2, body)).isEmpty();

        verifyNoInteractions(repository);
    }
    // a regression test for the bug fixed at the end of phase 2. `data.getId() != id`
    // unboxed the Integer, so a null body id was an NPE rather than a rejection.
    // @Valid stops that at the controller - and a unit test has no controller in front of it.

    @Test
    void editCustomer_delegatesToTheRepository_whenTheIdsAgree()
    {
        Customer body = new Customer(2, "bob", "Bob Jones");
        Customer updated = new Customer(2, "bob", "Bob J. Jones");

        when(repository.editCustomer(2, body)).thenReturn(Optional.of(updated));

        assertThat(service.editCustomer(2, body)).contains(updated);
    }
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

    @Test
    void deleteCustomerById_refusesToRemoveTheLastAdmin()
    {
        when(repository.getCustomersByRole(Role.ADMIN)).thenReturn(List.of(
            new Customer(4, "admin", "Admin User", List.of(), Role.ADMIN, "admin123")));

        assertThat(service.deleteCustomerById(4)).isFalse();

        verify(repository, never()).deleteById(4);
    }
}

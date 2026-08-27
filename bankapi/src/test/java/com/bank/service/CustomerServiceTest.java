package com.bank.service;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bank.model.Customer;
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

    @Test
    void addNewCustomer_returnsTrue_whenTheRepositoryInsertedTheRecord()
    {
        Customer customer = new Customer(4, "nina", "Nina Cortez");
        when(repository.addCustomer(customer)).thenReturn(Optional.of(customer));

        assertThat(service.addNewCustomer(customer)).isTrue();
    }

    @Test
    void addNewCustomer_returnsFalse_whenTheIdWasAlreadyTaken()
    {
        Customer customer = new Customer(1, "dupe", "Duplicate Id");
        when(repository.addCustomer(customer)).thenReturn(Optional.empty());

        assertThat(service.addNewCustomer(customer)).isFalse();
    }

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
}

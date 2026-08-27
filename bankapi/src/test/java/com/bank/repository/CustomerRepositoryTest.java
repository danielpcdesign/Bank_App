package com.bank.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.bank.model.Customer;

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

    @Test
    void seedsThreeCustomers_whenTheCollectionIsEmpty()
    {
        when(mongo.count()).thenReturn(0L);

        new CustomerRepository(mongo);

        verify(mongo, times(3)).save(any(Customer.class));
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
        when(mongo.existsById(99)).thenReturn(false);

        assertThat(repository.editCustomer(99, new Customer(99, "ghost", "Ghost User"))).isEmpty();

        verify(mongo, never()).save(any(Customer.class));
    }

    // the invariant argued over at length in phase 2: the stored document is keyed by the
    // PATH id, never by whatever the client put in the body. an ArgumentCaptor is the only
    // way to assert on the object handed to a collaborator rather than on a return value.
    @Test
    void editCustomer_keysTheReplacementByThePathId_ignoringTheBodyId()
    {
        CustomerRepository repository = repositoryWithPopulatedCollection();
        when(mongo.existsById(2)).thenReturn(true);
        when(mongo.save(any(Customer.class))).thenAnswer(call -> call.getArgument(0));

        // body claims id 99 on purpose. the repository must not believe it.
        repository.editCustomer(2, new Customer(99, "bob", "Bob Jones"));

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(mongo).save(saved.capture());

        assertThat(saved.getValue().getId()).isEqualTo(2);
        assertThat(saved.getValue().getUsername()).isEqualTo("bob");
        assertThat(saved.getValue().getFullName()).isEqualTo("Bob Jones");
    }

    @Test
    void findById_passesAbsenceStraightThrough()
    {
        CustomerRepository repository = repositoryWithPopulatedCollection();
        when(mongo.findById(99)).thenReturn(Optional.empty());

        assertThat(repository.findById(99)).isEmpty();
    }
}

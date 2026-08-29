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

import com.bank.model.Account;
import com.bank.model.AccountType;

// AccountMongoRepository is the mock, so nothing here touches atlas. what is under test is
// the wrapper's own branching: the seed guard, the duplicate catch, and the existence
// guards that turn "absent" into an empty Optional.
@ExtendWith(MockitoExtension.class)
class AccountRepositoryTest
{
    @Mock
    private AccountMongoRepository mongo;

    // deliberately NOT @InjectMocks, for the same reason as the customer version: the
    // constructor seeds, so construction is itself a behaviour under test and would
    // otherwise record three save() calls before the test body ran.
    private AccountRepository repositoryWithPopulatedCollection()
    {
        when(mongo.count()).thenReturn(3L);
        return new AccountRepository(mongo);
    }

    @Test
    void seedsThreeAccounts_whenTheCollectionIsEmpty()
    {
        when(mongo.count()).thenReturn(0L);

        new AccountRepository(mongo);

        verify(mongo, times(3)).save(any(Account.class));
    }

    @Test
    void doesNotSeed_whenTheCollectionAlreadyHasDocuments()
    {
        when(mongo.count()).thenReturn(3L);

        new AccountRepository(mongo);

        verify(mongo, never()).save(any(Account.class));
    }

    @Test
    void addAccount_returnsTheInsertedAccount_onSuccess()
    {
        AccountRepository repository = repositoryWithPopulatedCollection();
        Account account = new Account(104, AccountType.SAVINGS, 0.0, 0.0);
        when(mongo.insert(account)).thenReturn(account);

        assertThat(repository.addAccount(account)).contains(account);
    }

    // the catch block that makes 409 possible. the unique index on _id rejects the write and
    // the repository converts that into an empty Optional for the service to read.
    @Test
    void addAccount_returnsEmpty_whenTheInsertHitsADuplicateKey()
    {
        AccountRepository repository = repositoryWithPopulatedCollection();
        Account account = new Account(101, AccountType.SAVINGS, 0.0, 0.0);
        when(mongo.insert(account)).thenThrow(new DuplicateKeyException("_id 101 already exists"));

        assertThat(repository.addAccount(account)).isEmpty();
    }

    @Test
    void deleteById_reportsFalseAndDeletesNothing_whenTheIdIsAbsent()
    {
        AccountRepository repository = repositoryWithPopulatedCollection();
        when(mongo.existsById(999)).thenReturn(false);

        assertThat(repository.deleteById(999)).isFalse();

        verify(mongo, never()).deleteById(any());
    }

    @Test
    void deleteById_reportsTrue_whenTheIdExists()
    {
        AccountRepository repository = repositoryWithPopulatedCollection();
        when(mongo.existsById(102)).thenReturn(true);

        assertThat(repository.deleteById(102)).isTrue();

        verify(mongo).deleteById(102);
    }

    @Test
    void editAccount_returnsEmptyAndSavesNothing_whenTheIdIsAbsent()
    {
        AccountRepository repository = repositoryWithPopulatedCollection();
        when(mongo.existsById(999)).thenReturn(false);

        assertThat(repository.editAccount(999, new Account(999, AccountType.SAVINGS, 0.0, 0.0))).isEmpty();

        verify(mongo, never()).save(any(Account.class));
    }

    // same invariant the customer repository holds: the stored document is keyed by the PATH
    // id, never by whatever the client put in the body.
    @Test
    void editAccount_keysTheReplacementByThePathId_ignoringTheBodyId()
    {
        AccountRepository repository = repositoryWithPopulatedCollection();
        when(mongo.existsById(102)).thenReturn(true);
        when(mongo.save(any(Account.class))).thenAnswer(call -> call.getArgument(0));

        // body claims id 999 on purpose. the repository must not believe it.
        repository.editAccount(102, new Account(999, AccountType.CHECKING, 250.0, -100.0));

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(mongo).save(saved.capture());

        assertThat(saved.getValue().getId()).isEqualTo(102);
        assertThat(saved.getValue().getType()).isEqualTo(AccountType.CHECKING);
        assertThat(saved.getValue().getBalance()).isEqualTo(250.0);
        assertThat(saved.getValue().getOverdraftLimit()).isEqualTo(-100.0);
    }

    @Test
    void findById_passesAbsenceStraightThrough()
    {
        AccountRepository repository = repositoryWithPopulatedCollection();
        when(mongo.findById(999)).thenReturn(Optional.empty());

        assertThat(repository.findById(999)).isEmpty();
    }
}

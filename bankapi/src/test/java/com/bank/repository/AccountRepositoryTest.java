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
        when(mongo.findById(999)).thenReturn(Optional.empty());

        assertThat(repository.editAccount(999, AccountType.SAVINGS, 0.0)).isEmpty();

        verify(mongo, never()).save(any(Account.class));
    }

    /*
     * THE TEST THAT WOULD HAVE CAUGHT THE SECOND HALF OF THE MONEY-CREATION BUG, and it did
     * not exist because every replace test sent a complete body and asserted that the
     * complete body was what got stored - including its balance. That reads as correct right
     * up until you ask where the balance came from.
     *
     * The replacement is keyed by the PATH id and carries the STORED balance. Neither can be
     * stated by a caller any more, so PUT can rename an account's type or move its floor and
     * cannot touch the money. Withdraw and deposit are the only things that can.
     */
    @Test
    void editAccount_carriesTheStoredBalanceAcross_soAReplaceCannotMoveMoney()
    {
        AccountRepository repository = repositoryWithPopulatedCollection();
        when(mongo.findById(102)).thenReturn(Optional.of(new Account(102, AccountType.CHECKING, 250.0, -100.0)));
        when(mongo.save(any(Account.class))).thenAnswer(call -> call.getArgument(0));

        repository.editAccount(102, AccountType.CHECKING, -500.0);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(mongo).save(saved.capture());

        assertThat(saved.getValue().getId()).isEqualTo(102);
        assertThat(saved.getValue().getType()).isEqualTo(AccountType.CHECKING);
        assertThat(saved.getValue().getBalance()).isEqualTo(250.0);   // unchanged by the edit
        assertThat(saved.getValue().getOverdraftLimit()).isEqualTo(-500.0);
    }

    // the constructor's savings coercion still runs on the way through, so a replace cannot
    // give a savings account an overdraft even when the request asks for one.
    @Test
    void editAccount_forcesASavingsFloorToZero()
    {
        AccountRepository repository = repositoryWithPopulatedCollection();
        when(mongo.findById(101)).thenReturn(Optional.of(new Account(101, AccountType.SAVINGS, 500.0, 0.0)));
        when(mongo.save(any(Account.class))).thenAnswer(call -> call.getArgument(0));

        repository.editAccount(101, AccountType.SAVINGS, -100.0);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(mongo).save(saved.capture());

        assertThat(saved.getValue().getOverdraftLimit()).isZero();
        assertThat(saved.getValue().getBalance()).isEqualTo(500.0);
    }

    @Test
    void findById_passesAbsenceStraightThrough()
    {
        AccountRepository repository = repositoryWithPopulatedCollection();
        when(mongo.findById(999)).thenReturn(Optional.empty());

        assertThat(repository.findById(999)).isEmpty();
    }
}

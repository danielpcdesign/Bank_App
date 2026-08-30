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

import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.Customer;
import com.bank.model.Role;
import com.bank.repository.AccountRepository;
import com.bank.repository.CustomerRepository;

// no spring, no atlas. both repositories are fakes, so these test the service's own logic:
// the rules that need more than one object. what is always true of a single account is
// tested in AccountTest, not repeated here.
@ExtendWith(MockitoExtension.class)
class AccountServiceTest
{
    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private AccountService service;

    private static Account savings(int id, double balance)
    {
        return new Account(id, AccountType.SAVINGS, balance, 0.0);
    }

    /*
     * FOUR TESTS WERE DELETED HERE, all of them describing calls that can no longer be made.
     *
     * Two covered addNewAccount(Account), which is gone. It took a fully-built Account from
     * its caller - balance included - and handed it straight to storage, which is exactly the
     * primitive that let a client mint money. A public service method whose parameter is a
     * client-shaped account carrying a balance is the bug waiting to be rewired, so it was
     * removed rather than left sitting unused. The same call was made about Customer.setRole.
     *
     * Two more covered the id mismatch between body and path. editAccount takes a type and a
     * floor now rather than an Account, so there is no body id left to disagree with the
     * path - the case is unrepresentable rather than untested.
     */
    @Test
    void editAccount_passesTheTwoOwnedFieldsToTheRepository()
    {
        Account updated = new Account(102, AccountType.CHECKING, 250.0, -100.0);
        when(accountRepository.editAccount(102, AccountType.CHECKING, -100.0)).thenReturn(Optional.of(updated));

        assertThat(service.editAccount(102, AccountType.CHECKING, -100.0)).contains(updated);
    }

    @Test
    void editAccount_reportsAbsenceStraightThrough()
    {
        when(accountRepository.editAccount(999, AccountType.SAVINGS, 0.0)).thenReturn(Optional.empty());

        assertThat(service.editAccount(999, AccountType.SAVINGS, 0.0)).isEmpty();
    }

    //----------------------------------------------------------------CUSTOMER SCOPED----------------------------------------------------------------

    // absence of the customer and absence of accounts are different answers: one is a 404,
    // the other a 200 with an empty list. the service keeps them apart so the controller can.
    @Test
    void getAccountsForCustomer_returnsEmptyOptional_whenTheCustomerDoesNotExist()
    {
        when(customerRepository.findById(99)).thenReturn(Optional.empty());

        assertThat(service.getAccountsForCustomer(99)).isEmpty();
    }

    @Test
    void getAccountsForCustomer_returnsAnEmptyList_whenTheCustomerOwnsNothing()
    {
        when(customerRepository.findById(1)).thenReturn(Optional.of(new Customer(1, "alice", "Alice Smith", List.of())));

        assertThat(service.getAccountsForCustomer(1)).contains(List.of());
    }

    @Test
    void getAccountsForCustomer_resolvesEachIdToItsAccount()
    {
        Account one = savings(101, 500.0);
        when(customerRepository.findById(1)).thenReturn(Optional.of(new Customer(1, "alice", "Alice Smith", List.of(101))));
        when(accountRepository.findById(101)).thenReturn(Optional.of(one));

        assertThat(service.getAccountsForCustomer(1)).contains(List.of(one));
    }

    // a dangling id is skipped rather than turned into a null in the list. the whole reason
    // delete unlinks is to stop these existing, but a list that survives one is worth having.
    @Test
    void getAccountsForCustomer_skipsAnIdWithNoAccountBehindIt()
    {
        when(customerRepository.findById(1)).thenReturn(Optional.of(new Customer(1, "alice", "Alice Smith", List.of(101))));
        when(accountRepository.findById(101)).thenReturn(Optional.empty());

        assertThat(service.getAccountsForCustomer(1)).contains(List.of());
    }

    @Test
    void openAccountForCustomer_returnsEmptyAndCreatesNothing_whenTheCustomerDoesNotExist()
    {
        when(customerRepository.findById(99)).thenReturn(Optional.empty());

        assertThat(service.openAccountForCustomer(99, 104, AccountType.SAVINGS, 0.0)).isEmpty();

        verifyNoInteractions(accountRepository);
    }

    // the account id collided, so nothing was created - and the customer must not be left
    // holding an id for an account that was never inserted.
    // any(Account.class) is required, not lazy, and it is required for a NEW reason: the
    // service builds the Account itself now, so no instance the test holds could ever be
    // the one the repository is handed - Account does not override equals().
    @Test
    void openAccountForCustomer_returnsEmptyAndDoesNotLink_whenTheAccountIdIsTaken()
    {
        when(customerRepository.findById(1)).thenReturn(Optional.of(new Customer(1, "alice", "Alice Smith", List.of())));
        when(accountRepository.addAccount(any(Account.class))).thenReturn(Optional.empty());

        assertThat(service.openAccountForCustomer(1, 101, AccountType.SAVINGS, 0.0)).isEmpty();

        verify(customerRepository, never()).save(any(Customer.class));
    }

    // the link is the point of this method - creating the account alone would leave it
    // owned by nobody, which is what the unscoped POST already does.
    @Test
    void openAccountForCustomer_addsTheNewIdToTheCustomersList()
    {
        when(customerRepository.findById(1)).thenReturn(Optional.of(new Customer(1, "alice", "Alice Smith", List.of(101))));
        when(accountRepository.addAccount(any(Account.class))).thenAnswer(call -> Optional.of(call.getArgument(0)));

        assertThat(service.openAccountForCustomer(1, 104, AccountType.SAVINGS, 0.0)).isPresent();

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertThat(saved.getValue().getAccountIds()).containsExactly(101, 104);
    }

    /*
     * THE MONEY-CREATION FIX, tested at the layer that now owns it.
     *
     * The service constructs the account, and it has no parameter through which a balance
     * could arrive - so the zero asserted here is the only opening balance any account can
     * have. That is what makes deposit the single entry point for money: not a rule this
     * method follows, but the absence of any alternative.
     *
     * The floor IS the caller's and is carried through unchanged, which is the distinction
     * worth pinning: "how far below zero may this go" is a genuine choice made when an
     * account is opened, and a balance never was.
     */
    @Test
    void openAccountForCustomer_alwaysOpensTheAccountAtAZeroBalance()
    {
        when(customerRepository.findById(1)).thenReturn(Optional.of(new Customer(1, "alice", "Alice Smith", List.of())));
        when(accountRepository.addAccount(any(Account.class))).thenAnswer(call -> Optional.of(call.getArgument(0)));

        service.openAccountForCustomer(1, 104, AccountType.CHECKING, -100.0);

        ArgumentCaptor<Account> created = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).addAccount(created.capture());
        assertThat(created.getValue().getBalance()).isZero();
        assertThat(created.getValue().getOverdraftLimit()).isEqualTo(-100.0);
    }

    @Test
    void closeAccountForCustomer_returnsFalse_whenTheCustomerDoesNotExist()
    {
        when(customerRepository.findById(99)).thenReturn(Optional.empty());

        assertThat(service.closeAccountForCustomer(99, 101)).isFalse();

        verifyNoInteractions(accountRepository);
    }

    // ownership is the rule this method exists to enforce. without it any customer id in
    // the path could close any account in the bank.
    @Test
    void closeAccountForCustomer_returnsFalseAndDeletesNothing_whenTheCustomerDoesNotOwnIt()
    {
        when(customerRepository.findById(1)).thenReturn(Optional.of(new Customer(1, "alice", "Alice Smith", List.of(101))));

        assertThat(service.closeAccountForCustomer(1, 102)).isFalse();

        verifyNoInteractions(accountRepository);
    }

    @Test
    void closeAccountForCustomer_unlinksTheIdAndDeletesTheAccount()
    {
        when(customerRepository.findById(1)).thenReturn(Optional.of(new Customer(1, "alice", "Alice Smith", List.of(101, 104))));
        when(accountRepository.deleteById(101)).thenReturn(true);

        assertThat(service.closeAccountForCustomer(1, 101)).isTrue();

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertThat(saved.getValue().getAccountIds()).containsExactly(104);
        verify(accountRepository).deleteById(101);
    }

    // the unscoped delete is not told who the owner is, so it finds them. otherwise the
    // customer is left pointing at a document that no longer exists.
    @Test
    void deleteAccountById_unlinksTheIdFromWhicheverCustomerHeldIt()
    {
        when(accountRepository.findById(101)).thenReturn(Optional.of(savings(101, 500.0)));
        when(customerRepository.getCustomers()).thenReturn(List.of(new Customer(1, "alice", "Alice Smith", List.of(101))));
        when(accountRepository.deleteById(101)).thenReturn(true);

        assertThat(service.deleteAccountById(101)).isTrue();

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertThat(saved.getValue().getAccountIds()).isEmpty();
    }

    //----------------------------------------------------------------OPERATIONS----------------------------------------------------------------

    @Test
    void deposit_returnsEmpty_whenTheAccountDoesNotExist()
    {
        when(accountRepository.findById(999)).thenReturn(Optional.empty());

        assertThat(service.deposit(999, 100.0)).isEmpty();

        verify(accountRepository, never()).save(any(Account.class));
    }

    // the service does not re-implement the rule, it asks the model and persists only if the
    // model agreed. a refused amount must leave nothing written.
    @Test
    void deposit_returnsEmptyAndSavesNothing_whenTheModelRefusesTheAmount()
    {
        when(accountRepository.findById(101)).thenReturn(Optional.of(savings(101, 500.0)));

        assertThat(service.deposit(101, 10.50)).isEmpty();

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void deposit_savesAndReturnsTheAccountWithTheNewBalance()
    {
        when(accountRepository.findById(101)).thenReturn(Optional.of(savings(101, 500.0)));
        when(accountRepository.save(any(Account.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(service.deposit(101, 250.0)).get().extracting(Account::getBalance).isEqualTo(750.0);
    }

    @Test
    void withdraw_returnsEmptyAndSavesNothing_whenItWouldBreachTheFloor()
    {
        when(accountRepository.findById(101)).thenReturn(Optional.of(savings(101, 500.0)));

        assertThat(service.withdraw(101, 501.0)).isEmpty();

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void withdraw_savesAndReturnsTheAccountWithTheNewBalance()
    {
        when(accountRepository.findById(101)).thenReturn(Optional.of(savings(101, 500.0)));
        when(accountRepository.save(any(Account.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(service.withdraw(101, 200.0)).get().extracting(Account::getBalance).isEqualTo(300.0);
    }

    //----------------------------------------------------------------CREDENTIAL SURVIVAL----------------------------------------------------------------

    /*
     * These four cover a bug that 137 passing tests did not catch, because every operation
     * was correct on its own and the damage only appeared in SEQUENCE: create a customer with
     * a password, open an account for them, and the password was gone from the document.
     *
     * The cause was updating the account list by constructing a replacement Customer with a
     * constructor that does not take a password or a role. Each assertion below is on a field
     * the operation under test has no business touching at all.
     *
     * The ADMIN cases matter as much as the password ones: the same constructor defaulted
     * role to CUSTOMER, so an admin who owned an account was silently demoted.
     */
    @Test
    void openAccountForCustomer_leavesThePasswordIntact()
    {
        when(customerRepository.findById(1)).thenReturn(Optional.of(
            new Customer(1, "alice", "Alice Smith", List.of(101), Role.CUSTOMER, "alice123")));
        when(accountRepository.addAccount(any(Account.class))).thenAnswer(call -> Optional.of(call.getArgument(0)));

        service.openAccountForCustomer(1, 104, AccountType.SAVINGS, 0.0);

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("alice123");
        assertThat(saved.getValue().getAccountIds()).containsExactly(101, 104);
    }

    @Test
    void openAccountForCustomer_doesNotDemoteAnAdmin()
    {
        when(customerRepository.findById(4)).thenReturn(Optional.of(
            new Customer(4, "admin", "Admin User", List.of(), Role.ADMIN, "admin123")));
        when(accountRepository.addAccount(any(Account.class))).thenAnswer(call -> Optional.of(call.getArgument(0)));

        service.openAccountForCustomer(4, 104, AccountType.SAVINGS, 0.0);

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getPassword()).isEqualTo("admin123");
    }

    @Test
    void closeAccountForCustomer_leavesThePasswordAndRoleIntact()
    {
        when(customerRepository.findById(4)).thenReturn(Optional.of(
            new Customer(4, "admin", "Admin User", List.of(101, 104), Role.ADMIN, "admin123")));
        when(accountRepository.deleteById(101)).thenReturn(true);

        service.closeAccountForCustomer(4, 101);

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("admin123");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getAccountIds()).containsExactly(104);
    }

    // the unscoped delete reaches the same unlink by a different route, so it needs its own
    // assertion rather than trusting the one above.
    @Test
    void deleteAccountById_leavesTheOwnersPasswordAndRoleIntact()
    {
        when(accountRepository.findById(101)).thenReturn(Optional.of(savings(101, 500.0)));
        when(customerRepository.getCustomers()).thenReturn(List.of(
            new Customer(4, "admin", "Admin User", List.of(101), Role.ADMIN, "admin123")));
        when(accountRepository.deleteById(101)).thenReturn(true);

        service.deleteAccountById(101);

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("admin123");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getAccountIds()).isEmpty();
    }

    /*
     * THE BLAST RADIUS, asserted field by field rather than sampled.
     *
     * The user's requirement, in their words: creating an account should not dump everything
     * in the owning customer - at most it should affect the array of owned accounts. This
     * pins that down for every field Customer has, so "nothing else changed" is verified
     * rather than inferred from the two fields that happened to break last time.
     *
     * If a seventh field is added to Customer, this test does NOT automatically cover it -
     * add a line here. That is the honest limit of the assertion.
     */
    @Test
    void openAccountForCustomer_changesAccountIdsAndNothingElse()
    {
        when(customerRepository.findById(4)).thenReturn(Optional.of(
            new Customer(4, "admin", "Admin User", List.of(101), Role.ADMIN, "admin123")));
        when(accountRepository.addAccount(any(Account.class))).thenAnswer(call -> Optional.of(call.getArgument(0)));

        service.openAccountForCustomer(4, 104, AccountType.SAVINGS, 0.0);

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        Customer written = saved.getValue();

        // the one field this operation owns
        assertThat(written.getAccountIds()).containsExactly(101, 104);

        // every other field on Customer, unchanged
        assertThat(written.getId()).isEqualTo(4);
        assertThat(written.getUsername()).isEqualTo("admin");
        assertThat(written.getFullName()).isEqualTo("Admin User");
        assertThat(written.getRole()).isEqualTo(Role.ADMIN);
        assertThat(written.getPassword()).isEqualTo("admin123");
    }
}

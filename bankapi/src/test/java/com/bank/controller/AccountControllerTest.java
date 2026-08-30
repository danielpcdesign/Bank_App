package com.bank.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.Customer;
import com.bank.service.AccountService;
import com.bank.service.CustomerService;

import java.util.List;
import java.util.Optional;

// boots the web layer only - the controller, jackson, validation, the routing table.
// both services are mocks, so these prove the HTTP translation and nothing below it.
@WebMvcTest(AccountController.class)
class AccountControllerTest
{
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private CustomerService customerService;

    private static Account savings(int id, double balance)
    {
        return new Account(id, AccountType.SAVINGS, balance, 0.0);
    }

    // NOTE WHAT IS NOT IN EITHER OF THESE: a balance. CreateAccountRequest and
    // UpdateAccountRequest have no field for one, and that is the fix these tests guard.
    // The replace body carries no id either - the path is the only identity.
    private static final String CREATE_BODY =
        "{\"id\":104,\"type\":\"SAVINGS\",\"overdraftLimit\":0.0}";

    private static final String REPLACE_BODY =
        "{\"type\":\"SAVINGS\",\"overdraftLimit\":0.0}";

    @Test
    void getAllAccounts_returnsTheListFromTheService() throws Exception
    {
        when(accountService.getAllAccounts()).thenReturn(List.of(savings(101, 500.0)));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].type").value("SAVINGS"))
                .andExpect(jsonPath("$[0].balance").value(500.0));
    }

    @Test
    void getAccountById_returns200AndTheAccount_whenTheServiceFindsIt() throws Exception
    {
        when(accountService.getAccountById(101)).thenReturn(Optional.of(savings(101, 500.0)));

        mockMvc.perform(get("/api/v1/accounts/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.overdraftLimit").value(0.0));
    }

    // "not found" is a fact the service reports as an empty Optional.
    // "404" is an http decision only the controller makes.
    @Test
    void getAccountById_returns404_whenTheServiceReturnsEmpty() throws Exception
    {
        when(accountService.getAccountById(999)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/accounts/999"))
                .andExpect(status().isNotFound());
    }

    // the stub takes four plain values now rather than any(Account.class). that is not a
    // style change: the service is no longer handed a client-built object, so there is
    // nothing whose identity jackson could break - and the arguments become assertable.
    // the unscoped create delegates to the customer-scoped one, so it goes through exactly
    // the same guards - which is what these stubs assert.
    @Test
    void addAccount_returns201WithALocationHeader_whenTheOwnerExistsAndTheIdIsFree() throws Exception
    {
        when(customerService.getCustomerById(1)).thenReturn(Optional.of(new Customer(1, "alice", "Alice Smith")));
        when(accountService.openAccountForCustomer(1, 104, AccountType.SAVINGS, 0.0))
            .thenReturn(Optional.of(savings(104, 0.0)));

        mockMvc.perform(post("/api/v1/accounts").param("customerId", "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/accounts/104"));
    }

    // an account owned by nobody is not representable any more. without ?customerId= the
    // request cannot be satisfied at all, and @RequestParam rejects it before the method runs.
    @Test
    void addAccount_returns400AndNeverCallsTheService_whenCustomerIdIsMissing() throws Exception
    {
        mockMvc.perform(post("/api/v1/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_BODY))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void addAccount_returns404_whenTheOwningCustomerDoesNotExist() throws Exception
    {
        when(customerService.getCustomerById(99)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/accounts").param("customerId", "99")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_BODY))
                .andExpect(status().isNotFound());

        verifyNoInteractions(accountService);
    }

    // nothing is stubbed on purpose: the request must never get far enough to need it.
    // customerId supplied so the 400 can only be the missing type, not the missing owner
    @Test
    void addAccount_returns400AndNeverCallsTheService_whenTypeIsMissing() throws Exception
    {
        mockMvc.perform(post("/api/v1/accounts").param("customerId", "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":104,\"overdraftLimit\":0.0}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void addAccount_returns409_whenTheAccountIdIsAlreadyTaken() throws Exception
    {
        when(customerService.getCustomerById(1)).thenReturn(Optional.of(new Customer(1, "alice", "Alice Smith")));
        when(accountService.openAccountForCustomer(1, 104, AccountType.SAVINGS, 0.0)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/accounts").param("customerId", "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_BODY))
                .andExpect(status().isConflict());
    }

    // the id-mismatch test that used to sit here is DELETED, and deliberately not replaced
    // with an equivalent. UpdateAccountRequest has no id component, so a body id cannot
    // disagree with the path - the case is unrepresentable rather than merely untested.
    // What replaces it is editAccount_ignoresAnIdOrBalanceSmuggledIntoTheBody below.

    @Test
    void editAccount_returns404_whenTheServiceReturnsEmpty() throws Exception
    {
        when(accountService.editAccount(104, AccountType.SAVINGS, 0.0)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/accounts/104")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(REPLACE_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void editAccount_returns200AndTheUpdatedAccount_whenTheServiceReturnsIt() throws Exception
    {
        when(accountService.editAccount(104, AccountType.SAVINGS, 0.0)).thenReturn(Optional.of(savings(104, 25.0)));

        mockMvc.perform(put("/api/v1/accounts/104")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(REPLACE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(25.0));
    }

    @Test
    void deleteAccount_returns204_whenTheServiceReportsTrue() throws Exception
    {
        when(accountService.deleteAccountById(101)).thenReturn(true);

        mockMvc.perform(delete("/api/v1/accounts/101"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteAccount_returns404_whenTheServiceReportsFalse() throws Exception
    {
        when(accountService.deleteAccountById(999)).thenReturn(false);

        mockMvc.perform(delete("/api/v1/accounts/999"))
                .andExpect(status().isNotFound());
    }

    //----------------------------------------------------------------CUSTOMER SCOPED----------------------------------------------------------------

    @Test
    void getAccountsForCustomer_returns200AndTheList() throws Exception
    {
        when(accountService.getAccountsForCustomer(1)).thenReturn(Optional.of(List.of(savings(101, 500.0))));

        mockMvc.perform(get("/api/v1/customers/1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101));
    }

    // a customer with no accounts is a 200 and an empty list. only an absent customer is 404,
    // and the empty Optional is how the service says which of the two this is.
    @Test
    void getAccountsForCustomer_returns200AndAnEmptyList_whenTheCustomerOwnsNothing() throws Exception
    {
        when(accountService.getAccountsForCustomer(1)).thenReturn(Optional.of(List.of()));

        mockMvc.perform(get("/api/v1/customers/1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAccountsForCustomer_returns404_whenTheCustomerDoesNotExist() throws Exception
    {
        when(accountService.getAccountsForCustomer(99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/customers/99/accounts"))
                .andExpect(status().isNotFound());
    }

    @Test
    void openAccountForCustomer_returns201WithALocationHeader() throws Exception
    {
        when(customerService.getCustomerById(1)).thenReturn(Optional.of(new Customer(1, "alice", "Alice Smith")));
        when(accountService.openAccountForCustomer(1, 104, AccountType.SAVINGS, 0.0))
            .thenReturn(Optional.of(savings(104, 0.0)));

        mockMvc.perform(post("/api/v1/customers/1/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/accounts/104"));
    }

    // the customer is checked first so a missing one is a 404. folded into the account
    // service's single false it would surface as a 409, which would be a lie.
    @Test
    void openAccountForCustomer_returns404AndNeverOpensAnAccount_whenTheCustomerDoesNotExist() throws Exception
    {
        when(customerService.getCustomerById(99)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/customers/99/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_BODY))
                .andExpect(status().isNotFound());

        verifyNoInteractions(accountService);
    }

    @Test
    void openAccountForCustomer_returns409_whenTheAccountIdIsTaken() throws Exception
    {
        when(customerService.getCustomerById(1)).thenReturn(Optional.of(new Customer(1, "alice", "Alice Smith")));
        when(accountService.openAccountForCustomer(1, 104, AccountType.SAVINGS, 0.0)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/customers/1/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_BODY))
                .andExpect(status().isConflict());
    }

    //-------------------------------------------------THE MONEY-CREATION DEFECT-------------------------------------------------

    /*
     * THE TESTS THAT WOULD HAVE CAUGHT IT, and none of them existed.
     *
     * Every account test in this class sent a complete, well-formed body with a whole
     * balance, so none could notice that the balance was a field a client got to CHOOSE.
     * The live request was:
     *
     *     POST /api/v1/accounts?customerId=3
     *     {"id":999,"type":"CHECKING","balance":100.55,"overdraftLimit":50}   ->  201
     *
     * That is the same shape as the lesson already recorded for the customer bugs: a suite
     * of complete payloads cannot see a field that should never have been in the payload at
     * all. The fix is structural, so these assert the STRUCTURE - that a balance sent by a
     * client reaches nothing, and that a positive floor is refused outright.
     */

    // the exact reported body. it must no longer be able to state an opening balance, and
    // the verify is the assertion that matters: the service is told 0.0 whatever was sent.
    @Test
    void openAccountForCustomer_opensAtZero_whenTheBodyTriesToStateABalance() throws Exception
    {
        when(customerService.getCustomerById(3)).thenReturn(Optional.of(new Customer(3, "carol", "Carol Johnson")));
        when(accountService.openAccountForCustomer(3, 999, AccountType.CHECKING, 0.0))
            .thenReturn(Optional.of(new Account(999, AccountType.CHECKING, 0.0, 0.0)));

        mockMvc.perform(post("/api/v1/customers/3/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":999,\"type\":\"CHECKING\",\"balance\":100.55}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(0.0));

        // the record has no balance component, so the smuggled figure binds to nothing
        verify(accountService).openAccountForCustomer(3, 999, AccountType.CHECKING, 0.0);
    }

    // the other half of the same reported request. a limit is a FLOOR, so a positive one
    // sits above zero and would refuse ordinary withdrawals.
    @Test
    void openAccountForCustomer_returns400AndNeverCallsTheService_whenTheOverdraftLimitIsPositive() throws Exception
    {
        mockMvc.perform(post("/api/v1/customers/3/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":999,\"type\":\"CHECKING\",\"overdraftLimit\":50}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    // an omitted floor is zero rather than null - the boxed Double is what lets validation
    // tell "absent" from "0", and floor() is what stops the service ever seeing a null.
    @Test
    void openAccountForCustomer_treatsAnOmittedOverdraftLimitAsZero() throws Exception
    {
        when(customerService.getCustomerById(1)).thenReturn(Optional.of(new Customer(1, "alice", "Alice Smith")));
        when(accountService.openAccountForCustomer(1, 104, AccountType.SAVINGS, 0.0))
            .thenReturn(Optional.of(savings(104, 0.0)));

        mockMvc.perform(post("/api/v1/customers/1/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":104,\"type\":\"SAVINGS\"}"))
                .andExpect(status().isCreated());

        verify(accountService).openAccountForCustomer(1, 104, AccountType.SAVINGS, 0.0);
    }

    // PUT was the SECOND money-creation route, and closing only the create one would have
    // fixed half the bug. neither a balance nor an id can bind to UpdateAccountRequest.
    @Test
    void editAccount_ignoresAnIdOrBalanceSmuggledIntoTheBody() throws Exception
    {
        when(accountService.editAccount(101, AccountType.SAVINGS, 0.0))
            .thenReturn(Optional.of(savings(101, 500.0)));

        mockMvc.perform(put("/api/v1/accounts/101")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":999,\"type\":\"SAVINGS\",\"balance\":1000000,\"overdraftLimit\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500.0));

        // keyed by the PATH id, and told nothing about a balance
        verify(accountService).editAccount(101, AccountType.SAVINGS, 0.0);
    }

    @Test
    void editAccount_returns400AndNeverCallsTheService_whenTheOverdraftLimitIsPositive() throws Exception
    {
        mockMvc.perform(put("/api/v1/accounts/101")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\":\"CHECKING\",\"overdraftLimit\":50}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void closeAccountForCustomer_returns204_whenTheServiceReportsTrue() throws Exception
    {
        when(accountService.closeAccountForCustomer(1, 101)).thenReturn(true);

        mockMvc.perform(delete("/api/v1/customers/1/accounts/101"))
                .andExpect(status().isNoContent());
    }

    @Test
    void closeAccountForCustomer_returns404_whenTheCustomerDoesNotOwnTheAccount() throws Exception
    {
        when(accountService.closeAccountForCustomer(1, 102)).thenReturn(false);

        mockMvc.perform(delete("/api/v1/customers/1/accounts/102"))
                .andExpect(status().isNotFound());
    }

    //----------------------------------------------------------------OPERATIONS----------------------------------------------------------------

    @Test
    void deposit_returns200AndTheUpdatedAccount() throws Exception
    {
        when(accountService.getAccountById(101)).thenReturn(Optional.of(savings(101, 500.0)));
        when(accountService.deposit(101, 250.0)).thenReturn(Optional.of(savings(101, 750.0)));

        mockMvc.perform(post("/api/v1/accounts/101/deposit").param("amount", "250"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(750.0));
    }

    // a fractional amount is a 400, not a 409: no balance would ever make it succeed, so it
    // is a malformed request rather than a conflict with the account's state.
    @Test
    void deposit_returns400AndNeverCallsTheService_whenTheAmountIsFractional() throws Exception
    {
        mockMvc.perform(post("/api/v1/accounts/101/deposit").param("amount", "10.50"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void deposit_returns400_whenTheAmountIsZeroOrNegative() throws Exception
    {
        mockMvc.perform(post("/api/v1/accounts/101/deposit").param("amount", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/accounts/101/deposit").param("amount", "-50"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void deposit_returns404_whenTheAccountDoesNotExist() throws Exception
    {
        when(accountService.getAccountById(999)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/accounts/999/deposit").param("amount", "100"))
                .andExpect(status().isNotFound());

        verify(accountService, never()).deposit(anyInt(), anyDouble());
    }

    @Test
    void withdraw_returns200AndTheUpdatedAccount() throws Exception
    {
        when(accountService.getAccountById(101)).thenReturn(Optional.of(savings(101, 500.0)));
        when(accountService.withdraw(101, 200.0)).thenReturn(Optional.of(savings(101, 300.0)));

        mockMvc.perform(post("/api/v1/accounts/101/withdraw").param("amount", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(300.0));
    }

    // insufficient funds IS state-dependent - the same request succeeds once the balance
    // allows it - so it is the one refusal that earns a 409 rather than a 400.
    @Test
    void withdraw_returns409_whenTheAccountsRulesRefuseIt() throws Exception
    {
        when(accountService.getAccountById(101)).thenReturn(Optional.of(savings(101, 500.0)));
        when(accountService.withdraw(101, 501.0)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/accounts/101/withdraw").param("amount", "501"))
                .andExpect(status().isConflict());
    }

    @Test
    void withdraw_returns400_whenTheAmountIsFractional() throws Exception
    {
        mockMvc.perform(post("/api/v1/accounts/101/withdraw").param("amount", "0.01"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }
}
